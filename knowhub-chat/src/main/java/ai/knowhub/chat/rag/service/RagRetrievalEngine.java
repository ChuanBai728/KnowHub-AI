package ai.knowhub.chat.rag.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.model.ChannelExecutionVo;
import ai.knowhub.chat.model.RetrievalResultVo;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.RagRetrievalContext;
import ai.knowhub.chat.rag.model.SubQuestionChannelTrace;
import ai.knowhub.chat.rag.model.SubQuestionEvidence;
import ai.knowhub.chat.rag.retrieve.channel.RetrievalChannel;
import ai.knowhub.chat.rag.retrieve.channel.RetrievalChannelResult;
import ai.knowhub.chat.rag.support.SearchReferenceMapper;
import ai.knowhub.chat.service.ConversationTraceRecorder;
import ai.knowhub.document.service.DocumentKnowledgeService;
import ai.knowhub.document.support.DocumentKnowledgeMetadataKeys;
import ai.knowhub.enums.RetrievalChannelEnum;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 【RAG 检索引擎 — RAG 流水线的"核心检索器"】
 *
 * 这个类是 RAG 流水线中最核心的检索组件，负责：
 * 1. 将用户问题拆分成多个子问题（如果有的话）
 * 2. 为每个子问题并行调用多个检索通道（向量检索、关键词检索等）
 * 3. 融合多通道的检索结果（使用 RRF 算法）
 * 4. 提升到父块（获取更完整的上下文）
 * 5. 重排序（Rerank，精排）
 * 6. 裁剪到 TopK 个最终结果
 *
 * 检索通道是什么？
 * 每个检索通道是一种独立的检索策略：
 * - vector 通道：向量检索，通过语义相似度匹配（适合模糊查询）
 * - keyword 通道：关键词检索，通过精确关键词匹配（适合精确查询）
 * - web 通道：网络搜索（如 Tavily），用于实时信息
 *
 * RRF（Reciprocal Rank Fusion）是什么？
 * RRF 是一种融合多个排序列表的算法。它不关心各通道的绝对分数，
 * 只关心排名位置，能把分数体系不同的结果合在一起。
 * 公式：RRF_score = sum(channelWeight / (k + rank_i))。
 *
 * 设计模式：
 * - 策略模式：通过 RetrievalChannel 接口支持不同的检索策略
 * - 并行模式：子问题之间、通道之间都并行执行
 * - 降级模式：单个通道失败不影响其他通道
 *
 * 在 RAG 流水线中的位置：
 * 执行计划 -> 【本类：检索引擎】-> 证据列表 -> RagPromptAssemblyService -> Prompt
 */
@Slf4j
@Service
public class RagRetrievalEngine {

    private static final Pattern EXACT_TERM_PATTERN = Pattern.compile(
        "`[^`]+`"
            + "|--?[A-Za-z][A-Za-z0-9-]*"
            + "|\\b[A-Z]{2,}[A-Z0-9_-]*\\b"
            + "|\\b[A-Za-z]+[A-Z][A-Za-z0-9]*\\b"
            + "|\\b(?=[A-Za-z0-9_]*[A-Za-z])(?=[A-Za-z0-9_]*\\d)[A-Za-z0-9_]+\\b"
            + "|\\b\\d+(?:\\.\\d+)+\\b"
            + "|(?:[/\\\\][A-Za-z0-9_.-]+)+"
    );

    private static final List<String> EXACT_TERM_HINTS = List.of(
        "配置项", "字段", "参数", "命令", "错误码", "状态码", "类名", "方法名",
        "接口名", "版本号", "章节号", "文件名", "属性名", "键名"
    );

    /**
     * 检索通道列表（通过 Spring 自动注入所有实现 RetrievalChannel 接口的 Bean）。
     * 例如：VectorRetrievalChannel、KeywordRetrievalChannel 等。
     */
    private final List<RetrievalChannel> retrievalChannels;

    /** RAG 配置属性 */
    private final ChatRagProperties properties;

    /** Rerank 后处理器（用于精排） */
    private final DocumentPostProcessor rerankPostProcessor;

    /** 文档知识服务（用于父块提升） */
    private final DocumentKnowledgeService documentKnowledgeService;

    /** 线程池（用于并行检索） */
    private final ExecutorService executorService;

    /**
     * 构造函数。
     *
     * @param retrievalChannels       检索通道列表
     * @param properties              RAG 配置属性
     * @param rerankPostProcessor     Rerank 后处理器
     * @param documentKnowledgeService 文档知识服务
     * @param executorService         线程池
     */
    public RagRetrievalEngine(List<RetrievalChannel> retrievalChannels,
                              ChatRagProperties properties,
                              HttpDocumentRerankPostProcessor rerankPostProcessor,
                              DocumentKnowledgeService documentKnowledgeService,
                              @Qualifier("chatRagExecutorService") ExecutorService executorService) {
        this.retrievalChannels = retrievalChannels;
        this.properties = properties;
        this.rerankPostProcessor = rerankPostProcessor;
        this.documentKnowledgeService = documentKnowledgeService;
        this.executorService = executorService;
    }

    /**
     * 【检索入口方法】执行 RAG 检索。
     *
     * 检索流程：
     * 1. 将执行计划中的子问题列表提取出来
     * 2. 为每个子问题并行启动检索任务
     * 3. 等待所有子问题检索完成
     * 4. 为证据分配引用编号（去重复用）
     * 5. 返回包含所有证据的检索上下文
     *
     * @param plan         执行计划（包含检索问题、子问题列表、文档范围等）
     * @param traceRecorder 追踪记录器（可选）
     * @return RagRetrievalContext 检索上下文（包含各子问题的证据列表）
     */
    public RagRetrievalContext retrieve(ConversationExecutionPlan plan, ConversationTraceRecorder traceRecorder) {
        // 一个用户问题可能被拆成多个子问题，每个子问题都会独立检索，最后再汇总证据。
        RagRetrievalContext context = new RagRetrievalContext();
        context.setRetrievalQuestion(plan.getRetrievalQuestion());
        context.setUsedChannels(Collections.synchronizedList(new ArrayList<>()));
        context.setRetrievalNotes(Collections.synchronizedList(new ArrayList<>()));
        List<String> subQuestions = plan.getRetrievalSubQuestions() == null || plan.getRetrievalSubQuestions().isEmpty()
            ? List.of(plan.getRetrievalQuestion())
            : plan.getRetrievalSubQuestions();

        // 为每个子问题创建异步检索任务
        List<CompletableFuture<SubQuestionEvidence>> futures = new ArrayList<>();
        for (int index = 0; index < subQuestions.size(); index++) {
            final int subQuestionIndex = index + 1;
            final String subQuestion = subQuestions.get(index);

            // 子问题之间并行执行，避免复杂问题被串行检索拖慢。
            futures.add(CompletableFuture.supplyAsync(
                    () -> retrieveSingleSubQuestion(subQuestionIndex, subQuestion, plan, context.getUsedChannels(), context.getRetrievalNotes(), traceRecorder),
                    executorService
                )
                // 设置超时时间
                .orTimeout(Math.max(properties.getSubQuestionTimeoutMs(), 1L), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    // 子问题检索失败时返回空证据，不影响其他子问题
                    Throwable rootCause = unwrapThrowable(throwable);
                    log.warn("子问题检索失败: subQuestionIndex={}, subQuestion='{}', exceptionType={}, message={}",
                        subQuestionIndex,
                        subQuestion,
                        rootCause == null ? "" : rootCause.getClass().getName(),
                        rootCause == null ? "" : rootCause.getMessage(),
                        throwable);
                    context.getRetrievalNotes().add("子问题" + subQuestionIndex + "检索失败或超时，已自动忽略。");
                    return new SubQuestionEvidence(subQuestionIndex, subQuestion, List.of(), new ArrayList<>(), List.of(), 0, 0, 0);
                }));
        }

        // 等待所有子问题检索完成
        List<SubQuestionEvidence> evidenceList = futures.stream()
            .map(CompletableFuture::join)
            .toList();
        int acceptedCount = (int) evidenceList.stream().filter(item -> item.getDocuments() != null && !item.getDocuments().isEmpty()).count();
        log.info("RAG 检索完成: retrievalQuestion='{}', originalSubQuestionCount={}, acceptedSubQuestionCount={}, notes={}",
            plan.getRetrievalQuestion(),
            evidenceList.size(),
            acceptedCount,
            context.getRetrievalNotes());
        // 为证据分配引用编号（去重复用）
        assignReferenceIds(evidenceList);
        context.setSubQuestionEvidenceList(evidenceList);
        return context;
    }

    /**
     * 检索单个子问题。
     *
     * 检索流程：
     * 1. 并行调用所有支持的检索通道
     * 2. 对每个通道的结果应用"证据闸门"（过滤低质量结果）
     * 3. 使用 RRF 融合多通道结果
     * 4. 提升到父块（获取更完整的上下文）
     * 5. 重排序（Rerank）
     * 6. 裁剪到 TopK
     *
     * @param subQuestionIndex 子问题编号
     * @param subQuestion      子问题文本
     * @param plan             执行计划
     * @param usedChannels     已使用的通道列表（用于追踪）
     * @param notes            检索备注列表
     * @param traceRecorder    追踪记录器
     * @return SubQuestionEvidence 子问题的证据
     */
    private SubQuestionEvidence retrieveSingleSubQuestion(int subQuestionIndex,
                                                          String subQuestion,
                                                          ConversationExecutionPlan plan,
                                                          List<String> usedChannels,
                                                          List<String> notes,
                                                          ConversationTraceRecorder traceRecorder) {

        // 每个检索通道是一个策略：向量负责语义相似，关键词负责精确命中，两者可以同时工作。
        Map<String, ChannelExecutionTiming> channelTimings = new ConcurrentHashMap<>();
        List<CompletableFuture<RetrievalChannelResult>> futures = retrievalChannels.stream()
            .filter(channel -> channel.supports(plan))
            .map(channel -> {
                long startTimeMs = System.currentTimeMillis();
                return CompletableFuture.supplyAsync(() -> channel.retrieve(subQuestion, plan), executorService)
                .orTimeout(Math.max(properties.getChannelTimeoutMs(), 1L), TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> {
                    long endTimeMs = System.currentTimeMillis();
                    Throwable rootCause = throwable == null ? null : unwrapThrowable(throwable);
                    channelTimings.put(channel.channelName(), new ChannelExecutionTiming(
                        startTimeMs,
                        endTimeMs,
                        rootCause == null ? "" : StrUtil.blankToDefault(rootCause.getMessage(), rootCause.getClass().getSimpleName())
                    ));
                    if (throwable == null) {
                        return result;
                    }
                    // 单个通道失败时返回空结果，不影响其他通道
                    log.warn("检索通道失败: subQuestionIndex={}, subQuestion='{}', channel='{}', exceptionType={}, message={}",
                        subQuestionIndex,
                        subQuestion,
                        channel.channelName(),
                        rootCause == null ? "" : rootCause.getClass().getName(),
                        rootCause == null ? "" : rootCause.getMessage(),
                        throwable);
                    notes.add("子问题" + subQuestionIndex + "通道[" + channel.channelName() + "]检索失败或超时，已自动降级。");
                    return new RetrievalChannelResult(channel.channelName(), List.of());
                });
            })
            .toList();
        if (futures.isEmpty()) {
            notes.add("子问题" + subQuestionIndex + "没有可用的检索通道。");
            return new SubQuestionEvidence(subQuestionIndex, subQuestion, List.of(), new ArrayList<>(), List.of(), 0, 0, 0);
        }

        // 等待所有通道检索完成
        List<RetrievalChannelResult> rawChannelResults = futures.stream()
            .map(CompletableFuture::join)
            .filter(result -> result.getDocuments() != null)
            .toList();
        // 应用证据闸门（过滤低质量结果）
        List<RetrievalChannelResult> channelResults = rawChannelResults.stream()
            .map(this::applyEvidenceGate)
            .toList();
        List<SubQuestionChannelTrace> channelTraces = buildChannelTraces(rawChannelResults, channelResults);

        // 记录已使用的通道
        channelResults.stream()
            .filter(result -> !result.getDocuments().isEmpty())
            .forEach(result -> markUsedChannel(usedChannels, result.getChannelName()));

        // 先融合多通道结果，再提升到父块、重排序、裁剪 TopK，得到最终可喂给模型的证据。
        List<Document> mergedCandidates = fuseByRrf(subQuestion, channelResults);
        List<Document> parentCandidates = documentKnowledgeService.elevateToParentBlocks(
            mergedCandidates,
            properties.getParentEvidenceMaxChars()
        );
        List<Document> rerankedCandidates = applyRerank(subQuestion, parentCandidates, usedChannels);

        List<Document> finalDocuments = selectFinalDocuments(subQuestion, rerankedCandidates);

        notes.add("子问题" + subQuestionIndex + "检索完成："
            + summarizeChannelResults(channelResults)
            + "，final=" + finalDocuments.size());

        // 记录追踪信息
        if (traceRecorder != null) {
            try {
                recordChannelObservations(traceRecorder, subQuestionIndex, subQuestion,
                    rawChannelResults, channelResults, channelTimings, finalDocuments);
                recordRetrievalResultObservations(traceRecorder, subQuestionIndex, subQuestion,
                    rawChannelResults, channelResults, mergedCandidates, rerankedCandidates, finalDocuments);
            } catch (RuntimeException exception) {
                log.warn("记录检索观测数据失败, subQuestionIndex={}", subQuestionIndex, exception);
            }
        }

        return new SubQuestionEvidence(
            subQuestionIndex,
            subQuestion,
            finalDocuments,
            new ArrayList<>(),
            channelTraces,
            mergedCandidates.size(),
            parentCandidates.size(),
            rerankedCandidates.size()
        );
    }

    /**
     * 应用证据闸门：过滤低质量的检索结果。
     *
     * - 向量通道：过滤相似度低于 minVectorSimilarity 的结果
     * - 关键词通道：过滤分数低于最高分 * keywordRelativeScoreFloor 的结果
     */
    private RetrievalChannelResult applyEvidenceGate(RetrievalChannelResult result) {
        if (result == null || result.getDocuments() == null || result.getDocuments().isEmpty()) {
            return result;
        }

        List<Document> documents = switch (result.getChannelName()) {
            case "vector" -> filterVectorCandidates(result.getDocuments());
            case "keyword" -> filterKeywordCandidates(result.getDocuments());
            default -> result.getDocuments();
        };
        return new RetrievalChannelResult(result.getChannelName(), documents);
    }

    /**
     * 过滤向量检索结果：相似度低于阈值的文档被过滤。
     */
    private List<Document> filterVectorCandidates(List<Document> documents) {
        return documents.stream()

            .filter(document -> {
                Double score = resolveScore(document);
                return score != null && score >= properties.getMinVectorSimilarity();
            })
            .toList();
    }

    /**
     * 过滤关键词检索结果：分数低于最高分 * 相对阈值的文档被过滤。
     */
    private List<Document> filterKeywordCandidates(List<Document> documents) {
        Double topScore = documents.stream()
            .map(this::resolveScore)
            .filter(Objects::nonNull)
            .max(Double::compareTo)
            .orElse(null);
        if (topScore == null || topScore <= 0D) {
            return documents;
        }

        double acceptedFloor = topScore * Math.max(0D, properties.getKeywordRelativeScoreFloor());
        return documents.stream()
            .filter(document -> {
                Double score = resolveScore(document);
                return score != null && score >= acceptedFloor;
            })
            .toList();
    }

    /**
     * 使用 RRF（Reciprocal Rank Fusion）算法融合多通道结果。
     *
     * RRF 只关心各通道的排名位置，能把向量和关键词这类分数体系不同的结果合在一起。
     * 公式：RRF_score = sum(channelWeight / (k + rank_i))。
     * 普通语义问题降低关键词权重；精确术语问题恢复关键词权重，兼顾 T2 语义问题与技术文档检索。
     */
    private List<Document> fuseByRrf(String query, List<RetrievalChannelResult> channelResults) {
        // RRF 只关心各通道的排名位置，能把向量和关键词这类分数体系不同的结果合在一起。
        Map<String, CandidateHolder> holders = new LinkedHashMap<>();
        int rrfK = resolveRrfK();
        boolean exactTermQuery = hasExactTermSignal(query);

        for (RetrievalChannelResult retrievalChannelResult : channelResults) {
            double channelWeight = resolveRrfChannelWeight(
                retrievalChannelResult.getChannelName(),
                exactTermQuery
            );
            if (channelWeight > 0D) {
                accumulateRrf(retrievalChannelResult, holders, rrfK, channelWeight);
            }
        }

        return holders.values().stream()
            .sorted(Comparator.comparingDouble((CandidateHolder holder) -> holder.score).reversed()
                .thenComparingInt(holder -> holder.bestRank)
                .thenComparing(holder -> holder.key))
            .limit(resolvePreRerankTopK())
            .map(holder -> {

                holder.document.getMetadata().put(DocumentKnowledgeMetadataKeys.SCORE, holder.score);
                holder.document.getMetadata().put(DocumentKnowledgeMetadataKeys.RRF_SCORE, holder.score);
                holder.document.getMetadata().put(DocumentKnowledgeMetadataKeys.CHANNEL,
                    holder.channels.size() > 1 ? "hybrid" : holder.channels.iterator().next());
                holder.document.getMetadata().put(DocumentKnowledgeMetadataKeys.CHANNEL_COUNT, holder.channels.size());
                return holder.document;
            })
            .toList();
    }

    /**
     * 累加单个通道的 RRF 分数。
     * 排名越靠前（rank 越小），RRF 分数越高。
     */
    private void accumulateRrf(RetrievalChannelResult channelResult,
                               Map<String, CandidateHolder> holders,
                               int rrfK,
                               double channelWeight) {
        List<Document> documents = channelResult.getDocuments();
        for (int rank = 0; rank < documents.size(); rank++) {
            Document document = documents.get(rank);
            if (document == null) {
                continue;
            }
            String documentId = candidateKey(document);

            double rrfScore = channelWeight / (rrfK + rank + 1D);
            CandidateHolder holder = holders.computeIfAbsent(
                documentId,
                ignored -> new CandidateHolder(documentId, document)
            );
            holder.score += rrfScore;
            holder.bestRank = Math.min(holder.bestRank, rank + 1);
            holder.channels.add(channelResult.getChannelName());
        }
    }

    private int resolveRrfK() {
        ChatRagProperties.RrfProperties rrf = properties.getRrf();
        return rrf == null ? 20 : Math.max(1, rrf.getK());
    }

    private double resolveRrfChannelWeight(String channelName, boolean exactTermQuery) {
        ChatRagProperties.RrfProperties rrf = properties.getRrf();
        if (rrf == null) {
            return 1D;
        }
        String normalizedChannel = StrUtil.blankToDefault(channelName, "").toLowerCase(Locale.ROOT);
        double configuredWeight = switch (normalizedChannel) {
            case "vector" -> rrf.getVectorWeight();
            case "keyword" -> exactTermQuery && rrf.isAdaptiveKeywordWeightEnabled()
                ? rrf.getExactTermKeywordWeight()
                : rrf.getKeywordWeight();
            default -> 1D;
        };
        return Double.isFinite(configuredWeight) ? Math.max(0D, configuredWeight) : 0D;
    }

    private boolean hasExactTermSignal(String query) {
        if (StrUtil.isBlank(query)) {
            return false;
        }
        if (EXACT_TERM_PATTERN.matcher(query).find()) {
            return true;
        }
        return EXACT_TERM_HINTS.stream().anyMatch(query::contains);
    }

    private int resolvePreRerankTopK() {
        return Math.max(1, Math.max(properties.getCandidateTopK(), properties.getPreRerankTopK()));
    }

    private String candidateKey(Document document) {
        Object chunkId = document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_ID);
        if (chunkId != null && StrUtil.isNotBlank(String.valueOf(chunkId))) {
            return "chunk:" + chunkId;
        }
        return StrUtil.blankToDefault(document.getId(), String.valueOf(System.identityHashCode(document)));
    }

    /**
     * 在 Rerank 后选取最终证据。文档级多样性控制必须放在片段融合和父块提升之后，
     * 否则同一文档中排名稍后但真正命中问题的条款会被过早丢弃。
     */
    private List<Document> selectFinalDocuments(String subQuestion, List<Document> rerankedCandidates) {
        if (rerankedCandidates == null || rerankedCandidates.isEmpty()) {
            return List.of();
        }
        int finalTopK = Math.max(properties.getFinalTopK(), 1);
        if (!properties.isDocumentLevelDedupEnabled()) {
            return rerankedCandidates.stream().limit(finalTopK).toList();
        }

        Map<String, Integer> selectedDocumentCounts = new LinkedHashMap<>();
        int maxEvidencePerDocument = Math.max(properties.getMaxEvidencePerDocument(), 1);
        List<Document> selectionOrder = prioritizeComplementaryEvidence(
            subQuestion,
            rerankedCandidates,
            maxEvidencePerDocument
        );
        List<Document> selected = new ArrayList<>(finalTopK);
        for (Document candidate : selectionOrder) {
            if (candidate == null) {
                continue;
            }
            Object documentId = candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.DOCUMENT_ID);
            String documentKey = documentId == null || StrUtil.isBlank(String.valueOf(documentId))
                ? evidenceKey(candidate)
                : "doc:" + documentId;
            int selectedCount = selectedDocumentCounts.getOrDefault(documentKey, 0);
            if (selectedCount >= maxEvidencePerDocument) {
                continue;
            }
            selectedDocumentCounts.put(documentKey, selectedCount + 1);
            selected.add(candidate);
            if (selected.size() >= finalTopK) {
                break;
            }
        }
        return selected;
    }

    /**
     * Rerank 擅长挑选主证据，但关系型问题经常需要同一文档中的另一条规则共同完成推理。
     * 例如“深圳住宿上限”需要“住宿标准”和“深圳属于 A 类城市”两个章节。这里仅在候选章节
     * 能补上主证据尚未覆盖的问题锚点时，将其提前到第二位，避免把文档封面等无效片段机械补入。
     */
    private List<Document> prioritizeComplementaryEvidence(String question,
                                                            List<Document> candidates,
                                                            int maxEvidencePerDocument) {
        if (maxEvidencePerDocument < 2 || candidates.size() < 2 || StrUtil.isBlank(question)) {
            return candidates;
        }
        Document primary = candidates.stream().filter(Objects::nonNull).findFirst().orElse(null);
        if (primary == null) {
            return candidates;
        }

        Set<String> queryAnchors = extractQueryAnchors(question);
        if (queryAnchors.isEmpty()) {
            return candidates;
        }
        Set<String> primaryCoveredAnchors = coveredAnchors(queryAnchors, primary.getText());
        String primaryDocumentKey = documentKey(primary);

        Document complement = null;
        int bestComplementScore = 0;
        for (Document candidate : candidates) {
            if (candidate == null || candidate == primary || !primaryDocumentKey.equals(documentKey(candidate))) {
                continue;
            }
            Set<String> candidateAnchors = coveredAnchors(queryAnchors, candidate.getText());
            candidateAnchors.removeAll(primaryCoveredAnchors);
            int score = candidateAnchors.stream().mapToInt(String::length).sum();
            if (score > bestComplementScore) {
                bestComplementScore = score;
                complement = candidate;
            }
        }
        if (complement == null) {
            return candidates;
        }

        List<Document> reordered = new ArrayList<>(candidates.size());
        reordered.add(primary);
        reordered.add(complement);
        for (Document candidate : candidates) {
            if (candidate != primary && candidate != complement) {
                reordered.add(candidate);
            }
        }
        return reordered;
    }

    private Set<String> extractQueryAnchors(String question) {
        LinkedHashSet<String> anchors = new LinkedHashSet<>();
        String normalized = question.toLowerCase(Locale.ROOT);
        StringBuilder token = new StringBuilder();
        for (int index = 0; index <= normalized.length(); index++) {
            char current = index < normalized.length() ? normalized.charAt(index) : ' ';
            if (Character.isLetterOrDigit(current)) {
                token.append(current);
                continue;
            }
            addTokenAnchors(token.toString(), anchors);
            token.setLength(0);
        }
        anchors.removeAll(Set.of("请问", "帮我", "一下", "什么", "怎么", "如何", "多少", "是否", "哪些", "哪个", "需要", "可以", "相关", "规定"));
        return anchors;
    }

    private void addTokenAnchors(String token, Set<String> anchors) {
        if (token.length() < 2) {
            return;
        }
        int maxLength = Math.min(4, token.length());
        for (int length = 2; length <= maxLength; length++) {
            for (int start = 0; start + length <= token.length(); start++) {
                anchors.add(token.substring(start, start + length));
            }
        }
    }

    private Set<String> coveredAnchors(Set<String> anchors, String text) {
        if (StrUtil.isBlank(text)) {
            return new LinkedHashSet<>();
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        return anchors.stream()
            .filter(normalizedText::contains)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String documentKey(Document document) {
        Object documentId = document.getMetadata().get(DocumentKnowledgeMetadataKeys.DOCUMENT_ID);
        return documentId == null || StrUtil.isBlank(String.valueOf(documentId))
            ? evidenceKey(document)
            : "doc:" + documentId;
    }

    /** 统一子块与提升后父块的观测标识，用于追踪原始召回是否进入最终 Prompt。 */
    private String evidenceKey(Document document) {
        if (document == null) {
            return "";
        }
        Object parentBlockId = document.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID);
        if (parentBlockId != null && StrUtil.isNotBlank(String.valueOf(parentBlockId))) {
            return "parent:" + parentBlockId;
        }
        Object chunkId = document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_ID);
        if (chunkId != null && StrUtil.isNotBlank(String.valueOf(chunkId))) {
            return "chunk:" + chunkId;
        }
        return "id:" + StrUtil.blankToDefault(document.getId(), String.valueOf(System.identityHashCode(document)));
    }

    /**
     * 应用 Rerank（重排序/精排）。
     *
     * Rerank 会重新判断"问题和证据"的匹配度，通常比原始召回排序更贴近最终回答需要。
     */
    private List<Document> applyRerank(String subQuestion,
                                       List<Document> candidates,
                                       List<String> usedChannels) {
        if (!properties.isRerankEnabled() || candidates.isEmpty()) {
            return candidates;
        }

        // Rerank 会重新判断"问题和证据"的匹配度，通常比原始召回排序更贴近最终回答需要。
        markUsedChannel(usedChannels, RetrievalChannelEnum.RERANK.getName());
        return rerankPostProcessor.process(new Query(subQuestion), candidates);
    }

    /**
     * 为证据分配引用编号。
     *
     * 同一段证据可能被多个子问题命中，这里用 uniqueKey 去重后复用同一个引用编号。
     * 例如：子问题1和子问题2都检索到了同一段文档，它们会共享同一个引用编号（如 [3]）。
     */
    private void assignReferenceIds(List<SubQuestionEvidence> evidenceList) {
        // 同一段证据可能被多个子问题命中，这里用 uniqueKey 去重后复用同一个引用编号。
        final int[] referenceNumber = {1};
        Map<String, String> assignedIds = new LinkedHashMap<>();
        for (SubQuestionEvidence evidence : evidenceList) {
            List<SearchReference> references = new ArrayList<>();
            for (Document document : evidence.getDocuments()) {

                SearchReference reference = SearchReferenceMapper.fromDocument(
                    document,
                    evidence.getSubQuestionIndex(),
                    evidence.getSubQuestion(),
                    0
                );
                String uniqueKey = reference.uniqueKey();

                String assignedId = assignedIds.computeIfAbsent(uniqueKey, ignored -> String.valueOf(referenceNumber[0]++));
                reference.setReferenceId(assignedId);
                references.add(reference);
            }
            evidence.setReferences(references);
        }
    }

    /**
     * 从文档中解析分数。
     * 优先从 metadata 中获取，如果没有则使用 Spring AI 的 getScore()。
     */
    private Double resolveScore(Document document) {
        if (document == null) {
            return null;
        }

        Object metadataScore = document.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
        if (metadataScore instanceof Number number) {
            return number.doubleValue();
        }
        return document.getScore();
    }

    /** 标记已使用的通道（去重） */
    private void markUsedChannel(List<String> usedChannels, String channel) {

        if (!usedChannels.contains(channel)) {
            usedChannels.add(channel);
        }
    }

    /** 汇总通道检索结果（用于日志） */
    private String summarizeChannelResults(List<RetrievalChannelResult> channelResults) {
        if (channelResults.isEmpty()) {
            return "没有启用任何检索通道";
        }
        return channelResults.stream()
            .map(result -> result.getChannelName() + "=" + result.getDocuments().size())
            .reduce((left, right) -> left + "，" + right)
            .orElse("没有检索结果");
    }

    /**
     * 构建通道追踪信息。
     * 记录每个通道的原始召回数和过滤后保留数。
     */
    private List<SubQuestionChannelTrace> buildChannelTraces(List<RetrievalChannelResult> rawResults,
                                                             List<RetrievalChannelResult> filteredResults) {
        if ((rawResults == null || rawResults.isEmpty()) && (filteredResults == null || filteredResults.isEmpty())) {
            return List.of();
        }
        Map<String, Integer> rawMap = new LinkedHashMap<>();
        Map<String, Integer> filteredMap = new LinkedHashMap<>();
        if (rawResults != null) {
            rawResults.forEach(result -> rawMap.put(result.getChannelName(), result.getDocuments() == null ? 0 : result.getDocuments().size()));
        }
        if (filteredResults != null) {
            filteredResults.forEach(result -> filteredMap.put(result.getChannelName(), result.getDocuments() == null ? 0 : result.getDocuments().size()));
        }
        LinkedHashSet<String> channelNames = new LinkedHashSet<>();
        channelNames.addAll(rawMap.keySet());
        channelNames.addAll(filteredMap.keySet());
        List<SubQuestionChannelTrace> traces = new ArrayList<>(channelNames.size());
        for (String channelName : channelNames) {
            traces.add(new SubQuestionChannelTrace(
                channelName,
                rawMap.getOrDefault(channelName, 0),
                filteredMap.getOrDefault(channelName, 0)
            ));
        }
        return traces;
    }

    /**
     * 记录通道执行观测数据（用于追踪和调试）。
     */
    private void recordChannelObservations(ConversationTraceRecorder traceRecorder,
                                           int subQuestionIndex,
                                           String subQuestion,
                                           List<RetrievalChannelResult> rawResults,
                                           List<RetrievalChannelResult> filteredResults,
                                           Map<String, ChannelExecutionTiming> channelTimings,
                                           List<Document> finalDocuments) {
        if (rawResults == null || rawResults.isEmpty()) {
            return;
        }

        List<ChannelExecutionVo> executions = new ArrayList<>();
        for (RetrievalChannelResult rawResult : rawResults) {
            String channelName = rawResult.getChannelName();
            int recalledCount = rawResult.getDocuments() == null ? 0 : rawResult.getDocuments().size();

            RetrievalChannelResult filteredResult = filteredResults == null ? null :
                filteredResults.stream().filter(r -> channelName.equals(r.getChannelName())).findFirst().orElse(null);
            int acceptedCount = filteredResult == null || filteredResult.getDocuments() == null ? 0 : filteredResult.getDocuments().size();

            LinkedHashSet<String> finalEvidenceKeys = finalDocuments == null
                ? new LinkedHashSet<>()
                : finalDocuments.stream().map(this::evidenceKey)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            int finalSelectedCount = rawResult.getDocuments() == null ? 0 : (int) rawResult.getDocuments().stream()
                .map(this::evidenceKey)
                .filter(finalEvidenceKeys::contains)
                .distinct()
                .count();
            ChannelExecutionTiming timing = channelTimings == null ? null : channelTimings.get(channelName);

            ChannelExecutionVo execution = new ChannelExecutionVo();
            execution.setId(traceRecorder.exchangeId());
            execution.setTraceId(traceRecorder.traceId());
            execution.setSubQuestionIndex(subQuestionIndex);
            execution.setSubQuestion(subQuestion);
            execution.setChannelType(channelName);
            execution.setExecutionState(timing != null && StrUtil.isNotBlank(timing.errorMessage()) ? 3 : 2);
            if (timing != null) {
                execution.setStartTime(Instant.ofEpochMilli(timing.startTimeMs()));
                execution.setEndTime(Instant.ofEpochMilli(timing.endTimeMs()));
                execution.setDurationMs(Math.max(timing.endTimeMs() - timing.startTimeMs(), 0L));
                execution.setErrorMessage(timing.errorMessage());
            }
            execution.setRecalledCount(recalledCount);
            execution.setAcceptedCount(acceptedCount);
            execution.setFinalSelectedCount(finalSelectedCount);

            if (rawResult.getDocuments() != null && !rawResult.getDocuments().isEmpty()) {
                List<Double> scores = rawResult.getDocuments().stream()
                    .map(doc -> {
                        Object scoreObj = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
                        if (scoreObj instanceof Number) {
                            return ((Number) scoreObj).doubleValue();
                        }
                        return 0.0;
                    })
                    .filter(score -> score > 0)
                    .toList();

                if (!scores.isEmpty()) {
                    execution.setAvgScore(BigDecimal.valueOf(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
                    execution.setMaxScore(BigDecimal.valueOf(scores.stream().mapToDouble(Double::doubleValue).max().orElse(0)));
                    execution.setMinScore(BigDecimal.valueOf(scores.stream().mapToDouble(Double::doubleValue).min().orElse(0)));
                }
            }

            executions.add(execution);
        }

        traceRecorder.recordChannelExecutions(executions);
    }

    /**
     * 记录检索结果观测数据（用于追踪和调试）。
     * 记录每个文档的原始分数、RRF 分数、Rerank 分数、是否被选中等信息。
     */
    private void recordRetrievalResultObservations(ConversationTraceRecorder traceRecorder,
                                                   int subQuestionIndex,
                                                   String subQuestion,
                                                   List<RetrievalChannelResult> rawResults,
                                                   List<RetrievalChannelResult> filteredResults,
                                                   List<Document> mergedCandidates,
                                                   List<Document> rerankedCandidates,
                                                   List<Document> finalDocuments) {
        List<RetrievalResultVo> results = new ArrayList<>();
        Map<String, Integer> finalRankMap = new LinkedHashMap<>();
        if (finalDocuments != null) {
            for (int i = 0; i < finalDocuments.size(); i++) {
                String key = evidenceKey(finalDocuments.get(i));
                if (StrUtil.isNotBlank(key)) {
                    finalRankMap.put(key, i + 1);
                }
            }
        }
        Map<String, Integer> rrfRankMap = rankByEvidenceKey(mergedCandidates);
        Map<String, Document> mergedDocumentMap = documentByEvidenceKey(mergedCandidates);
        Map<String, Document> rerankedDocumentMap = documentByEvidenceKey(rerankedCandidates);

        if (rawResults != null) {
            for (RetrievalChannelResult rawResult : rawResults) {
                String channelName = rawResult.getChannelName();
                List<Document> rawDocs = rawResult.getDocuments();
                if (rawDocs == null || rawDocs.isEmpty()) {
                    continue;
                }

                for (int i = 0; i < rawDocs.size(); i++) {
                    Document doc = rawDocs.get(i);
                    RetrievalResultVo view = new RetrievalResultVo();
                    view.setId(traceRecorder.exchangeId());
                    view.setTraceId(traceRecorder.traceId());
                    view.setSubQuestionIndex(subQuestionIndex);
                    view.setSubQuestion(subQuestion);
                    view.setChannelType(channelName);
                    view.setChannelRank(i + 1);
                    String observationEvidenceKey = evidenceKey(doc);
                    view.setRrfRank(rrfRankMap.get(observationEvidenceKey));

                    Object scoreObj = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
                    if (scoreObj instanceof Number) {
                        view.setOriginalScore(BigDecimal.valueOf(((Number) scoreObj).doubleValue()));
                    }

                    Document mergedDocument = mergedDocumentMap.get(observationEvidenceKey);
                    Object rrfScoreObj = mergedDocument == null
                        ? doc.getMetadata().get(DocumentKnowledgeMetadataKeys.RRF_SCORE)
                        : mergedDocument.getMetadata().get(DocumentKnowledgeMetadataKeys.RRF_SCORE);
                    if (rrfScoreObj instanceof Number) {
                        view.setRrfScore(BigDecimal.valueOf(((Number) rrfScoreObj).doubleValue()));
                    }

                    Document rerankedDocument = rerankedDocumentMap.get(observationEvidenceKey);
                    Object rerankScoreObj = rerankedDocument == null
                        ? doc.getMetadata().get("rerankScore")
                        : rerankedDocument.getMetadata().get("rerankScore");
                    if (rerankScoreObj instanceof Number) {
                        view.setRerankScore(BigDecimal.valueOf(((Number) rerankScoreObj).doubleValue()));
                    }

                    Object docIdObj = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.DOCUMENT_ID);
                    if (docIdObj != null) {
                        view.setDocumentId(Long.parseLong(String.valueOf(docIdObj)));
                    }

                    Object docNameObj = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME);
                    if (docNameObj != null) {
                        view.setDocumentName(String.valueOf(docNameObj));
                    }

                    Object chunkIdObj = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_ID);
                    if (chunkIdObj != null) {
                        view.setChunkId(Long.parseLong(String.valueOf(chunkIdObj)));
                    }

                    Object chunkNoObj = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO);
                    if (chunkNoObj != null) {
                        view.setChunkNo(Integer.parseInt(String.valueOf(chunkNoObj)));
                    }

                    Object parentBlockIdObj = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID);
                    if (parentBlockIdObj != null) {
                        view.setParentBlockId(Long.parseLong(String.valueOf(parentBlockIdObj)));
                    }

                    Document evidenceDocument = rerankedDocument == null ? mergedDocument : rerankedDocument;
                    Object parentBlockNoObj = evidenceDocument == null ? null
                        : evidenceDocument.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO);
                    if (parentBlockNoObj != null) {
                        view.setParentBlockNo(Integer.parseInt(String.valueOf(parentBlockNoObj)));
                    }
                    view.setElevated(evidenceDocument != null && !Objects.equals(evidenceDocument.getId(), doc.getId()));

                    Object sectionPathObj = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.SECTION_PATH);
                    if (sectionPathObj != null) {
                        view.setSectionPath(String.valueOf(sectionPathObj));
                    }

                    String content = doc.getText();
                    if (content != null && !content.isEmpty()) {
                        view.setChunkTextPreview(content.length() > 500 ? content.substring(0, 500) : content);
                        view.setChunkCharCount(content.length());
                    }

                    boolean passedGate = filteredResults != null && filteredResults.stream()
                        .anyMatch(fr -> channelName.equals(fr.getChannelName()) &&
                            fr.getDocuments() != null &&
                            fr.getDocuments().stream().anyMatch(d -> Objects.equals(d.getId(), doc.getId())));
                    view.setGatePassed(passedGate);

                    boolean isSelected = finalRankMap.containsKey(observationEvidenceKey);
                    view.setSelected(isSelected);

                    if (isSelected) {
                        view.setFinalRank(finalRankMap.get(observationEvidenceKey));
                        view.setSelectionReason("已贡献到最终 Prompt 证据");
                    } else if (!passedGate) {

                        Object scoreObj2 = doc.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
                        double score = scoreObj2 instanceof Number ? ((Number) scoreObj2).doubleValue() : 0.0;
                        if ("vector".equals(channelName)) {
                            view.setSelectionReason(String.format(
                                "向量闸门过滤：分数 %.4f < 阈值 %.4f",
                                score, properties.getMinVectorSimilarity()
                            ));
                        } else if ("keyword".equals(channelName)) {
                            view.setSelectionReason(String.format(
                                "关键词闸门过滤：分数 %.4f 低于相对阈值（floor=%.2f）",
                                score, properties.getKeywordRelativeScoreFloor()
                            ));
                        } else {
                            view.setSelectionReason("闸门过滤：分数 " + String.format("%.4f", score));
                        }
                    } else {
                        view.setSelectionReason("超出 finalTopK 限制（topK=" + properties.getFinalTopK() + "）");
                    }

                    results.add(view);
                }
            }
        }

        traceRecorder.recordRetrievalResults(results);
    }

    private Map<String, Integer> rankByEvidenceKey(List<Document> documents) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        if (documents == null) {
            return ranks;
        }
        for (int index = 0; index < documents.size(); index++) {
            String key = evidenceKey(documents.get(index));
            if (StrUtil.isNotBlank(key)) {
                ranks.putIfAbsent(key, index + 1);
            }
        }
        return ranks;
    }

    private Map<String, Document> documentByEvidenceKey(List<Document> documents) {
        Map<String, Document> indexed = new LinkedHashMap<>();
        if (documents == null) {
            return indexed;
        }
        for (Document document : documents) {
            String key = evidenceKey(document);
            if (StrUtil.isNotBlank(key)) {
                indexed.putIfAbsent(key, document);
            }
        }
        return indexed;
    }

    /** 单个检索通道的实际执行观测。 */
    private record ChannelExecutionTiming(long startTimeMs, long endTimeMs, String errorMessage) {
    }

    /**
     * 【候选文档持有者】内部类，用于 RRF 融合过程中的临时数据存储。
     *
     * 每个文档对应一个 CandidateHolder，记录：
     * - document：原始文档对象
     * - channels：该文档被哪些通道检索到（用于判断是否为 hybrid 结果）
     * - score：累积的 RRF 分数
     */
    private static class CandidateHolder {

        private final String key;
        private final Document document;
        private final LinkedHashSet<String> channels = new LinkedHashSet<>();
        private int bestRank = Integer.MAX_VALUE;
        private double score;

        private CandidateHolder(String key, Document document) {
            this.key = key;
            this.document = document;
        }
    }

    /**
     * 解包异常链，找到根本原因。
     * CompletableFuture 会把异常包装在 CompletionException 中，这里逐层解包。
     */
    private Throwable unwrapThrowable(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null
            && current.getCause() != current
            && (current instanceof CompletionException
            || current instanceof ExecutionException
            || current instanceof TimeoutException)) {
            current = current.getCause();
        }
        return current;
    }
}
