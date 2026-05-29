package ai.knowhub.chat.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.data.KnowHubChatMemorySummary;
import ai.knowhub.chat.mapper.KnowHubChatMemorySummaryMapper;
import ai.knowhub.chat.model.ConversationExchangeVo;
import ai.knowhub.chat.model.ConversationMemorySummaryVo;
import ai.knowhub.chat.model.memory.ConversationMemoryContext;
import ai.knowhub.chat.model.memory.ConversationSummaryPayload;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.prompt.PromptTemplateNames;
import ai.knowhub.prompt.PromptTemplateService;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.enums.ChatTurnStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【持久化会话记忆服务】
 *
 * ConversationMemoryService 接口的持久化实现类，
 * 使用 MySQL 数据库存储会话的长期摘要（Long-term Summary）。
 *
 * 核心功能 - 摘要压缩策略：
 * 当多轮对话积累的历史消息超过大模型的上下文窗口时，系统会自动将较早的
 * 对话压缩成结构化摘要。压缩策略如下：
 *   1. 保留最近 N 轮对话原文（N = keepRecentTurns）
 *   2. 将 N 轮之前的对话分批压缩（每批 compressionBatchTurns 轮）
 *   3. 使用大模型生成结构化摘要（包含目标、事实、偏好、待跟进问题等）
 *   4. 摘要以 JSON 格式存储在数据库中
 *
 * 摘要结构（ConversationSummaryPayload）：
 * - summary: 长期摘要文本
 * - conversationGoal: 会话目标
 * - stableFacts: 已确认的事实列表
 * - userPreferences: 用户偏好和约束
 * - resolvedPoints: 已解决的问题
 * - pendingQuestions: 待跟进的问题
 * - retrievalHints: 检索提示关键词
 *
 * 增量压缩机制：
 * 使用 coveredExchangeId 记录已压缩到哪一轮，下次只压缩新增的轮次，
 * 避免重复压缩。
 *
 * 异步刷新：
 * refreshConversationSummaryAsync() 使用 CompletableFuture 在独立线程池中执行，
 * 不阻塞主流程。使用 ConcurrentHashMap 做去重，避免同一会话并发刷新。
 *
 * 回退机制：
 * 如果大模型调用失败，fallbackMerge() 使用规则化的方式生成摘要（取问题和回答的前 N 个字符）。
 *
 * 数据库表：knowhub_chat_memory_summary
 */
@Slf4j
@Service
public class PersistentConversationMemoryService implements ConversationMemoryService {

    // ==================== 正则表达式常量 ====================

    /** 用于从模型输出中提取 JSON 对象的正则 */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    /** 用于从问题中提取检索提示关键词的正则（英文单词或中文词组） */
    private static final Pattern RETRIEVAL_HINT_PATTERN = Pattern.compile("[a-zA-Z0-9._-]{2,}|[\\p{IsHan}]{2,12}");

    // ==================== 文本截断长度常量 ====================

    /** 每个摘要分区的最大条目数 */
    private static final int MAX_SECTION_ITEMS = 6;

    /** 单个条目的最大长度 */
    private static final int MAX_ITEM_LENGTH = 80;

    /** 会话目标的最大长度 */
    private static final int MAX_GOAL_LENGTH = 120;

    /** 问题的最大长度（用于压缩转录） */
    private static final int MAX_QUESTION_LENGTH = 160;

    /** 回答的最大长度（用于压缩转录） */
    private static final int MAX_ANSWER_LENGTH = 320;

    /** 回答上下文中回答的最大长度 */
    private static final int MAX_ANSWER_CONTEXT_ANSWER_LENGTH = 220;

    // ==================== 依赖注入 ====================

    /** 会话归档存储，用于查询历史回合数据 */
    private final ConversationArchiveStore conversationArchiveStore;

    /** 会话摘要表 Mapper */
    private final KnowHubChatMemorySummaryMapper summaryMapper;

    /** Jackson ObjectMapper */
    private final ObjectMapper objectMapper;

    /** RAG 配置属性 */
    private final ChatRagProperties properties;

    /** 摘要刷新专用线程池 */
    private final ExecutorService chatMemorySummaryExecutorService;

    /** 可观测的聊天模型服务 */
    private final ObservedChatModelService observedChatModelService;

    /** 提示词模板服务 */
    private final PromptTemplateService promptTemplateService;

    /**
     * 正在刷新中的会话 ID 集合（线程安全）。
     * 用于防止同一会话并发刷新摘要。
     */
    private final Set<String> refreshingConversationIds = ConcurrentHashMap.newKeySet();

    /** 百度 UID 生成器 */
    @Resource
    private UidGenerator uidGenerator;

    /**
     * 构造函数。
     */
    public PersistentConversationMemoryService(ConversationArchiveStore conversationArchiveStore,
                                               KnowHubChatMemorySummaryMapper summaryMapper,
                                               ObjectMapper objectMapper,
                                               ChatRagProperties properties,
                                               @Qualifier("chatMemorySummaryExecutorService") ExecutorService chatMemorySummaryExecutorService,
                                               ObservedChatModelService observedChatModelService,
                                               PromptTemplateService promptTemplateService) {
        this.conversationArchiveStore = conversationArchiveStore;
        this.summaryMapper = summaryMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.chatMemorySummaryExecutorService = chatMemorySummaryExecutorService;
        this.observedChatModelService = observedChatModelService;
        this.promptTemplateService = promptTemplateService;
    }

    // ==================== 接口方法实现 ====================

    @Override
    public ConversationMemoryContext loadMemoryContext(String conversationId) {
        return loadMemoryContext(conversationId, null);
    }

    /**
     * 加载会话记忆上下文。
     *
     * 两种模式：
     * 1. 摘要压缩未启用：只返回最近 N 轮的原文转录
     * 2. 摘要压缩已启用：返回长期摘要 + 最近 N 轮原文的组合
     */
    @Override
    public ConversationMemoryContext loadMemoryContext(String conversationId, ConversationTraceRecorder traceRecorder) {
        if (StrUtil.isBlank(conversationId)) {
            return emptyContext();
        }

        ChatRagProperties.HistorySummaryProperties historySummaryProperties = properties.getHistorySummary();
        if (!historySummaryProperties.isEnabled()) {

            // 摘要压缩未启用，只返回最近对话原文
            String recentTranscript = renderRecentTranscript(
                conversationArchiveStore.listRecentExchanges(conversationId, Math.max(1, properties.getRewriteHistoryTurns() * 3)),
                Math.max(1, properties.getRewriteHistoryTurns()),
                historySummaryProperties.getRecentTranscriptMaxChars()
            );
            String answerRecentTranscript = renderAnswerRecentTranscript(
                conversationArchiveStore.listRecentExchanges(conversationId, Math.max(1, properties.getRewriteHistoryTurns() * 3)),
                Math.max(1, properties.getRewriteHistoryTurns()),
                Math.max(1, properties.getAnswerHistoryMaxChars())
            );
            return ConversationMemoryContext.builder()
                .assembledHistory(recentTranscript)
                .longTermSummary("")
                .recentTranscript(recentTranscript)
                .answerRecentTranscript(answerRecentTranscript)
                .summaryPayload(ConversationSummaryPayload.builder().build())
                .coveredExchangeId(0L)
                .coveredExchangeCount(0)
                .compressionCount(0)
                .compressionApplied(false)
                .build();
        }

        // 摘要压缩已启用：检查是否需要增量压缩
        KnowHubChatMemorySummary summaryState = refreshSummaryIfNecessary(
            conversationId,
            findSummary(conversationId).orElse(null),
            traceRecorder
        );
        ConversationSummaryPayload summaryPayload = readSummaryPayload(summaryState);

        // 加载最近 N 轮对话原文
        List<ConversationExchangeVo> recentExchanges = conversationArchiveStore.listRecentExchanges(
            conversationId,
            recentFetchLimit(historySummaryProperties.getKeepRecentTurns())
        );
        String recentTranscript = renderRecentTranscript(
            recentExchanges,
            historySummaryProperties.getKeepRecentTurns(),
            historySummaryProperties.getRecentTranscriptMaxChars()
        );
        String answerRecentTranscript = renderAnswerRecentTranscript(
            recentExchanges,
            historySummaryProperties.getKeepRecentTurns(),
            Math.max(1, properties.getAnswerHistoryMaxChars())
        );
        String longTermSummary = summaryState == null ? "" : safeText(summaryState.getSummaryText());

        return ConversationMemoryContext.builder()

            .assembledHistory(assembleHistory(longTermSummary, recentTranscript))
            .longTermSummary(longTermSummary)
            .recentTranscript(recentTranscript)
            .answerRecentTranscript(answerRecentTranscript)
            .summaryPayload(summaryPayload)
            .coveredExchangeId(summaryState == null ? 0L : defaultLong(summaryState.getCoveredExchangeId()))
            .coveredExchangeCount(summaryState == null ? 0 : safeInt(summaryState.getCoveredExchangeCount()))
            .compressionCount(summaryState == null ? 0 : safeInt(summaryState.getCompressionCount()))
            .compressionApplied(StrUtil.isNotBlank(longTermSummary))
            .build();
    }

    /**
     * 异步刷新会话摘要。
     *
     * 使用 ConcurrentHashMap 做去重：如果该会话正在刷新中，直接跳过。
     */
    @Override
    public void refreshConversationSummaryAsync(String conversationId) {
        if (StrUtil.isBlank(conversationId) || !properties.getHistorySummary().isEnabled()) {
            return;
        }

        if (!refreshingConversationIds.add(conversationId)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                refreshSummaryIfNecessary(conversationId, findSummary(conversationId).orElse(null), null);
            }
            catch (Exception exception) {
                log.warn("异步预热会话摘要失败, conversationId={}", conversationId, exception);
            }
            finally {
                refreshingConversationIds.remove(conversationId);
            }
        }, chatMemorySummaryExecutorService);
    }

    @Override
    public ConversationMemorySummaryVo getConversationSummary(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return emptySummaryVo("");
        }
        return toSummaryVo(conversationId, findSummary(conversationId).orElse(null));
    }

    /**
     * 强制重建会话摘要。
     *
     * 删除现有摘要后重新压缩，用于手动修复或管理员操作。
     */
    @Override
    public ConversationMemorySummaryVo rebuildConversationSummary(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return emptySummaryVo("");
        }

        if (!refreshingConversationIds.add(conversationId)) {
            return getConversationSummary(conversationId);
        }
        try {
            summaryMapper.delete(new LambdaQueryWrapper<KnowHubChatMemorySummary>()
                .eq(KnowHubChatMemorySummary::getConversationId, conversationId));
            KnowHubChatMemorySummary rebuiltState = refreshSummaryIfNecessary(conversationId, null, null);
            return toSummaryVo(conversationId, rebuiltState);
        }
        finally {
            refreshingConversationIds.remove(conversationId);
        }
    }

    @Override
    public void deleteConversationSummary(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return;
        }
        summaryMapper.delete(new LambdaQueryWrapper<KnowHubChatMemorySummary>()
            .eq(KnowHubChatMemorySummary::getConversationId, conversationId));
    }

    // ==================== 核心压缩逻辑 ====================

    /**
     * 如果有必要，增量压缩会话摘要。
     *
     * 逻辑：
     * 1. 查询 coveredExchangeId 之后的所有新增回合
     * 2. 过滤出稳定回合（已完成且有问题的回合）
     * 3. 计算溢出数量 = 稳定回合数 - keepRecentTurns
     * 4. 如果有溢出，分批压缩溢出的回合
     */
    private KnowHubChatMemorySummary refreshSummaryIfNecessary(String conversationId,
                                                                  KnowHubChatMemorySummary currentState,
                                                                  ConversationTraceRecorder traceRecorder) {
        ChatRagProperties.HistorySummaryProperties historySummaryProperties = properties.getHistorySummary();
        long coveredExchangeId = currentState == null ? 0L : defaultLong(currentState.getCoveredExchangeId());

        // 查询新增的回合
        List<ConversationExchangeVo> incrementalExchanges = conversationArchiveStore.listExchangesAfter(
            conversationId,
            coveredExchangeId
        );
        List<ConversationExchangeVo> stableExchanges = incrementalExchanges.stream()
            .filter(this::isStableSummaryExchange)
            .toList();

        // 计算溢出数量
        int overflowCount = Math.max(0, stableExchanges.size() - historySummaryProperties.getKeepRecentTurns());
        if (overflowCount <= 0) {
            return currentState;
        }

        // 分批压缩溢出的回合
        List<ConversationExchangeVo> overflowExchanges = stableExchanges.subList(0, overflowCount);
        KnowHubChatMemorySummary workingState = currentState;

        for (int start = 0; start < overflowExchanges.size(); start += historySummaryProperties.getCompressionBatchTurns()) {
            int end = Math.min(start + historySummaryProperties.getCompressionBatchTurns(), overflowExchanges.size());
            List<ConversationExchangeVo> batch = overflowExchanges.subList(start, end);
            ConversationSummaryPayload mergedPayload = mergeSummaryPayload(readSummaryPayload(workingState), batch, traceRecorder);
            ConversationExchangeVo lastExchange = batch.get(batch.size() - 1);
            workingState = saveSummarySnapshot(
                conversationId,
                workingState,
                mergedPayload,
                lastExchange.getExchangeId(),
                safeInt(workingState == null ? null : workingState.getCoveredExchangeCount()) + batch.size(),
                resolveSourceTime(lastExchange)
            );
        }
        return workingState;
    }

    /**
     * 合并摘要：使用大模型将现有摘要和新对话批次合并为新的摘要。
     *
     * 如果大模型调用失败，回退到规则化合并（fallbackMerge）。
     */
    private ConversationSummaryPayload mergeSummaryPayload(ConversationSummaryPayload existingPayload,
                                                           List<ConversationExchangeVo> batch,
                                                           ConversationTraceRecorder traceRecorder) {
        try {
            String content = observedChatModelService.callText(
                "summary",
                promptTemplateService.render(PromptTemplateNames.CONVERSATION_SUMMARY_SYSTEM, Map.of()),
                buildSummaryMergePrompt(existingPayload, batch),
                traceRecorder
            );
            ConversationSummaryPayload parsedPayload = parseSummaryPayload(content);
            if (parsedPayload != null) {
                return normalizePayload(parsedPayload);
            }
        }
        catch (Exception exception) {
            log.warn("合并会话长期摘要失败，回退到规则压缩: {}", exception.getMessage());
        }
        return fallbackMerge(existingPayload, batch);
    }

    /** 构建摘要合并的提示词 */
    private String buildSummaryMergePrompt(ConversationSummaryPayload existingPayload,
                                           List<ConversationExchangeVo> batch) {
        String existingJson = writePayloadJson(normalizePayload(copyPayload(existingPayload)));
        return promptTemplateService.render(PromptTemplateNames.CONVERSATION_SUMMARY_MERGE, Map.of(
            "existingSummaryJson", StrUtil.isNotBlank(existingJson) ? existingJson : "{}",
            "newConversationBatch", renderCompressionTranscript(batch)
        ));
    }

    /**
     * 回退合并：当大模型调用失败时，使用规则化方式生成摘要。
     *
     * 策略：取问题和回答的前 N 个字符拼接，作为临时摘要。
     */
    private ConversationSummaryPayload fallbackMerge(ConversationSummaryPayload existingPayload,
                                                     List<ConversationExchangeVo> batch) {
        ConversationSummaryPayload mergedPayload = copyPayload(existingPayload);
        String batchHighlight = renderFallbackBatchHighlight(batch);
        String mergedSummary = joinNonBlank(mergedPayload.getSummary(), batchHighlight, "；");
        mergedPayload.setSummary(clipText(mergedSummary, properties.getHistorySummary().getSummaryMaxChars()));

        ConversationExchangeVo lastExchange = batch.get(batch.size() - 1);
        if (StrUtil.isBlank(mergedPayload.getConversationGoal()) && StrUtil.isNotBlank(lastExchange.getQuestion())) {
            mergedPayload.setConversationGoal(clipText(lastExchange.getQuestion(), MAX_GOAL_LENGTH));
        }

        List<String> pendingQuestions = new ArrayList<>(safeList(mergedPayload.getPendingQuestions()));
        for (ConversationExchangeVo exchange : batch) {
            if (StrUtil.isNotBlank(exchange.getQuestion())) {
                pendingQuestions.add(clipText(exchange.getQuestion(), MAX_ITEM_LENGTH));
            }
        }
        mergedPayload.setPendingQuestions(deduplicateAndLimit(pendingQuestions));

        List<String> retrievalHints = new ArrayList<>(safeList(mergedPayload.getRetrievalHints()));
        if (StrUtil.isNotBlank(lastExchange.getQuestion())) {
            retrievalHints.addAll(extractRetrievalHints(lastExchange.getQuestion()));
        }
        mergedPayload.setRetrievalHints(deduplicateAndLimit(retrievalHints));
        return normalizePayload(mergedPayload);
    }

    /**
     * 保存摘要快照到数据库。
     *
     * 使用乐观锁策略：先查询最新状态，如果 coveredExchangeId 已经比当前大则跳过。
     */
    private KnowHubChatMemorySummary saveSummarySnapshot(String conversationId,
                                                            KnowHubChatMemorySummary currentState,
                                                            ConversationSummaryPayload payload,
                                                            long coveredExchangeId,
                                                            int coveredExchangeCount,
                                                            Date lastSourceEditTime) {
        KnowHubChatMemorySummary latestState = findSummary(conversationId).orElse(null);
        long latestCoveredExchangeId = latestState == null ? 0L : defaultLong(latestState.getCoveredExchangeId());

        // 如果数据库中的 coveredExchangeId 已经比当前大，说明有其他线程已经更新了
        if (latestCoveredExchangeId > coveredExchangeId) {
            return latestState;
        }

        if (latestState != null
            && latestCoveredExchangeId == coveredExchangeId
            && StrUtil.isNotBlank(latestState.getSummaryText())) {
            return latestState;
        }

        String summaryText = buildLongTermSummaryText(payload);
        String summaryJson = writePayloadJson(payload);

        // 首次创建摘要记录
        if (latestState == null) {
            KnowHubChatMemorySummary newState = new KnowHubChatMemorySummary();
            newState.setId(uidGenerator.getUid());
            newState.setConversationId(conversationId);
            newState.setCoveredExchangeId(coveredExchangeId);
            newState.setCoveredExchangeCount(Math.max(coveredExchangeCount, 0));
            newState.setCompressionCount(1);
            newState.setSummaryVersion(1);
            newState.setSummaryText(summaryText);
            newState.setSummaryJson(summaryJson);
            newState.setLastSourceEditTime(lastSourceEditTime);
            newState.setStatus(BusinessStatus.YES.getCode());
            summaryMapper.insert(newState);
            return newState;
        }

        // 更新现有摘要记录
        KnowHubChatMemorySummary updateState = new KnowHubChatMemorySummary();
        updateState.setId(latestState.getId());
        updateState.setCoveredExchangeId(coveredExchangeId);
        updateState.setCoveredExchangeCount(Math.max(coveredExchangeCount, safeInt(latestState.getCoveredExchangeCount())));
        updateState.setCompressionCount(safeInt(latestState.getCompressionCount()) + 1);
        updateState.setSummaryVersion(safeInt(latestState.getSummaryVersion()) + 1);
        updateState.setSummaryText(summaryText);
        updateState.setSummaryJson(summaryJson);
        updateState.setLastSourceEditTime(lastSourceEditTime);
        summaryMapper.updateById(updateState);

        // 同步更新内存中的状态
        latestState.setCoveredExchangeId(updateState.getCoveredExchangeId());
        latestState.setCoveredExchangeCount(updateState.getCoveredExchangeCount());
        latestState.setCompressionCount(updateState.getCompressionCount());
        latestState.setSummaryVersion(updateState.getSummaryVersion());
        latestState.setSummaryText(updateState.getSummaryText());
        latestState.setSummaryJson(updateState.getSummaryJson());
        latestState.setLastSourceEditTime(updateState.getLastSourceEditTime());
        return latestState;
    }

    // ==================== 数据查询方法 ====================

    /** 查询指定会话的最新摘要记录 */
    private Optional<KnowHubChatMemorySummary> findSummary(String conversationId) {
        return Optional.ofNullable(summaryMapper.selectOne(
            new LambdaQueryWrapper<KnowHubChatMemorySummary>()
                .eq(KnowHubChatMemorySummary::getConversationId, conversationId)
                .orderByDesc(KnowHubChatMemorySummary::getId)
                .last("LIMIT 1")
        ));
    }

    /** 从摘要记录中读取结构化摘要载荷 */
    private ConversationSummaryPayload readSummaryPayload(KnowHubChatMemorySummary summaryState) {
        if (summaryState == null) {
            return ConversationSummaryPayload.builder().build();
        }
        if (StrUtil.isNotBlank(summaryState.getSummaryJson())) {
            ConversationSummaryPayload payload = parseSummaryPayload(summaryState.getSummaryJson());
            if (payload != null) {
                return normalizePayload(payload);
            }
        }
        ConversationSummaryPayload fallbackPayload = ConversationSummaryPayload.builder()
            .summary(summaryState.getSummaryText())
            .build();
        return normalizePayload(fallbackPayload);
    }

    /** 解析 JSON 字符串为 ConversationSummaryPayload */
    private ConversationSummaryPayload parseSummaryPayload(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            ConversationSummaryPayload payload = ConversationSummaryPayload.builder()
                .summary(root.path("summary").asText(""))
                .conversationGoal(root.path("conversation_goal").asText(""))
                .stableFacts(readStringArray(root.path("stable_facts")))
                .userPreferences(readStringArray(root.path("user_preferences")))
                .resolvedPoints(readStringArray(root.path("resolved_points")))
                .pendingQuestions(readStringArray(root.path("pending_questions")))
                .retrievalHints(readStringArray(root.path("retrieval_hints")))
                .build();
            return normalizePayload(payload);
        }
        catch (Exception exception) {
            log.debug("解析会话长期摘要 JSON 失败: {}", raw, exception);
            return null;
        }
    }

    /** 从 JsonNode 数组中读取字符串列表 */
    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        node.forEach(item -> {
            String text = item.asText("").trim();
            if (StrUtil.isNotBlank(text)) {
                result.add(text);
            }
        });
        return result;
    }

    // ==================== 文本渲染方法 ====================

    /** 渲染压缩转录文本（用于大模型压缩） */
    private String renderCompressionTranscript(List<ConversationExchangeVo> batch) {
        StringBuilder builder = new StringBuilder();
        for (ConversationExchangeVo exchange : batch) {
            if (StrUtil.isNotBlank(exchange.getQuestion())) {
                builder.append("用户：")
                    .append(clipText(exchange.getQuestion(), MAX_QUESTION_LENGTH))
                    .append('\n');
            }
            if (StrUtil.isNotBlank(exchange.getAnswer())) {
                builder.append("助手：")
                    .append(clipText(exchange.getAnswer(), MAX_ANSWER_LENGTH))
                    .append('\n');
            }
            if (exchange.getStatus() == ChatTurnStatus.STOPPED && StrUtil.isNotBlank(exchange.getErrorMessage())) {
                builder.append("补充说明：本轮被停止，说明=")
                    .append(clipText(exchange.getErrorMessage(), MAX_ITEM_LENGTH))
                    .append('\n');
            }
        }
        return builder.toString().trim();
    }

    /** 渲染回退批次高亮文本（规则化摘要） */
    private String renderFallbackBatchHighlight(List<ConversationExchangeVo> batch) {
        List<String> highlights = new ArrayList<>();
        for (ConversationExchangeVo exchange : batch) {
            if (StrUtil.isNotBlank(exchange.getQuestion())) {
                highlights.add("用户关注：" + clipText(exchange.getQuestion(), MAX_ITEM_LENGTH));
            }
            if (StrUtil.isNotBlank(exchange.getAnswer())) {
                highlights.add("已有结论：" + clipText(exchange.getAnswer(), MAX_ITEM_LENGTH));
            }
            if (highlights.size() >= 4) {
                break;
            }
        }
        return String.join("；", highlights);
    }

    /** 渲染最近对话原文（用于查询改写和检索） */
    private String renderRecentTranscript(List<ConversationExchangeVo> exchanges,
                                          int keepRecentTurns,
                                          int recentTranscriptMaxChars) {
        List<ConversationExchangeVo> renderableExchanges = new ArrayList<>();
        for (ConversationExchangeVo exchange : exchanges) {
            if (shouldKeepInRecentWindow(exchange)) {
                renderableExchanges.add(exchange);
            }
        }
        if (renderableExchanges.isEmpty()) {
            return "";
        }
        int fromIndex = Math.max(0, renderableExchanges.size() - keepRecentTurns);
        StringBuilder builder = new StringBuilder("【最近对话原文】\n");
        for (int index = fromIndex; index < renderableExchanges.size(); index++) {
            ConversationExchangeVo exchange = renderableExchanges.get(index);
            if (StrUtil.isNotBlank(exchange.getQuestion())) {
                builder.append("用户：")
                    .append(clipText(exchange.getQuestion(), MAX_QUESTION_LENGTH))
                    .append('\n');
            }
            if (exchange.getStatus() == ChatTurnStatus.COMPLETED && StrUtil.isNotBlank(exchange.getAnswer())) {
                builder.append("助手：")
                    .append(clipText(exchange.getAnswer(), MAX_ANSWER_LENGTH))
                    .append('\n');
            }
        }

        return clipRecentTranscript(builder.toString().trim(), recentTranscriptMaxChars);
    }

    /** 渲染用于回答生成的最近对话上下文（只包含问题，不含回答） */
    private String renderAnswerRecentTranscript(List<ConversationExchangeVo> exchanges,
                                                int keepRecentTurns,
                                                int maxChars) {
        if (exchanges == null || exchanges.isEmpty()) {
            return "";
        }
        List<ConversationExchangeVo> renderableExchanges = new ArrayList<>();
        for (ConversationExchangeVo exchange : exchanges) {
            if (exchange != null
                && exchange.getStatus() != ChatTurnStatus.RUNNING
                && StrUtil.isNotBlank(exchange.getQuestion())) {
                renderableExchanges.add(exchange);
            }
        }
        if (renderableExchanges.isEmpty()) {
            return "";
        }
        int fromIndex = Math.max(0, renderableExchanges.size() - keepRecentTurns);
        StringBuilder builder = new StringBuilder("【最近相关对话】\n");
        for (int index = fromIndex; index < renderableExchanges.size(); index++) {
            ConversationExchangeVo exchange = renderableExchanges.get(index);
            if (StrUtil.isNotBlank(exchange.getQuestion())) {
                builder.append("用户：")
                    .append(clipText(exchange.getQuestion(), MAX_QUESTION_LENGTH))
                    .append('\n');
            }

        }
        return clipRecentTranscript(builder.toString().trim(), maxChars);
    }

    /** 构建长期摘要的可读文本（用于存储和展示） */
    private String buildLongTermSummaryText(ConversationSummaryPayload payload) {
        ConversationSummaryPayload normalizedPayload = normalizePayload(payload);
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "长期会话摘要", normalizedPayload.getSummary());
        appendSection(builder, "会话目标", normalizedPayload.getConversationGoal());
        appendBulletSection(builder, "已确认事实", normalizedPayload.getStableFacts());
        appendBulletSection(builder, "用户偏好与约束", normalizedPayload.getUserPreferences());
        appendBulletSection(builder, "已解决问题", normalizedPayload.getResolvedPoints());
        appendBulletSection(builder, "待跟进问题", normalizedPayload.getPendingQuestions());
        appendBulletSection(builder, "检索提示", normalizedPayload.getRetrievalHints());
        return clipText(builder.toString().trim(), properties.getHistorySummary().getSummaryMaxChars());
    }

    /** 组装历史上下文（长期摘要 + 最近对话原文） */
    private String assembleHistory(String longTermSummary, String recentTranscript) {
        return joinNonBlank(longTermSummary, recentTranscript, "\n\n").trim();
    }

    // ==================== 数据规范化方法 ====================

    /** 规范化摘要载荷（截断过长文本、去重、限制条目数） */
    private ConversationSummaryPayload normalizePayload(ConversationSummaryPayload payload) {
        ConversationSummaryPayload workingPayload = payload == null ? ConversationSummaryPayload.builder().build() : payload;
        String normalizedSummary = clipText(safeText(workingPayload.getSummary()), properties.getHistorySummary().getSummaryMaxChars());
        if (StrUtil.isBlank(normalizedSummary)) {
            normalizedSummary = synthesizeSummaryFromSections(workingPayload);
        }
        return ConversationSummaryPayload.builder()
            .summary(normalizedSummary)
            .conversationGoal(clipText(safeText(workingPayload.getConversationGoal()), MAX_GOAL_LENGTH))
            .stableFacts(deduplicateAndLimit(workingPayload.getStableFacts()))
            .userPreferences(deduplicateAndLimit(workingPayload.getUserPreferences()))
            .resolvedPoints(deduplicateAndLimit(workingPayload.getResolvedPoints()))
            .pendingQuestions(deduplicateAndLimit(workingPayload.getPendingQuestions()))
            .retrievalHints(deduplicateAndLimit(workingPayload.getRetrievalHints()))
            .build();
    }

    /** 从各分区合成摘要文本（当 summary 字段为空时使用） */
    private String synthesizeSummaryFromSections(ConversationSummaryPayload payload) {
        List<String> parts = new ArrayList<>();
        if (StrUtil.isNotBlank(payload.getConversationGoal())) {
            parts.add("目标：" + clipText(payload.getConversationGoal(), MAX_ITEM_LENGTH));
        }
        if (!safeList(payload.getStableFacts()).isEmpty()) {
            parts.add("事实：" + String.join("；", safeList(payload.getStableFacts())));
        }
        if (!safeList(payload.getPendingQuestions()).isEmpty()) {
            parts.add("待跟进：" + String.join("；", safeList(payload.getPendingQuestions())));
        }
        return clipText(String.join("；", parts), properties.getHistorySummary().getSummaryMaxChars());
    }

    /** 去重并限制条目数 */
    private List<String> deduplicateAndLimit(List<String> values) {
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String value : safeList(values)) {
            String text = clipText(safeText(value), MAX_ITEM_LENGTH);
            if (StrUtil.isNotBlank(text)) {
                deduplicated.add(text);
            }
            if (deduplicated.size() >= MAX_SECTION_ITEMS) {
                break;
            }
        }
        return new ArrayList<>(deduplicated);
    }

    /** 深拷贝摘要载荷 */
    private ConversationSummaryPayload copyPayload(ConversationSummaryPayload payload) {
        if (payload == null) {
            return ConversationSummaryPayload.builder().build();
        }
        return ConversationSummaryPayload.builder()
            .summary(payload.getSummary())
            .conversationGoal(payload.getConversationGoal())
            .stableFacts(new ArrayList<>(safeList(payload.getStableFacts())))
            .userPreferences(new ArrayList<>(safeList(payload.getUserPreferences())))
            .resolvedPoints(new ArrayList<>(safeList(payload.getResolvedPoints())))
            .pendingQuestions(new ArrayList<>(safeList(payload.getPendingQuestions())))
            .retrievalHints(new ArrayList<>(safeList(payload.getRetrievalHints())))
            .build();
    }

    // ==================== JSON 和文本辅助方法 ====================

    /** 将摘要载荷序列化为 JSON 字符串 */
    private String writePayloadJson(ConversationSummaryPayload payload) {
        try {
            return objectMapper.writeValueAsString(normalizePayload(payload));
        }
        catch (Exception exception) {
            throw new IllegalStateException("序列化会话长期摘要失败", exception);
        }
    }

    /** 从模型输出中提取 JSON 对象字符串 */
    private String extractJsonObject(String raw) {
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group();
        }
        return raw.trim();
    }

    /** 判断回合是否为稳定的摘要候选（已完成且有问题） */
    private boolean isStableSummaryExchange(ConversationExchangeVo exchange) {
        if (exchange == null || exchange.getStatus() == null) {
            return false;
        }

        return exchange.getStatus() == ChatTurnStatus.COMPLETED
            && StrUtil.isNotBlank(exchange.getQuestion());
    }

    /** 判断回合是否应该保留在最近窗口中 */
    private boolean shouldKeepInRecentWindow(ConversationExchangeVo exchange) {
        return exchange != null
            && exchange.getStatus() != ChatTurnStatus.RUNNING
            && (StrUtil.isNotBlank(exchange.getQuestion()) || StrUtil.isNotBlank(exchange.getAnswer()));
    }

    /** 计算最近回合的查询数量限制 */
    private int recentFetchLimit(int keepRecentTurns) {

        return Math.max(keepRecentTurns * 3, keepRecentTurns + 4);
    }

    /** 追加带标题的文本段 */
    private void appendSection(StringBuilder builder, String title, String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append('【').append(title).append("】\n")
            .append(content.trim())
            .append('\n');
    }

    /** 追加带标题的列表段 */
    private void appendBulletSection(StringBuilder builder, String title, List<String> values) {
        List<String> normalizedValues = safeList(values);
        if (normalizedValues.isEmpty()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append('【').append(title).append("】\n");
        for (String value : normalizedValues) {
            builder.append("- ").append(value).append('\n');
        }
    }

    /** 截断文本（从末尾截断，添加省略号） */
    private String clipText(String text, int maxChars) {
        String normalized = safeText(text);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 1)) + "...";
    }

    /** 截断最近转录文本（从开头截断，添加省略号） */
    private String clipRecentTranscript(String text, int maxChars) {
        String normalized = safeText(text);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        int startIndex = Math.max(0, normalized.length() - Math.max(0, maxChars - 1));
        return "..." + normalized.substring(startIndex);
    }

    /** 连接两个非空文本 */
    private String joinNonBlank(String left, String right, String delimiter) {
        if (StrUtil.isBlank(left)) {
            return safeText(right);
        }
        if (StrUtil.isBlank(right)) {
            return safeText(left);
        }
        return safeText(left) + delimiter + safeText(right);
    }

    // ==================== 安全访问辅助方法 ====================

    private List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Date resolveSourceTime(ConversationExchangeVo exchange) {
        if (exchange == null) {
            return null;
        }
        return exchange.getEditTime() != null ? exchange.getEditTime() : exchange.getCreateTime();
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    // ==================== 展示转换方法 ====================

    private ConversationMemoryContext emptyContext() {
        return ConversationMemoryContext.builder()
            .assembledHistory("")
            .longTermSummary("")
            .recentTranscript("")
            .answerRecentTranscript("")
            .summaryPayload(ConversationSummaryPayload.builder().build())
            .coveredExchangeId(0L)
            .coveredExchangeCount(0)
            .compressionCount(0)
            .compressionApplied(false)
            .build();
    }

    private ConversationMemorySummaryVo toSummaryVo(String conversationId, KnowHubChatMemorySummary summaryState) {
        if (summaryState == null) {
            return emptySummaryVo(conversationId);
        }
        return new ConversationMemorySummaryVo(
            conversationId,
            StrUtil.isNotBlank(summaryState.getSummaryText()),
            defaultLong(summaryState.getCoveredExchangeId()),
            safeInt(summaryState.getCoveredExchangeCount()),
            safeInt(summaryState.getCompressionCount()),
            safeInt(summaryState.getSummaryVersion()),
            safeText(summaryState.getSummaryText()),
            readSummaryPayload(summaryState),
            toInstant(summaryState.getLastSourceEditTime()),
            toInstant(summaryState.getEditTime())
        );
    }

    private ConversationMemorySummaryVo emptySummaryVo(String conversationId) {
        return new ConversationMemorySummaryVo(
            conversationId,
            false,
            0L,
            0,
            0,
            0,
            "",
            ConversationSummaryPayload.builder().build(),
            null,
            null
        );
    }

    /** 从问题中提取检索提示关键词 */
    private List<String> extractRetrievalHints(String question) {
        if (StrUtil.isBlank(question)) {
            return List.of();
        }
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        Matcher matcher = RETRIEVAL_HINT_PATTERN.matcher(question);
        while (matcher.find()) {
            String hint = matcher.group().trim();
            if (hint.length() >= 2 && !isNoiseHint(hint)) {
                hints.add(clipText(hint, MAX_ITEM_LENGTH));
            }
            if (hints.size() >= MAX_SECTION_ITEMS) {
                break;
            }
        }
        return new ArrayList<>(hints);
    }

    /** 判断是否为噪声提示词（常见虚词，不适合作为检索关键词） */
    private boolean isNoiseHint(String value) {
        return "请问".equals(value)
            || "帮我".equals(value)
            || "一下".equals(value)
            || "如何".equals(value)
            || "怎么".equals(value)
            || "什么".equals(value)
            || "哪个".equals(value)
            || "这个".equals(value)
            || "那个".equals(value)
            || "可以".equals(value)
            || "需要".equals(value);
    }
}
