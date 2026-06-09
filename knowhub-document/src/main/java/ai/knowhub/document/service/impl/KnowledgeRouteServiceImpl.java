package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentProfile;
import ai.knowhub.document.data.KnowHubKnowledgeRouteTrace;
import ai.knowhub.document.data.KnowHubKnowledgeScopeNode;
import ai.knowhub.document.data.KnowHubKnowledgeTopicNode;
import ai.knowhub.document.data.KnowHubTopicDocumentRelation;
import ai.knowhub.document.mapper.KnowHubDocumentMapper;
import ai.knowhub.document.mapper.KnowHubDocumentProfileMapper;
import ai.knowhub.document.mapper.KnowHubKnowledgeRouteTraceMapper;
import ai.knowhub.document.mapper.KnowHubKnowledgeScopeNodeMapper;
import ai.knowhub.document.mapper.KnowHubKnowledgeTopicNodeMapper;
import ai.knowhub.document.mapper.KnowHubTopicDocumentRelationMapper;
import ai.knowhub.document.model.route.DocumentRouteCandidate;
import ai.knowhub.document.model.route.KnowledgeRouteDecision;
import ai.knowhub.document.model.route.ScopeRouteCandidate;
import ai.knowhub.document.model.route.TopicRouteCandidate;
import ai.knowhub.document.service.KnowledgeRouteIndexService;
import ai.knowhub.document.service.KnowledgeRouteService;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.enums.DocumentIndexStatusEnum;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 【知识路由服务实现】
 *
 * 这个类是 KnowledgeRouteService 接口的核心实现，负责将用户问题路由到最相关的知识范围、主题和文档。
 * 它是 RAG 流程中"检索"阶段的前置组件，决定了从哪些文档中检索知识。
 *
 * 三级路由架构：
 *   1. Scope 路由：确定问题属于哪个知识范围（如"人事管理"、"财务管理"）
 *   2. Topic 路由：在匹配的知识范围内，确定问题涉及哪个主题（如"请假制度"、"报销流程"）
 *   3. Document 路由：在匹配的主题下，确定应该从哪些文档中检索知识
 *
 * 评分机制（每个候选的最终得分）：
 *   - 语义得分（semanticMainScore）：基于 embedding 余弦相似度，阈值 0.20 以上才有分
 *     公式：(cosine_similarity - 0.20) * 50
 *   - 词法得分（lexicalAssist）：基于 ES 全文搜索得分，最多贡献 10 分
 *   - 关键词实体辅助（keywordEntityAssist）：实体关键词命中 +6 分/个
 *   - Scope/Topic 关联加分：文档属于 Top Scope +15 分，属于 Top Topic 按关联分数 * 20
 *
 * 置信度计算：
 *   confidence = topScore / (10 + topScore + secondScore + 5)
 *   - >= 0.55：SUCCESS（高置信度）
 *   - < 0.55：LOW_CONFIDENCE（低置信度，进入保守扩范围候选）
 *   - 无候选文档：FAILED
 *
 * 影子路由（Shadow Route）：
 *   在非自动路由模式下，系统仍然会执行路由计算并将结果记录到 trace 表中，
 *   用于后续分析路由质量和优化路由策略。
 *
 * 设计模式：三级漏斗模式（Scope -> Topic -> Document 逐级过滤），
 *           混合评分模式（语义 + 词法 + 实体关键词综合评分）
 */
@Slf4j
@AllArgsConstructor
@Service
public class KnowledgeRouteServiceImpl implements KnowledgeRouteService {

    /** 路由状态：成功 */
    private static final int ROUTE_STATUS_SUCCESS = 1;

    /** 路由状态：低置信度 */
    private static final int ROUTE_STATUS_LOW_CONFIDENCE = 2;

    /** 路由状态：失败 */
    private static final int ROUTE_STATUS_FAILED = 3;

    /** 候选向量计算的批量大小（避免一次 embedding 太多导致超时） */
    private static final int ROUTE_EMBEDDING_BATCH_SIZE = 10;

    /** 自动知识路由默认保留的文档候选数，避免过早收缩导致 gold document 被截断。 */
    private static final int ROUTE_DOCUMENT_CANDIDATE_LIMIT = 20;

    /** 文档 Mapper */
    private final KnowHubDocumentMapper documentMapper;

    /** 文档画像 Mapper */
    private final KnowHubDocumentProfileMapper documentProfileMapper;

    /** 知识范围 Mapper */
    private final KnowHubKnowledgeScopeNodeMapper scopeNodeMapper;

    /** 知识主题 Mapper */
    private final KnowHubKnowledgeTopicNodeMapper topicNodeMapper;

    /** 主题-文档关联 Mapper */
    private final KnowHubTopicDocumentRelationMapper topicDocumentRelationMapper;

    /** 路由追踪记录 Mapper */
    private final KnowHubKnowledgeRouteTraceMapper knowledgeRouteTraceMapper;

    /** Embedding 模型的延迟提供者（可能未配置） */
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    /** 知识路由索引服务的延迟提供者（ES 实现，可能未启用） */
    private final ObjectProvider<KnowledgeRouteIndexService> knowledgeRouteIndexServiceProvider;

    /** 百度 UID 生成器 */
    private final UidGenerator uidGenerator;

    /**
     * 执行知识路由。
     *
     * 流程：
     *   1. 构建查询上下文（合并原始问题和改写问题，提取关键词，生成 embedding）
     *   2. 第一级：rankScopes - 对所有知识范围评分排序
     *   3. 第二级：rankTopics - 对所有知识主题评分排序（Scope 关联加分）
     *   4. 第三级：rankDocuments - 对所有可检索文档评分排序（Scope/Topic 关联加分）
     *   5. 计算置信度，判断路由状态
     *
     * @param question       原始用户问题
     * @param rewriteQuestion 改写后的用户问题（查询扩展）
     * @return 路由决策结果（包含 Scope/Topic/Document 候选列表和置信度）
     */
    @Override
    public KnowledgeRouteDecision route(String question, String rewriteQuestion) {
        RouteQueryContext queryContext = buildQueryContext(question, rewriteQuestion);
        KnowledgeRouteDecision decision = new KnowledgeRouteDecision();
        if (queryContext.queryTerms().isEmpty()) {
            decision.setRouteStatus("FAILED");
            decision.setReason("问题为空或无法提取有效关键词");
            return decision;
        }
        // 三级路由：Scope -> Topic -> Document
        List<ScopeRouteCandidate> scopeCandidates = rankScopes(queryContext);
        List<TopicRouteCandidate> topicCandidates = rankTopics(queryContext, scopeCandidates);
        List<DocumentRouteCandidate> documentCandidates = rankDocuments(queryContext, scopeCandidates, topicCandidates);
        decision.setScopes(scopeCandidates);
        decision.setTopics(topicCandidates);
        decision.setDocuments(documentCandidates);
        // 计算置信度
        BigDecimal confidence = resolveConfidence(documentCandidates);
        decision.setConfidence(confidence);
        if (documentCandidates.isEmpty()) {
            decision.setRouteStatus("FAILED");
        }
        else if (confidence.compareTo(BigDecimal.valueOf(0.55D)) < 0) {
            decision.setRouteStatus("LOW_CONFIDENCE");
        }
        else {
            decision.setRouteStatus("SUCCESS");
        }
        decision.setReason(documentCandidates.isEmpty()
            ? "没有找到可用候选文档"
            : resolveDecisionReason(documentCandidates, confidence));
        log.info("知识范围路由完成: question='{}', rewriteQuestion='{}', scopeCount={}, topicCount={}, documentCount={}, confidence={}, topDocument='{}'",
            StrUtil.blankToDefault(question, ""),
            StrUtil.blankToDefault(rewriteQuestion, ""),
            scopeCandidates.size(),
            topicCandidates.size(),
            documentCandidates.size(),
            confidence,
            documentCandidates.isEmpty() ? "" : documentCandidates.get(0).getDocumentName());
        return decision;
    }

    /**
     * 记录影子路由结果。
     * 在非自动路由模式下，系统仍然执行路由计算并将结果保存到 trace 表，
     * 用于后续分析路由质量。
     *
     * @param conversationId   会话ID
     * @param exchangeId       交互轮次ID
     * @param selectedDocumentId 实际选中的文档ID（可能与路由推荐不同）
     * @param question         原始问题
     * @param rewriteQuestion  改写后的问题
     */
    @Override
    public void recordShadowRoute(String conversationId,
                                  long exchangeId,
                                  Long selectedDocumentId,
                                  String question,
                                  String rewriteQuestion) {
        try {
            KnowledgeRouteDecision decision = route(question, rewriteQuestion);
            saveTrace(conversationId, exchangeId, selectedDocumentId, question, rewriteQuestion, "shadow", decision);
        }
        catch (Exception exception) {
            log.warn("记录知识路由影子结果失败: conversationId={}, exchangeId={}", conversationId, exchangeId, exception);
        }
    }

    /**
     * 记录自动路由结果。
     *
     * @param conversationId   会话ID
     * @param exchangeId       交互轮次ID
     * @param question         原始问题
     * @param rewriteQuestion  改写后的问题
     * @param decision         路由决策结果
     */
    @Override
    public void recordAutoRoute(String conversationId,
                                long exchangeId,
                                String question,
                                String rewriteQuestion,
                                KnowledgeRouteDecision decision) {
        try {
            Long selectedDocumentId = decision == null || decision.topDocument() == null || StrUtil.isBlank(decision.topDocument().getDocumentId())
                ? null
                : Long.valueOf(decision.topDocument().getDocumentId());
            saveTrace(conversationId, exchangeId, selectedDocumentId, question, rewriteQuestion, "auto", decision);
        }
        catch (Exception exception) {
            log.warn("记录知识路由 AUTO 结果失败: conversationId={}, exchangeId={}", conversationId, exchangeId, exception);
        }
    }

    /**
     * 保存路由追踪记录到数据库。
     * 记录包含：问题、路由模式、Top Scope/Topic/Document 的 JSON 快照、
     * 是否命中选中文档、置信度、路由状态等。
     */
    private void saveTrace(String conversationId,
                           long exchangeId,
                           Long selectedDocumentId,
                           String question,
                           String rewriteQuestion,
                           String mode,
                           KnowledgeRouteDecision decision) {
        KnowHubKnowledgeRouteTrace trace = new KnowHubKnowledgeRouteTrace();
        trace.setId(uidGenerator.getUid());
        trace.setConversationId(conversationId);
        trace.setExchangeId(exchangeId);
        trace.setQuestion(question);
        trace.setRewriteQuestion(rewriteQuestion);
        trace.setMode(mode);
        trace.setTopScopesJson(writeScopeJson(decision == null ? List.of() : decision.getScopes()));
        trace.setTopTopicsJson(writeTopicJson(decision == null ? List.of() : decision.getTopics()));
        trace.setTopDocumentsJson(writeDocumentJson(decision == null ? List.of() : decision.getDocuments()));
        trace.setSelectedDocumentId(selectedDocumentId);
        trace.setHitSelectedDocument(resolveHitSelectedDocument(selectedDocumentId, decision));
        trace.setConfidence(decision == null ? BigDecimal.ZERO : decision.getConfidence());
        trace.setRouteStatus(resolveRouteStatus(decision));
        trace.setErrorMsg(decision == null ? "" : StrUtil.blankToDefault(decision.getReason(), ""));
        trace.setStatus(BusinessStatus.YES.getCode());
        knowledgeRouteTraceMapper.insert(trace);
    }

    /** 将路由状态字符串转为数据库存储的整数编码 */
    private Integer resolveRouteStatus(KnowledgeRouteDecision decision) {
        if (decision == null) {
            return ROUTE_STATUS_FAILED;
        }
        return switch (StrUtil.blankToDefault(decision.getRouteStatus(), "FAILED")) {
            case "SUCCESS" -> ROUTE_STATUS_SUCCESS;
            case "LOW_CONFIDENCE" -> ROUTE_STATUS_LOW_CONFIDENCE;
            default -> ROUTE_STATUS_FAILED;
        };
    }

    /**
     * 判断路由推荐的 Top-3 文档是否包含实际选中的文档。
     * 用于评估路由准确性。
     */
    private Integer resolveHitSelectedDocument(Long selectedDocumentId, KnowledgeRouteDecision decision) {
        if (selectedDocumentId == null || decision == null || decision.getDocuments() == null || decision.getDocuments().isEmpty()) {
            return null;
        }
        boolean hit = decision.getDocuments().stream()
            .limit(3)
            .anyMatch(item -> Objects.equals(String.valueOf(selectedDocumentId), item.getDocumentId()));
        return hit ? 1 : 0;
    }

    /**
     * 第一级路由：对所有知识范围评分排序。
     *
     * 评分组成：
     *   - 语义得分（embedding 余弦相似度）
     *   - 词法得分（ES 全文搜索得分）
     *   - 关键词实体辅助（实体关键词命中）
     *
     * 如果数据库中没有 Scope 节点，从文档的知识范围字段中推导。
     */
    private List<ScopeRouteCandidate> rankScopes(RouteQueryContext queryContext) {
        List<KnowHubKnowledgeScopeNode> nodes = scopeNodeMapper.selectList(new LambdaQueryWrapper<KnowHubKnowledgeScopeNode>()
            .eq(KnowHubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode()));
        if (nodes.isEmpty()) {
            return deriveScopesFromDocuments(queryContext);
        }
        List<String> routeTexts = nodes.stream()
            .map(node -> join(node.getScopeName(), node.getDescription(), node.getAliases(), node.getExamples()))
            .toList();
        List<Double> semanticScores = computeSemanticScores(queryContext, routeTexts);
        Map<String, Double> lexicalScores = searchLexicalScores(queryContext.routingText(), "scope", 5).stream()
            .collect(Collectors.toMap(KnowledgeRouteIndexService.RouteLexicalHit::entityCode, KnowledgeRouteIndexService.RouteLexicalHit::score, (left, right) -> left));
        return buildScopeCandidates(queryContext, nodes, routeTexts, semanticScores, lexicalScores);
    }

    /**
     * 从文档的知识范围字段推导 Scope 候选。
     * 当数据库中没有显式的 Scope 节点时使用此降级方案。
     */
    private List<ScopeRouteCandidate> deriveScopesFromDocuments(RouteQueryContext queryContext) {
        List<KnowHubDocument> documents = listRetrievableDocuments();
        Map<String, ScopeAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (KnowHubDocument document : documents) {
            if (StrUtil.isBlank(document.getKnowledgeScopeCode()) && StrUtil.isBlank(document.getKnowledgeScopeName())) {
                continue;
            }
            String code = firstNonBlank(document.getKnowledgeScopeCode(), "general_document");
            String name = firstNonBlank(document.getKnowledgeScopeName(), "通用文档");
            String routeText = join(code, name, document.getBusinessCategory(), document.getDocumentTags());
            double score = keywordEntityAssist(queryContext.queryTerms(), routeText);
            double semanticScore = semanticScore(queryContext, routeText);
            ScopeAccumulator accumulator = accumulatorMap.computeIfAbsent(code, key -> new ScopeAccumulator(code, name));
            if (score + semanticMainScore(semanticScore) > accumulator.maxScore) {
                accumulator.maxScore = score + semanticMainScore(semanticScore);
                accumulator.reason = buildReason(queryContext.queryTerms(), routeText, semanticScore);
            }
        }
        return accumulatorMap.values().stream()
            .filter(item -> item.maxScore > 0D || queryContext.semanticEnabled())
            .map(item -> new ScopeRouteCandidate(item.scopeCode, item.scopeName, scoreToBigDecimal(item.maxScore), item.reason))
            .sorted((left, right) -> right.getScore().compareTo(left.getScore()))
            .limit(ROUTE_DOCUMENT_CANDIDATE_LIMIT)
            .toList();
    }

    /**
     * 第二级路由：对所有知识主题评分排序。
     *
     * 额外加分：如果主题属于 Top Scope，额外 +8 分。
     * 如果数据库中没有 Topic 节点，从文档画像的核心主题中推导。
     */
    private List<TopicRouteCandidate> rankTopics(RouteQueryContext queryContext, List<ScopeRouteCandidate> scopeCandidates) {
        List<KnowHubKnowledgeTopicNode> nodes = topicNodeMapper.selectList(new LambdaQueryWrapper<KnowHubKnowledgeTopicNode>()
            .eq(KnowHubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode()));
        Set<String> preferredScopes = scopeCandidates.stream().map(ScopeRouteCandidate::getScopeCode).collect(Collectors.toSet());
        if (nodes.isEmpty()) {
            return deriveTopicsFromProfiles(queryContext, preferredScopes);
        }
        List<String> routeTexts = nodes.stream()
            .map(node -> join(
                node.getTopicName(),
                node.getDescription(),
                node.getAliases(),
                node.getExamples(),
                node.getAnswerShape(),
                node.getExecutionPreference()
            ))
            .toList();
        List<Double> semanticScores = computeSemanticScores(queryContext, routeTexts);
        Map<String, Double> lexicalScores = searchLexicalScores(queryContext.routingText(), "topic", 8).stream()
            .collect(Collectors.toMap(KnowledgeRouteIndexService.RouteLexicalHit::entityCode, KnowledgeRouteIndexService.RouteLexicalHit::score, (left, right) -> left));
        List<TopicRouteCandidate> candidates = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            KnowHubKnowledgeTopicNode node = nodes.get(index);
            String routeText = routeTexts.get(index);
            double score = semanticMainScore(semanticScores.get(index))
                + lexicalAssist(lexicalScores.get(node.getTopicCode()))
                + keywordEntityAssist(queryContext.queryTerms(), routeText);
            // 属于 Top Scope 的主题额外加分
            if (!preferredScopes.isEmpty() && preferredScopes.contains(node.getScopeCode())) {
                score += 8D;
            }
            if (score > 0D || queryContext.semanticEnabled()) {
                candidates.add(new TopicRouteCandidate(
                    node.getTopicCode(),
                    node.getTopicName(),
                    node.getScopeCode(),
                    scoreToBigDecimal(score),
                    buildReason(queryContext.queryTerms(), routeText, semanticScores.get(index))
                ));
            }
        }
        return candidates.stream()
            .sorted((left, right) -> right.getScore().compareTo(left.getScore()))
            .limit(8)
            .toList();
    }

    /**
     * 从文档画像的核心主题中推导 Topic 候选。
     * 当数据库中没有显式的 Topic 节点时使用此降级方案。
     */
    private List<TopicRouteCandidate> deriveTopicsFromProfiles(RouteQueryContext queryContext, Set<String> preferredScopes) {
        List<KnowHubDocumentProfile> profiles = documentProfileMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentProfile>()
            .eq(KnowHubDocumentProfile::getStatus, BusinessStatus.YES.getCode())
            .eq(KnowHubDocumentProfile::getProfileStatus, 2));
        Map<String, TopicAccumulator> accumulatorMap = new LinkedHashMap<>();
        Map<Long, KnowHubDocument> documentMap = listRetrievableDocuments().stream()
            .collect(Collectors.toMap(KnowHubDocument::getId, item -> item));
        for (KnowHubDocumentProfile profile : profiles) {
            KnowHubDocument document = documentMap.get(profile.getDocumentId());
            String scopeCode = document == null ? "" : StrUtil.blankToDefault(document.getKnowledgeScopeCode(), "");
            for (String topic : parseJsonArray(profile.getCoreTopics())) {
                String routeText = join(topic, profile.getDocumentSummary(), profile.getExampleQuestions());
                double score = keywordEntityAssist(queryContext.queryTerms(), routeText);
                double semanticScore = semanticScore(queryContext, routeText);
                if (!preferredScopes.isEmpty() && preferredScopes.contains(scopeCode)) {
                    score += 6D;
                }
                TopicAccumulator accumulator = accumulatorMap.computeIfAbsent(topic, key -> new TopicAccumulator(topic, scopeCode));
                double finalScore = score + semanticMainScore(semanticScore);
                if (finalScore > accumulator.maxScore) {
                    accumulator.maxScore = finalScore;
                    accumulator.reason = buildReason(queryContext.queryTerms(), routeText, semanticScore);
                }
            }
        }
        return accumulatorMap.values().stream()
            .filter(item -> item.maxScore > 0D || queryContext.semanticEnabled())
            .map(item -> new TopicRouteCandidate(normalizeCode(item.topicName), item.topicName, item.scopeCode, scoreToBigDecimal(item.maxScore), item.reason))
            .sorted((left, right) -> right.getScore().compareTo(left.getScore()))
            .limit(8)
            .toList();
    }

    /**
     * 第三级路由：对所有可检索文档评分排序。
     *
     * 额外加分：
     *   - 文档属于 Top Scope：+15 分
     *   - 文档关联 Top Topic：关联分数 * 20
     */
    private List<DocumentRouteCandidate> rankDocuments(RouteQueryContext queryContext,
                                                       List<ScopeRouteCandidate> scopeCandidates,
                                                       List<TopicRouteCandidate> topicCandidates) {
        List<KnowHubDocument> documents = listRetrievableDocuments();
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowHubDocumentProfile> profileMap = documentProfileMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentProfile>()
                .eq(KnowHubDocumentProfile::getStatus, BusinessStatus.YES.getCode())
                .eq(KnowHubDocumentProfile::getProfileStatus, 2))
            .stream()
            .collect(Collectors.toMap(KnowHubDocumentProfile::getDocumentId, item -> item, (left, right) -> right));
        Map<String, Map<Long, KnowHubTopicDocumentRelation>> topicRelationMap = topicDocumentRelationMapper.selectList(
                new LambdaQueryWrapper<KnowHubTopicDocumentRelation>()
                    .eq(KnowHubTopicDocumentRelation::getStatus, BusinessStatus.YES.getCode()))
            .stream()
            .collect(Collectors.groupingBy(KnowHubTopicDocumentRelation::getTopicCode,
                Collectors.toMap(KnowHubTopicDocumentRelation::getDocumentId, item -> item, (left, right) -> right)));
        String topScopeCode = scopeCandidates.isEmpty() ? "" : scopeCandidates.get(0).getScopeCode();
        String topTopicCode = topicCandidates.isEmpty() ? "" : topicCandidates.get(0).getTopicCode();
        List<DocumentRouteMaterial> materials = documents.stream()
            .map(document -> buildDocumentRouteMaterial(document, profileMap.get(document.getId())))
            .toList();
        List<Double> semanticScores = computeSemanticScores(queryContext, materials.stream().map(DocumentRouteMaterial::routeText).toList());
        Map<Long, Double> lexicalScores = searchLexicalScores(queryContext.routingText(), "document", ROUTE_DOCUMENT_CANDIDATE_LIMIT).stream()
            .filter(hit -> hit.documentId() != null)
            .collect(Collectors.toMap(KnowledgeRouteIndexService.RouteLexicalHit::documentId, KnowledgeRouteIndexService.RouteLexicalHit::score, (left, right) -> left));
        return documents.stream()
            .map(document -> buildDocumentCandidate(
                queryContext,
                document,
                profileMap.get(document.getId()),
                topScopeCode,
                topTopicCode,
                topicRelationMap,
                materials,
                semanticScores,
                lexicalScores
            ))
            .filter(candidate -> candidate.getScore().compareTo(BigDecimal.ZERO) > 0 || queryContext.semanticEnabled())
            .sorted((left, right) -> right.getScore().compareTo(left.getScore()))
            .limit(ROUTE_DOCUMENT_CANDIDATE_LIMIT)
            .toList();
    }

    /**
     * 构建单个文档的路由候选。
     * 评分组成：语义得分 + 词法得分 + 实体关键词辅助 + Scope 关联加分 + Topic 关联加分。
     */
    private DocumentRouteCandidate buildDocumentCandidate(RouteQueryContext queryContext,
                                                          KnowHubDocument document,
                                                          KnowHubDocumentProfile profile,
                                                          String topScopeCode,
                                                          String topTopicCode,
                                                          Map<String, Map<Long, KnowHubTopicDocumentRelation>> topicRelationMap,
                                                          List<DocumentRouteMaterial> materials,
                                                          List<Double> semanticScores,
                                                          Map<Long, Double> lexicalScores) {
        int materialIndex = findMaterialIndex(materials, document.getId());
        String routeText = materialIndex >= 0 ? materials.get(materialIndex).routeText() : join(
            document.getDocumentName(),
            document.getKnowledgeScopeName(),
            document.getKnowledgeScopeCode(),
            document.getBusinessCategory(),
            document.getDocumentTags()
        );
        double semanticScore = materialIndex >= 0 && materialIndex < semanticScores.size() ? semanticScores.get(materialIndex) : 0D;
        double score = semanticMainScore(semanticScore)
            + lexicalAssist(lexicalScores.get(document.getId()))
            + keywordEntityAssist(queryContext.queryTerms(), routeText);
        // 属于 Top Scope 的文档额外 +15 分
        if (StrUtil.isNotBlank(topScopeCode) && topScopeCode.equals(document.getKnowledgeScopeCode())) {
            score += 15D;
        }
        // 关联 Top Topic 的文档按关联分数 * 20 加分
        if (StrUtil.isNotBlank(topTopicCode)) {
            Map<Long, KnowHubTopicDocumentRelation> relationMap = topicRelationMap.get(topTopicCode);
            if (relationMap != null) {
                KnowHubTopicDocumentRelation relation = relationMap.get(document.getId());
                if (relation != null && relation.getRelationScore() != null) {
                    score += relation.getRelationScore().doubleValue() * 20D;
                }
            }
        }
        if (score <= 0D && !queryContext.semanticEnabled()) {
            return new DocumentRouteCandidate(
                String.valueOf(document.getId()),
                document.getDocumentName(),
                document.getLastIndexTaskId() == null ? "" : String.valueOf(document.getLastIndexTaskId()),
                StrUtil.blankToDefault(document.getKnowledgeScopeCode(), ""),
                StrUtil.blankToDefault(document.getKnowledgeScopeName(), ""),
                StrUtil.blankToDefault(document.getBusinessCategory(), ""),
                StrUtil.blankToDefault(document.getDocumentTags(), ""),
                BigDecimal.ZERO,
                "未命中路由关键词"
            );
        }
        return new DocumentRouteCandidate(
            String.valueOf(document.getId()),
            document.getDocumentName(),
            document.getLastIndexTaskId() == null ? "" : String.valueOf(document.getLastIndexTaskId()),
            StrUtil.blankToDefault(document.getKnowledgeScopeCode(), ""),
            StrUtil.blankToDefault(document.getKnowledgeScopeName(), ""),
            StrUtil.blankToDefault(document.getBusinessCategory(), ""),
            StrUtil.blankToDefault(document.getDocumentTags(), ""),
            scoreToBigDecimal(score),
            buildReason(queryContext.queryTerms(), routeText, semanticScore)
        );
    }

    /**
     * 构建查询上下文。
     * 合并原始问题和改写问题，提取关键词，生成 embedding。
     */
    private RouteQueryContext buildQueryContext(String question, String rewriteQuestion) {
        String routingText = buildRoutingText(question, rewriteQuestion);
        List<String> queryTerms = tokenize(routingText);
        float[] queryEmbedding = embedSingle(routingText);
        return new RouteQueryContext(
            StrUtil.blankToDefault(question, ""),
            StrUtil.blankToDefault(rewriteQuestion, ""),
            routingText,
            queryTerms,
            queryEmbedding
        );
    }

    /**
     * 构建路由文本。
     * 合并原始问题和改写问题（如果不同的话）。
     */
    private String buildRoutingText(String question, String rewriteQuestion) {
        String original = StrUtil.blankToDefault(question, "").trim();
        String rewritten = StrUtil.blankToDefault(rewriteQuestion, "").trim();
        if (StrUtil.isBlank(original)) {
            return rewritten;
        }
        if (StrUtil.isBlank(rewritten) || Objects.equals(original, rewritten)) {
            return original;
        }
        return original + " " + rewritten;
    }

    /**
     * 构建 Scope 候选列表。
     * 综合语义得分、词法得分和实体关键词辅助得分。
     */
    private List<ScopeRouteCandidate> buildScopeCandidates(RouteQueryContext queryContext,
                                                           List<KnowHubKnowledgeScopeNode> nodes,
                                                           List<String> routeTexts,
                                                           List<Double> semanticScores,
                                                           Map<String, Double> lexicalScores) {
        List<ScopeRouteCandidate> candidates = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            KnowHubKnowledgeScopeNode node = nodes.get(index);
            String routeText = routeTexts.get(index);
            double finalScore = semanticMainScore(semanticScores.get(index))
                + lexicalAssist(lexicalScores.get(node.getScopeCode()))
                + keywordEntityAssist(queryContext.queryTerms(), routeText);
            if (finalScore > 0D || semanticScores.get(index) > 0D) {
                candidates.add(new ScopeRouteCandidate(
                    node.getScopeCode(),
                    node.getScopeName(),
                    scoreToBigDecimal(finalScore),
                    buildReason(queryContext.queryTerms(), routeText, semanticScores.get(index))
                ));
            }
        }
        return candidates.stream()
            .sorted((left, right) -> right.getScore().compareTo(left.getScore()))
            .limit(5)
            .toList();
    }

    /** 构建文档路由素材（拼接文档的所有路由文本） */
    private DocumentRouteMaterial buildDocumentRouteMaterial(KnowHubDocument document, KnowHubDocumentProfile profile) {
        return new DocumentRouteMaterial(
            document.getId(),
            join(
                document.getDocumentName(),
                document.getKnowledgeScopeName(),
                document.getKnowledgeScopeCode(),
                document.getBusinessCategory(),
                document.getDocumentTags(),
                profile == null ? "" : profile.getDocumentSummary(),
                profile == null ? "" : profile.getCoreTopics(),
                profile == null ? "" : profile.getExampleQuestions(),
                profile == null ? "" : profile.getDocumentType()
            )
        );
    }

    /** 在素材列表中查找指定文档ID的索引 */
    private int findMaterialIndex(List<DocumentRouteMaterial> materials, Long documentId) {
        for (int index = 0; index < materials.size(); index++) {
            if (Objects.equals(materials.get(index).documentId(), documentId)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 计算路由置信度。
     * 公式：topScore / (10 + topScore + secondScore + 5)
     * 当 topScore 远大于 secondScore 时，置信度接近 1。
     */
    private BigDecimal resolveConfidence(List<DocumentRouteCandidate> documents) {
        if (documents == null || documents.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double top = documents.get(0).getScore().doubleValue();
        double second = documents.size() > 1 ? documents.get(1).getScore().doubleValue() : 0D;
        double normalized = top / Math.max(10D, top + second + 5D);
        return scoreToBigDecimal(normalized);
    }

    /** 列出所有可检索的文档（索引构建成功且有 lastIndexTaskId） */
    private List<KnowHubDocument> listRetrievableDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowHubDocument>()
            .eq(KnowHubDocument::getStatus, BusinessStatus.YES.getCode())
            .eq(KnowHubDocument::getIndexStatus, DocumentIndexStatusEnum.BUILD_SUCCESS.getCode())
            .isNotNull(KnowHubDocument::getLastIndexTaskId)
            .orderByAsc(KnowHubDocument::getId));
    }

    /**
     * 计算词法得分（本地实现，用于无 ES 时的降级方案）。
     * 按关键词长度加权：长关键词匹配权重更高。
     * 避免短关键词被长关键词覆盖（如"管理"被"用户管理"覆盖）。
     */
    private double lexicalScore(List<String> queryTerms, String content) {
        String normalizedContent = normalize(content);
        if (queryTerms.isEmpty() || normalizedContent.isBlank()) {
            return 0D;
        }
        double score = 0D;
        List<String> sortedTerms = queryTerms.stream()
            .map(this::normalize)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
        List<String> matchedTerms = new ArrayList<>();
        for (String term : sortedTerms) {
            String normalizedTerm = normalize(term);
            if (normalizedTerm.length() < 2) {
                continue;
            }
            // 跳过已被更长关键词覆盖的短关键词
            boolean coveredByLongerTerm = matchedTerms.stream().anyMatch(existing -> existing.contains(normalizedTerm));
            if (coveredByLongerTerm) {
                continue;
            }
            if (normalizedContent.contains(normalizedTerm)) {
                matchedTerms.add(normalizedTerm);
                score += lexicalWeight(normalizedTerm.length());
            }
        }
        return score;
    }

    /**
     * 通过 ES 索引搜索词法得分。
     * 委托给 KnowledgeRouteIndexService 的 search 方法。
     */
    private List<KnowledgeRouteIndexService.RouteLexicalHit> searchLexicalScores(String routingText, String entityType, int size) {
        KnowledgeRouteIndexService routeIndexService = knowledgeRouteIndexServiceProvider.getIfAvailable();
        if (routeIndexService == null) {
            return List.of();
        }
        return routeIndexService.search(routingText, entityType, size);
    }

    /**
     * 对文本进行分词。
     * 按标点、空白和连接词分割，保留至少2字符的 token。
     * 对中文片段额外生成 n-gram（2-6字）。
     */
    private List<String> tokenize(String text) {
        String normalized = StrUtil.blankToDefault(text, "").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String segment : normalized.split("[\\s、，,；;：:（）()\\-的和及与或]+")) {
            String trimmed = segment.trim();
            if (trimmed.length() >= 2) {
                terms.add(trimmed);
                expandChineseNgrams(terms, trimmed);
            }
        }
        return new ArrayList<>(terms).stream().limit(40).toList();
    }

    /** 解析 JSON 数组字符串（简单实现） */
    private List<String> parseJsonArray(String raw) {
        String normalized = StrUtil.blankToDefault(raw, "").trim();
        if (normalized.isBlank() || "[]".equals(normalized)) {
            return List.of();
        }
        String body = normalized.replace("[", "").replace("]", "");
        if (body.isBlank()) {
            return List.of();
        }
        return List.of(body.split(",")).stream()
            .map(item -> item.replace("\"", "").trim())
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    /** 将 double 转为 BigDecimal（保留4位小数） */
    private BigDecimal scoreToBigDecimal(double score) {
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 构建路由理由。
     * 优先展示命中了哪些关键词，否则展示语义得分信息。
     */
    private String buildReason(List<String> queryTerms, String content, double semanticScore) {
        List<String> matched = queryTerms.stream()
            .filter(term -> normalize(content).contains(normalize(term)))
            .limit(3)
            .toList();
        if (!matched.isEmpty()) {
            return "命中关键词：" + String.join("、", matched);
        }
        if (semanticScore >= 0.55D) {
            return "语义相似度高，基于文档画像与元数据召回";
        }
        if (semanticScore >= 0.35D) {
            return "语义相近，采用保守扩范围召回";
        }
        return "基于文档画像与元数据综合召回";
    }

    /** 将多个字符串用空格拼接（过滤空值） */
    private String join(String... values) {
        return Arrays.stream(values)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.joining(" "));
    }

    /**
     * 文本归一化：去除空白、标点和特殊字符，转小写。
     * 用于模糊匹配时消除格式差异。
     */
    private String normalize(String value) {
        return StrUtil.blankToDefault(value, "")
            .replaceAll("[\\s>`*#_\\-，,。；;：:（）()“”\"'\\[\\]]+", "")
            .toLowerCase(Locale.ROOT);
    }

    /** 返回第一个非空的字符串，否则返回 fallback */
    private String firstNonBlank(String primary, String fallback) {
        if (StrUtil.isNotBlank(primary)) {
            return primary.trim();
        }
        return StrUtil.blankToDefault(fallback, "");
    }

    /** 将文本转为编码格式（非字母数字字符替换为下划线） */
    private String normalizeCode(String value) {
        return normalize(value).replaceAll("[^a-z0-9]+", "_");
    }

    /**
     * 将单个文本转为 embedding 向量。
     * 如果 EmbeddingModel 不可用或调用失败，返回 null（退回词面匹配）。
     */
    private float[] embedSingle(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            return null;
        }
        try {
            return embeddingModel.embed(text.trim());
        }
        catch (Exception exception) {
            log.warn("知识路由生成问题向量失败，退回词面匹配: text='{}'", StrUtil.maxLength(text, 120), exception);
            return null;
        }
    }

    /**
     * 批量计算候选文本的语义得分。
     * 分批调用 EmbeddingModel（每批最多 ROUTE_EMBEDDING_BATCH_SIZE 个），
     * 计算每个候选与查询向量的余弦相似度。
     */
    private List<Double> computeSemanticScores(RouteQueryContext queryContext, List<String> routeTexts) {
        if (!queryContext.semanticEnabled() || routeTexts == null || routeTexts.isEmpty()) {
            return routeTexts == null ? List.of() : routeTexts.stream().map(item -> 0D).toList();
        }
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            return routeTexts.stream().map(item -> 0D).toList();
        }
        try {
            List<String> normalizedRouteTexts = routeTexts.stream()
                .map(item -> StrUtil.blankToDefault(item, ""))
                .toList();
            List<Double> scores = new ArrayList<>(normalizedRouteTexts.size());
            for (int index = 0; index < normalizedRouteTexts.size(); index++) {
                scores.add(0D);
            }
            // 分批计算 embedding
            int totalBatchCount = (normalizedRouteTexts.size() + ROUTE_EMBEDDING_BATCH_SIZE - 1) / ROUTE_EMBEDDING_BATCH_SIZE;
            for (int startIndex = 0; startIndex < normalizedRouteTexts.size(); startIndex += ROUTE_EMBEDDING_BATCH_SIZE) {
                int endIndex = Math.min(startIndex + ROUTE_EMBEDDING_BATCH_SIZE, normalizedRouteTexts.size());
                List<String> currentBatch = normalizedRouteTexts.subList(startIndex, endIndex);
                int currentBatchIndex = (startIndex / ROUTE_EMBEDDING_BATCH_SIZE) + 1;
                log.debug("知识路由候选向量分批计算: batchIndex={}/{}, candidateRange=[{}, {}], batchSize={}",
                    currentBatchIndex, totalBatchCount, startIndex + 1, endIndex, currentBatch.size());
                List<float[]> embeddings = embeddingModel.embed(currentBatch);
                if (embeddings == null || embeddings.size() != currentBatch.size()) {
                    return routeTexts.stream().map(item -> 0D).toList();
                }
                for (int batchIndex = 0; batchIndex < embeddings.size(); batchIndex++) {
                    scores.set(startIndex + batchIndex, cosineSimilarity(queryContext.queryEmbedding(), embeddings.get(batchIndex)));
                }
            }
            return scores;
        }
        catch (Exception exception) {
            log.warn("知识路由生成候选向量失败，退回词面匹配: candidateCount={}", routeTexts.size(), exception);
            return routeTexts.stream().map(item -> 0D).toList();
        }
    }

    /** 计算单个文本与查询的语义相似度 */
    private double semanticScore(RouteQueryContext queryContext, String routeText) {
        if (!queryContext.semanticEnabled()) {
            return 0D;
        }
        float[] routeEmbedding = embedSingle(routeText);
        if (routeEmbedding == null) {
            return 0D;
        }
        return cosineSimilarity(queryContext.queryEmbedding(), routeEmbedding);
    }

    /**
     * 将原始语义相似度转为语义主得分。
     * 阈值 0.20 以下得 0 分，以上按 (similarity - 0.20) * 50 计算。
     * 例如：similarity = 0.60 -> score = (0.60 - 0.20) * 50 = 20 分
     */
    private double semanticMainScore(double semanticScore) {
        if (semanticScore <= 0.20D) {
            return 0D;
        }
        return (semanticScore - 0.20D) * 50D;
    }

    /**
     * 将 ES 词法得分转为辅助得分。
     * 最多贡献 10 分，按 1.6 倍缩放。
     */
    private double lexicalAssist(Double lexicalScore) {
        if (lexicalScore == null || lexicalScore <= 0D) {
            return 0D;
        }
        return Math.min(10D, lexicalScore * 1.6D);
    }

    /**
     * 关键词实体辅助得分。
     * 如果查询中的关键词看起来像实体（包含字母/数字或长度 <= 4），
     * 且在路由文本中命中，每个 +6 分。
     */
    private double keywordEntityAssist(List<String> queryTerms, String routeText) {
        if (queryTerms == null || queryTerms.isEmpty()) {
            return 0D;
        }
        double score = 0D;
        String normalizedContent = normalize(routeText);
        for (String term : queryTerms) {
            if (!looksLikeEntityTerm(term)) {
                continue;
            }
            String normalizedTerm = normalize(term);
            if (StrUtil.isBlank(normalizedTerm) || normalizedTerm.length() < 2) {
                continue;
            }
            if (normalizedContent.contains(normalizedTerm)) {
                score += 6D;
            }
        }
        return score;
    }

    /**
     * 判断关键词是否看起来像实体。
     * 包含字母/数字或长度 <= 4 的关键词更可能是实体名称。
     */
    private boolean looksLikeEntityTerm(String term) {
        if (StrUtil.isBlank(term)) {
            return false;
        }
        String trimmed = term.trim();
        return trimmed.matches(".*[A-Za-z].*")
            || trimmed.matches(".*\\d.*")
            || trimmed.length() <= 4;
    }

    /**
     * 计算两个向量的余弦相似度。
     * cosine(a, b) = (a . b) / (|a| * |b|)
     * 值域 [-1, 1]，1 表示完全相同方向。
     */
    private double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm <= 0D || rightNorm <= 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * 词法匹配权重（按关键词长度）。
     * 长关键词匹配权重更高：>= 8 字符 -> 12 分，>= 5 -> 8 分，>= 3 -> 4 分，其他 -> 2 分。
     */
    private double lexicalWeight(int termLength) {
        if (termLength >= 8) {
            return 12D;
        }
        if (termLength >= 5) {
            return 8D;
        }
        if (termLength >= 3) {
            return 4D;
        }
        return 2D;
    }

    /**
     * 为中文片段生成 n-gram（2-6字）。
     * 例如："用户管理" -> ["用户", "管理", "用户管", "户管理", "用户管理"]
     */
    private void expandChineseNgrams(Set<String> terms, String segment) {
        String normalized = segment.trim();
        if (normalized.length() < 4) {
            return;
        }
        int maxGramLength = Math.min(6, normalized.length());
        for (int gramLength = 2; gramLength <= maxGramLength; gramLength++) {
            for (int start = 0; start + gramLength <= normalized.length(); start++) {
                String gram = normalized.substring(start, start + gramLength);
                if (gram.length() >= 2) {
                    terms.add(gram);
                }
            }
        }
    }

    /**
     * 解析路由决策理由。
     * 根据置信度级别返回不同的描述。
     */
    private String resolveDecisionReason(List<DocumentRouteCandidate> documentCandidates, BigDecimal confidence) {
        if (documentCandidates == null || documentCandidates.isEmpty()) {
            return "没有找到可用候选文档";
        }
        String topReason = StrUtil.blankToDefault(documentCandidates.get(0).getReason(), "");
        if (confidence == null) {
            return topReason;
        }
        if (confidence.compareTo(BigDecimal.valueOf(0.55D)) < 0) {
            return StrUtil.blankToDefault(topReason, "低置信度，已进入保守扩范围候选");
        }
        return topReason;
    }

    /** 将 Scope 候选列表序列化为 JSON 字符串（用于 trace 记录） */
    private String writeScopeJson(List<ScopeRouteCandidate> candidates) {
        return candidates == null || candidates.isEmpty() ? "[]" : candidates.stream()
            .map(item -> "{\"scopeCode\":\"" + item.getScopeCode() + "\",\"scopeName\":\"" + item.getScopeName() + "\",\"score\":\"" + item.getScore() + "\",\"reason\":\"" + escapeJson(item.getReason()) + "\"}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    /** 将 Topic 候选列表序列化为 JSON 字符串（用于 trace 记录） */
    private String writeTopicJson(List<TopicRouteCandidate> candidates) {
        return candidates == null || candidates.isEmpty() ? "[]" : candidates.stream()
            .map(item -> "{\"topicCode\":\"" + item.getTopicCode() + "\",\"topicName\":\"" + item.getTopicName() + "\",\"scopeCode\":\"" + item.getScopeCode() + "\",\"score\":\"" + item.getScore() + "\",\"reason\":\"" + escapeJson(item.getReason()) + "\"}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    /** 将 Document 候选列表序列化为 JSON 字符串（用于 trace 记录） */
    private String writeDocumentJson(List<DocumentRouteCandidate> candidates) {
        return candidates == null || candidates.isEmpty() ? "[]" : candidates.stream()
            .map(item -> "{\"documentId\":\"" + item.getDocumentId() + "\",\"documentName\":\"" + escapeJson(item.getDocumentName()) + "\",\"lastIndexTaskId\":\"" + item.getLastIndexTaskId() + "\",\"score\":\"" + item.getScore() + "\",\"reason\":\"" + escapeJson(item.getReason()) + "\"}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    /** JSON 字符串转义（双引号） */
    private String escapeJson(String text) {
        return StrUtil.blankToDefault(text, "").replace("\"", "\\\"");
    }

    /** Scope 累加器（用于从文档推导 Scope 时的得分聚合） */
    private static final class ScopeAccumulator {
        private final String scopeCode;
        private final String scopeName;
        private double maxScore;
        private String reason = "";

        private ScopeAccumulator(String scopeCode, String scopeName) {
            this.scopeCode = scopeCode;
            this.scopeName = scopeName;
        }
    }

    /** Topic 累加器（用于从画像推导 Topic 时的得分聚合） */
    private static final class TopicAccumulator {
        private final String topicName;
        private final String scopeCode;
        private double maxScore;
        private String reason = "";

        private TopicAccumulator(String topicName, String scopeCode) {
            this.topicName = topicName;
            this.scopeCode = scopeCode;
        }
    }

    /**
     * 路由查询上下文记录。
     * 封装了原始问题、改写问题、路由文本、关键词列表和查询向量。
     */
    private record RouteQueryContext(String originalQuestion,
                                     String rewriteQuestion,
                                     String routingText,
                                     List<String> queryTerms,
                                     float[] queryEmbedding) {
        /** 判断语义检索是否可用（有有效的查询向量） */
        private boolean semanticEnabled() {
            return queryEmbedding != null && queryEmbedding.length > 0;
        }
    }

    /** 文档路由素材记录（文档ID + 拼接的路由文本） */
    private record DocumentRouteMaterial(Long documentId, String routeText) {
    }
}
