package ai.knowhub.chat.rag.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.model.memory.ConversationMemoryContext;
import ai.knowhub.chat.model.memory.ConversationSummaryPayload;
import ai.knowhub.chat.model.trace.ConversationTraceStageCode;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.AnswerHistoryContext;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.DocumentNavigationDecision;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.rag.model.HistoryPlanningContext;
import ai.knowhub.chat.rag.model.RagRewriteResult;
import ai.knowhub.chat.service.ConversationMemoryService;
import ai.knowhub.chat.service.ConversationTraceRecorder;
import ai.knowhub.chat.service.TaskInfo;
import ai.knowhub.chat.support.TimeSensitiveQueryHelper;
import ai.knowhub.document.model.KnowledgeDocumentDescriptor;
import ai.knowhub.document.model.route.DocumentRouteCandidate;
import ai.knowhub.document.model.route.KnowledgeRouteDecision;
import ai.knowhub.document.service.DocumentKnowledgeService;
import ai.knowhub.document.service.KnowledgeRouteService;
import ai.knowhub.enums.ChatQueryMode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 【聊天准备编排器 — RAG 流水线的"总指挥"】
 *
 * 这是整个 RAG 流水线中最核心的编排类，负责在用户提问后、AI 回答前，
 * 完成所有的"准备工作"：会话记忆加载、问题改写、知识路由、文档导航决策等。
 *
 * 它本身不生成答案，而是产出一个 {@link ConversationExecutionPlan}（执行计划），
 * 后续的执行器（如 ReactAgentExecutor、GraphThenEvidenceExecutor 等）根据这个计划来执行。
 *
 * 设计模式：
 * - 编排器模式（Orchestrator Pattern）：协调多个服务的调用顺序和数据流转
 * - 策略模式（Strategy Pattern）：根据 chatMode 和路由结果选择不同的执行模式
 *
 * RAG 流水线中的位置：
 * 用户提问 -> 【本类：准备编排】-> 执行计划 -> 执行器 -> 生成回答
 *
 * 关键决策点：
 * 1. OPEN_CHAT 模式：直接走 ReAct Agent（开放式聊天，不限文档）
 * 2. DOCUMENT 模式：指定文档的问答
 * 3. AUTO_DOCUMENT 模式：自动选择文档，可能需要澄清
 */
@Slf4j
@Service
public class ChatPreparationOrchestrator {

    /**
     * 能力询问提示词——用户在问"你能做什么"这类问题时，不应该走文档问答。
     */
    private static final Set<String> CAPABILITY_HINTS = Set.of(
        "你都能干什么", "你能做什么", "你可以做什么", "你会什么", "你是谁", "怎么用你", "你能帮我什么"
    );

    /**
     * 开放式聊天提示词——天气、新闻等实时信息类问题，不适合文档问答模式。
     */
    private static final Set<String> OPEN_CHAT_HINTS = Set.of(
        "天气", "温度", "下雨", "新闻", "股价", "汇率", "热搜", "今天", "明天", "最新", "现在"
    );

    /**
     * 闲聊提示词——打招呼、感谢等社交性对话。
     */
    private static final Set<String> CHITCHAT_HINTS = Set.of(
        "你好", "您好", "hello", "hi", "谢谢", "感谢", "再见", "拜拜"
    );

    /** RAG 配置属性 */
    private final ChatRagProperties properties;
    /** 会话记忆服务，负责加载和压缩历史对话 */
    private final ConversationMemoryService conversationMemoryService;
    /** 回答历史上下文组装器，处理追问场景 */
    private final AnswerHistoryContextAssembler answerHistoryContextAssembler;
    /** 查询改写服务，把口语化问题变成检索友好的表达 */
    private final ChatQueryRewriteService chatQueryRewriteService;
    /** 文档问题路由器，决定走图查询还是混合检索 */
    private final DocumentQuestionRouter documentQuestionRouter;
    /** 知识路由服务，AUTO_DOCUMENT 模式下选择目标文档 */
    private final KnowledgeRouteService knowledgeRouteService;
    /** 文档知识服务，提供可检索文档的元数据 */
    private final DocumentKnowledgeService documentKnowledgeService;

    /**
     * 构造函数，通过 Spring 依赖注入所有需要的服务。
     */
    public ChatPreparationOrchestrator(ChatRagProperties properties,
                                       ConversationMemoryService conversationMemoryService,
                                       AnswerHistoryContextAssembler answerHistoryContextAssembler,
                                       ChatQueryRewriteService chatQueryRewriteService,
                                       DocumentQuestionRouter documentQuestionRouter,
                                       KnowledgeRouteService knowledgeRouteService,
                                       DocumentKnowledgeService documentKnowledgeService) {
        this.properties = properties;
        this.conversationMemoryService = conversationMemoryService;
        this.answerHistoryContextAssembler = answerHistoryContextAssembler;
        this.chatQueryRewriteService = chatQueryRewriteService;
        this.documentQuestionRouter = documentQuestionRouter;
        this.knowledgeRouteService = knowledgeRouteService;
        this.documentKnowledgeService = documentKnowledgeService;
    }

    /**
     * 【编排入口方法】执行前编排：决定本轮问题应该走哪条执行路径。
     *
     * 这是整个 RAG 流水线的起点。它不负责生成答案，而是完成以下准备工作：
     * 1. 加载会话记忆（历史对话压缩 + 最近几轮原文）
     * 2. 改写问题（补全上下文、拆分子问题）
     * 3. 路由知识范围（AUTO_DOCUMENT 模式下选择文档）
     * 4. 判断执行模式（图查询 / 混合检索 / 澄清 / ReAct Agent）
     * 5. 构建并返回执行计划
     *
     * @param taskInfo 任务信息，包含会话ID、问题、聊天模式、选中的文档等
     * @return ConversationExecutionPlan 执行计划，包含后续执行器需要的所有信息
     */
    // 执行前编排入口：它不负责生成答案，而是决定本轮问题应该走哪条执行路径。
    public ConversationExecutionPlan prepare(TaskInfo taskInfo) {
        // 从 taskInfo 中解构出所有需要的参数
        String conversationId = taskInfo.conversationId();
        String question = taskInfo.question();
        ChatQueryMode chatMode = taskInfo.chatMode();
        Long selectedDocumentId = taskInfo.selectedDocumentId();
        String selectedDocumentName = taskInfo.selectedDocumentName();
        Long selectedTaskId = taskInfo.selectedTaskId();
        LocalDate currentDate = taskInfo.currentDate();
        String currentDateText = taskInfo.currentDateText();
        ConversationTraceRecorder traceRecorder = taskInfo.traceRecorder();

        // ========== 阶段 1：加载会话记忆 ==========
        ConversationTraceRecorder.StageHandle memoryStage = traceRecorder == null
            ? null
            : traceRecorder.startStage(ConversationTraceStageCode.MEMORY, chatMode == null ? "" : chatMode.name(), "正在装载会话记忆与最近窗口。", null);
        ConversationMemoryContext memoryContext;
        try {
            // 会话记忆会把长对话压缩成摘要，再拼上最近几轮原文，减少 Prompt 过长的问题。
            memoryContext = summarizeHistory(conversationId, traceRecorder);
            if (traceRecorder != null) {
                traceRecorder.completeStage(memoryStage, "会话记忆装载完成。", Map.of(
                    "compressionApplied", memoryContext != null && memoryContext.isCompressionApplied(),
                    "coveredExchangeId", memoryContext == null ? 0L : memoryContext.getCoveredExchangeId(),
                    "coveredExchangeCount", memoryContext == null ? 0 : memoryContext.getCoveredExchangeCount(),
                    "compressionCount", memoryContext == null ? 0 : memoryContext.getCompressionCount(),
                    "longTermSummary", memoryContext == null ? "" : safeText(memoryContext.getLongTermSummary()),
                    "recentTranscript", memoryContext == null ? "" : safeText(memoryContext.getRecentTranscript()),
                    "answerRecentTranscript", memoryContext == null ? "" : safeText(memoryContext.getAnswerRecentTranscript())
                ));
            }
        }
        catch (RuntimeException exception) {
            if (traceRecorder != null) {
                traceRecorder.failStage(memoryStage, "会话记忆装载失败。", exception.getMessage(), null);
            }
            throw exception;
        }

        // 从记忆上下文中提取历史规划信息（会话目标、已确认事实、待跟进问题等）
        HistoryPlanningContext historyPlanningContext = buildHistoryPlanningContext(memoryContext);
        // 构建用于问题改写的历史摘要
        String historySummary = buildPlanningHistory(memoryContext, historyPlanningContext);
        // 构建用于回答的历史上下文（处理追问场景）
        AnswerHistoryContext answerHistoryContext = buildAnswerHistoryContext(
            question,
            memoryContext == null ? "" : memoryContext.getAnswerRecentTranscript()
        );

        // 判断问题是否涉及时效性（需要当前日期锚定、需要最新搜索）
        boolean requiresCurrentDateAnchoring = TimeSensitiveQueryHelper.requiresCurrentDateAnchoring(question);
        boolean requiresFreshSearch = TimeSensitiveQueryHelper.requiresFreshSearch(question);
        if (chatMode == null) {
            throw new IllegalArgumentException("chatMode 不能为空");
        }

        // ========== 阶段 2：根据聊天模式分流 ==========

        if (chatMode == ChatQueryMode.OPEN_CHAT) {
            // 开放式聊天不限定某份文档，直接交给 ReAct Agent，让它按需使用联网搜索或工具。
            ConversationExecutionPlan plan = basePlan(question, chatMode, memoryContext, historyPlanningContext, historySummary, answerHistoryContext, currentDate, currentDateText,
                requiresCurrentDateAnchoring, requiresFreshSearch)
                .mode(ExecutionMode.REACT_AGENT)
                .build();
            if (traceRecorder != null) {
                ConversationTraceRecorder.StageHandle routeStage = traceRecorder.startStage(ConversationTraceStageCode.ROUTE, ExecutionMode.REACT_AGENT.name(), "路由到开放式 Agent。", null);
                traceRecorder.completeStage(routeStage, "已判定走开放式 Agent 路径。", Map.of(
                    "chatMode", chatMode.name(),
                    "executionMode", ExecutionMode.REACT_AGENT.name(),
                    "requiresFreshSearch", requiresFreshSearch,
                    "requiresCurrentDateAnchoring", requiresCurrentDateAnchoring
                ));
            }
            return plan;
        }

        // 文档问答模式需要 RAG 功能开启
        if (!properties.isEnabled()) {
            throw new IllegalStateException("当前文档问答模式未启用，请先开启聊天侧 RAG 编排");
        }
        if (chatMode == ChatQueryMode.DOCUMENT && (selectedDocumentId == null || selectedTaskId == null)) {
            throw new IllegalArgumentException("当前文档问答模式缺少有效的文档范围");
        }

        // ========== 阶段 3：问题改写 ==========
        ConversationTraceRecorder.StageHandle rewriteStage = traceRecorder == null
            ? null
            : traceRecorder.startStage(
                ConversationTraceStageCode.REWRITE,
                ExecutionMode.RETRIEVAL.name(),
                "正在生成检索友好的问题表达。",
                buildRewriteStageSnapshot(question, historySummary, null)
            );
        RagRewriteResult rewriteResult;
        try {
            // RAG 检索前先改写问题：短追问会补上下文，复合问题会拆成多个子问题。
            rewriteResult = chatQueryRewriteService.rewrite(question, historySummary, traceRecorder);
            if (traceRecorder != null) {
                traceRecorder.completeStage(rewriteStage, "问题改写完成。", buildRewriteStageSnapshot(question, historySummary, rewriteResult));
            }
        }
        catch (RuntimeException exception) {
            if (traceRecorder != null) {
                traceRecorder.failStage(
                    rewriteStage,
                    "问题改写失败。",
                    exception.getMessage(),
                    buildRewriteStageSnapshot(question, historySummary, null)
                );
            }
            throw exception;
        }

        // 从改写结果中提取改写后的问题和子问题列表
        String rewriteQuestion = rewriteResult == null ? safeText(question) : firstNonBlank(rewriteResult.getRewrittenQuestion(), safeText(question));
        List<String> rewriteSubQuestions = rewriteResult == null || rewriteResult.getSubQuestions() == null || rewriteResult.getSubQuestions().isEmpty()
            ? List.of(rewriteQuestion)
            : rewriteResult.getSubQuestions();

        // ========== 阶段 4：知识路由（AUTO_DOCUMENT 模式） ==========
        Long routedDocumentId = selectedDocumentId;
        String routedDocumentName = selectedDocumentName;
        Long routedTaskId = selectedTaskId;
        List<Long> routedDocumentIds = routedDocumentId == null ? List.of() : List.of(routedDocumentId);
        List<Long> routedTaskIds = routedTaskId == null ? List.of() : List.of(routedTaskId);
        if (chatMode == ChatQueryMode.AUTO_DOCUMENT) {
            // 自动文档模式会先选知识范围；不确定时返回澄清问题，而不是随便选一份文档回答。
            KnowledgeRouteDecision routeDecision = knowledgeRouteService.route(question, rewriteQuestion);
            knowledgeRouteService.recordAutoRoute(conversationId, taskInfo.exchangeId(), question, rewriteQuestion, routeDecision);
            List<DocumentRouteCandidate> candidateDocuments = selectAutoCandidates(routeDecision, question, rewriteQuestion);
            // 如果需要澄清（范围不清晰），直接返回澄清模式的执行计划。
            // 评测/批处理模式下可关闭澄清，让问题继续进入 RAG 检索链路。
            if (shouldAskClarification(routeDecision, candidateDocuments)) {
                return basePlan(question, chatMode, memoryContext, historyPlanningContext, historySummary, answerHistoryContext, currentDate, currentDateText,
                    requiresCurrentDateAnchoring, requiresFreshSearch)
                    .mode(ExecutionMode.CLARIFICATION)
                    .rewriteQuestion(rewriteQuestion)
                    .rewriteSubQuestions(rewriteSubQuestions)
                    .retrievalQuestion(rewriteQuestion)
                    .retrievalSubQuestions(rewriteSubQuestions)
                    .retrievalDocumentIds(candidateDocuments.stream()
                        .map(DocumentRouteCandidate::getDocumentId)
                        .filter(StrUtil::isNotBlank)
                        .map(Long::valueOf)
                        .toList())
                    .retrievalTaskIds(candidateDocuments.stream()
                        .map(DocumentRouteCandidate::getLastIndexTaskId)
                        .filter(StrUtil::isNotBlank)
                        .map(Long::valueOf)
                        .toList())
                    .clarificationReply(buildClarificationReply(question, routeDecision, candidateDocuments))
                    .clarificationOptions(buildClarificationOptions(candidateDocuments))
                    .clarificationReason(buildClarificationReason(routeDecision, candidateDocuments))
                    .build();
            }
            // 置信度足够高时，选择排名第一的文档
            boolean confidentTopDocument = routeDecision != null
                && routeDecision.getConfidence() != null
                && routeDecision.getConfidence().doubleValue() >= 0.55D;
            DocumentRouteCandidate topDocument = confidentTopDocument && !candidateDocuments.isEmpty() ? candidateDocuments.get(0) : null;
            if (topDocument != null && StrUtil.isNotBlank(topDocument.getDocumentId()) && StrUtil.isNotBlank(topDocument.getLastIndexTaskId())) {
                routedDocumentId = Long.valueOf(topDocument.getDocumentId());
                routedDocumentName = topDocument.getDocumentName();
                routedTaskId = Long.valueOf(topDocument.getLastIndexTaskId());
            }
            else {
                routedDocumentId = null;
                routedDocumentName = "";
                routedTaskId = null;
            }
            routedDocumentIds = candidateDocuments.stream()
                .map(DocumentRouteCandidate::getDocumentId)
                .filter(StrUtil::isNotBlank)
                .map(Long::valueOf)
                .toList();
            routedTaskIds = candidateDocuments.stream()
                .map(DocumentRouteCandidate::getLastIndexTaskId)
                .filter(StrUtil::isNotBlank)
                .map(Long::valueOf)
                .toList();
            if (traceRecorder != null) {
                traceRecorder.completeStage(
                    traceRecorder.startStage(ConversationTraceStageCode.ROUTE, "AUTO_DOCUMENT", "正在生成知识范围候选。", null),
                    "知识范围路由完成。",
                    Map.of(
                        "confidence", routeDecision == null || routeDecision.getConfidence() == null ? "" : routeDecision.getConfidence().toPlainString(),
                        "routeStatus", routeDecision == null ? "" : StrUtil.blankToDefault(routeDecision.getRouteStatus(), ""),
                        "candidateDocumentCount", candidateDocuments.size(),
                        "confidentTopDocument", confidentTopDocument,
                        "topDocumentId", topDocument == null ? "" : StrUtil.blankToDefault(topDocument.getDocumentId(), ""),
                        "topDocumentName", topDocument == null ? "" : StrUtil.blankToDefault(topDocument.getDocumentName(), "")
                    )
                );
            }
        }
        else if (chatMode == ChatQueryMode.DOCUMENT) {
            knowledgeRouteService.recordShadowRoute(conversationId, taskInfo.exchangeId(), selectedDocumentId, question, rewriteQuestion);
        }

        // ========== 阶段 5：文档导航路由 ==========
        ConversationTraceRecorder.StageHandle routeStage = traceRecorder == null
            ? null
            : traceRecorder.startStage(ConversationTraceStageCode.ROUTE, ExecutionMode.RETRIEVAL.name(), "正在判定图查询还是混合检索。", null);
        DocumentNavigationDecision navigationDecision;
        try {
            if (properties.isForceRetrievalMode()) {
                navigationDecision = null;
            }
            else {
                // 文档导航路由会判断：普通证据检索够不够，还是应该进入章节/条款/图结构查询。
                navigationDecision = documentQuestionRouter.route(routedDocumentId, question, rewriteResult);
            }
            if (traceRecorder != null) {
                traceRecorder.completeStage(routeStage, "执行路由完成。", Map.of(
                    "executionMode", properties.isForceRetrievalMode() ? ExecutionMode.RETRIEVAL.name() : navigationDecision == null || navigationDecision.getExecutionMode() == null ? "" : navigationDecision.getExecutionMode().name(),
                    "targetSectionHint", navigationDecision == null || navigationDecision.getStructureAnchor() == null ? "" : StrUtil.blankToDefault(navigationDecision.getStructureAnchor().getTargetSectionHint(), ""),
                    "targetItemIndex", navigationDecision == null || navigationDecision.getItemAnchor() == null || navigationDecision.getItemAnchor().getItemIndex() == null
                        ? ""
                        : String.valueOf(navigationDecision.getItemAnchor().getItemIndex()),
                    "navigationSummary", properties.isForceRetrievalMode() ? "forceRetrievalMode" : navigationDecision == null ? "" : StrUtil.blankToDefault(navigationDecision.getSummaryText(), "")
                ));
            }
        }
        catch (RuntimeException exception) {
            if (traceRecorder != null) {
                traceRecorder.failStage(routeStage, "执行路由失败。", exception.getMessage(), null);
            }
            throw exception;
        }

        // 从导航决策中提取执行模式、检索问题和子问题
        ExecutionMode executionMode = properties.isForceRetrievalMode()
            ? ExecutionMode.RETRIEVAL
            : navigationDecision == null || navigationDecision.getExecutionMode() == null
            ? ExecutionMode.RETRIEVAL
            : navigationDecision.getExecutionMode();
        String retrievalQuestion = navigationDecision == null || navigationDecision.getRetrievalPlan() == null
            ? rewriteQuestion
            : firstNonBlank(navigationDecision.getRetrievalPlan().getRetrievalQuestion(), rewriteQuestion);
        List<String> retrievalSubQuestions = navigationDecision == null || navigationDecision.getRetrievalPlan() == null
            || navigationDecision.getRetrievalPlan().getSubQuestions() == null || navigationDecision.getRetrievalPlan().getSubQuestions().isEmpty()
            ? rewriteSubQuestions
            : navigationDecision.getRetrievalPlan().getSubQuestions();

        log.info("聊天编排完成: conversationId={}, chatMode={}, originalQuestion='{}', rewriteQuestion='{}', retrievalQuestion='{}', executionMode={}, targetSection='{}'",
            conversationId,
            chatMode,
            safeText(question),
            rewriteQuestion,
            retrievalQuestion,
            executionMode,
            navigationDecision == null || navigationDecision.getStructureAnchor() == null ? "" : safeText(navigationDecision.getStructureAnchor().getTargetSectionHint()));

        // ========== 阶段 6：构建最终执行计划 ==========
        // ConversationExecutionPlan 是后续执行器的"作战计划"，包含执行模式、检索问题、文档范围和兜底话术。
        return basePlan(question, chatMode, memoryContext, historyPlanningContext, historySummary, answerHistoryContext, currentDate, currentDateText,
            requiresCurrentDateAnchoring, requiresFreshSearch)
            .mode(executionMode)
            .navigationDecision(navigationDecision)
            .rewriteQuestion(rewriteQuestion)
            .rewriteSubQuestions(rewriteSubQuestions)
            .retrievalQuestion(retrievalQuestion)
            .retrievalSubQuestions(retrievalSubQuestions)
            .selectedDocumentId(routedDocumentId)
            .selectedDocumentName(routedDocumentName)
            .selectedTaskId(routedTaskId)
            .retrievalDocumentIds(routedDocumentIds)
            .retrievalTaskIds(routedTaskIds)
            .noEvidenceReply(buildDocumentModeNoEvidenceReply(question, requiresFreshSearch))
            .build();
    }

    /**
     * 构建执行计划的基础部分（所有模式共用的字段）。
     *
     * @return ConversationExecutionPlanBuilder 构建器，调用方可以继续链式设置其他字段
     */
    private ConversationExecutionPlan.ConversationExecutionPlanBuilder basePlan(String question,
                                                                                ChatQueryMode chatMode,
                                                                                ConversationMemoryContext memoryContext,
                                                                                HistoryPlanningContext historyPlanningContext,
                                                                                String historySummary,
                                                                                AnswerHistoryContext answerHistoryContext,
                                                                                LocalDate currentDate,
                                                                                String currentDateText,
                                                                                boolean requiresCurrentDateAnchoring,
                                                                                boolean requiresFreshSearch) {
        return ConversationExecutionPlan.builder()
            .chatMode(chatMode)
            .originalQuestion(question)
            .agentQuestion(question)
            .rewriteQuestion(question)
            .rewriteSubQuestions(List.of(question))
            .retrievalQuestion(question)
            .retrievalSubQuestions(List.of(question))
            .historySummary(historySummary)
            .longTermSummary(memoryContext.getLongTermSummary())
            .historyPlanningContext(historyPlanningContext)
            .recentHistoryTranscript(memoryContext.getRecentTranscript())
            .answerRecentTranscript(memoryContext.getAnswerRecentTranscript())
            .answerHistoryContext(answerHistoryContext)
            .historyCompressionApplied(memoryContext.isCompressionApplied())
            .historyCoveredExchangeId(memoryContext.getCoveredExchangeId())
            .historyCoveredExchangeCount(memoryContext.getCoveredExchangeCount())
            .historyCompressionCount(memoryContext.getCompressionCount())
            .currentDate(currentDate)
            .currentDateText(currentDateText)
            .requiresCurrentDateAnchoring(requiresCurrentDateAnchoring)
            .requiresFreshSearch(requiresFreshSearch)
            .noEvidenceReply(properties.getNoEvidenceReply());
    }

    /**
     * 构建改写阶段的快照数据，用于追踪记录。
     */
    private Map<String, Object> buildRewriteStageSnapshot(String question,
                                                          String historySummary,
                                                          RagRewriteResult rewriteResult) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("originalQuestion", StrUtil.blankToDefault(question, ""));
        snapshot.put("historyContext", StrUtil.blankToDefault(historySummary, ""));
        snapshot.put("rewriteQuestion", rewriteResult == null ? "" : StrUtil.blankToDefault(rewriteResult.getRewrittenQuestion(), ""));
        snapshot.put("subQuestions", rewriteResult == null || rewriteResult.getSubQuestions() == null ? List.of() : rewriteResult.getSubQuestions());
        snapshot.put("rawModelOutput", rewriteResult == null ? "" : StrUtil.blankToDefault(rewriteResult.getRawModelOutput(), ""));

        ChatRagProperties.RewriteOptionsProperties rewriteOptions = properties == null ? null : properties.getRewriteOptions();
        boolean overrideEnabled = rewriteOptions != null && rewriteOptions.isEnabled();
        snapshot.put("rewriteOverrideEnabled", overrideEnabled);
        snapshot.put("rewriteTemperature", rewriteOptions == null ? null : rewriteOptions.getTemperature());
        snapshot.put("rewriteTopP", rewriteOptions == null ? null : rewriteOptions.getTopP());
        snapshot.put("rewriteThinking", rewriteOptions == null ? null : rewriteOptions.getThinking());
        return snapshot;
    }

    /**
     * 加载会话记忆上下文（委托给 ConversationMemoryService）。
     */
    private ConversationMemoryContext summarizeHistory(String conversationId, ConversationTraceRecorder traceRecorder) {
        return conversationMemoryService.loadMemoryContext(conversationId, traceRecorder);
    }

    /**
     * 从记忆上下文中构建历史规划信息。
     *
     * HistoryPlanningContext 包含：
     * - conversationGoal：会话目标（用户最终想达成什么）
     * - stableFacts：已确认的事实（避免重复询问）
     * - pendingQuestions：待跟进的问题
     * - retrievalHints：检索提示（帮助检索引擎理解上下文）
     */
    private HistoryPlanningContext buildHistoryPlanningContext(ConversationMemoryContext memoryContext) {
        ConversationSummaryPayload payload = memoryContext == null ? null : memoryContext.getSummaryPayload();
        if (payload == null) {
            return HistoryPlanningContext.builder().build();
        }
        return HistoryPlanningContext.builder()
            .conversationGoal(payload.getConversationGoal())
            .stableFacts(payload.getStableFacts() == null ? List.of() : new ArrayList<>(payload.getStableFacts()))
            .pendingQuestions(payload.getPendingQuestions() == null ? List.of() : new ArrayList<>(payload.getPendingQuestions()))
            .retrievalHints(payload.getRetrievalHints() == null ? List.of() : new ArrayList<>(payload.getRetrievalHints()))
            .queryContextHints(payload.getRetrievalHints() == null ? List.of() : new ArrayList<>(payload.getRetrievalHints()))
            .build();
    }

    /**
     * 构建用于问题改写的历史摘要。
     *
     * 会把结构化的历史信息（会话目标、已确认事实等）和最近的对话原文拼接在一起，
     * 并控制总长度不超过 planningHistoryMaxChars 配置。
     */
    private String buildPlanningHistory(ConversationMemoryContext memoryContext,
                                        HistoryPlanningContext historyPlanningContext) {
        String structuredHistory = buildStructuredPlanningHistory(historyPlanningContext);
        String recentTranscript = memoryContext == null ? "" : safeText(memoryContext.getRecentTranscript());
        int maxChars = Math.max(1, properties.getPlanningHistoryMaxChars());
        if (recentTranscript.isBlank()) {
            return clipHead(structuredHistory, maxChars);
        }
        // 最近对话分配 65% 的预算（最近的对话对改写更有用）
        int recentBudget = Math.min(Math.max(maxChars / 2, (int) Math.round(maxChars * 0.65D)), maxChars);
        String recentPart = clipTail(recentTranscript, recentBudget);
        int structuredBudget = Math.max(0, maxChars - recentPart.length() - (recentPart.isBlank() ? 0 : 2));
        String structuredPart = clipHead(structuredHistory, structuredBudget);
        return joinNonBlank(structuredPart, recentPart);
    }

    /**
     * 构建用于回答的历史上下文（委托给 AnswerHistoryContextAssembler）。
     */
    private AnswerHistoryContext buildAnswerHistoryContext(String question,
                                                           String answerRecentTranscript) {
        return answerHistoryContextAssembler.assemble(question, answerRecentTranscript);
    }

    /**
     * 构建结构化的历史规划文本（会话目标、已确认事实、待跟进问题、检索提示）。
     */
    private String buildStructuredPlanningHistory(HistoryPlanningContext historyPlanningContext) {
        StringBuilder builder = new StringBuilder();
        if (historyPlanningContext == null) {
            return "";
        }
        appendSection(builder, "会话目标", historyPlanningContext.getConversationGoal());
        appendBulletSection(builder, "已确认事实", historyPlanningContext.getStableFacts());
        appendBulletSection(builder, "待跟进问题", historyPlanningContext.getPendingQuestions());
        appendBulletSection(builder, "检索提示", historyPlanningContext.getRetrievalHints());
        return builder.toString().trim();
    }

    /** 追加带标题的段落 */
    private void appendSection(StringBuilder builder, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append("【").append(title).append("】\n").append(content.trim()).append('\n');
    }

    /** 追加带标题的列表段落（每项前加 "- "） */
    private void appendBulletSection(StringBuilder builder, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append("【").append(title).append("】\n");
        values.stream()
            .filter(item -> item != null && !item.isBlank())
            .limit(5)
            .forEach(item -> builder.append("- ").append(item.trim()).append('\n'));
    }

    /**
     * 从文本头部截取指定长度（保留开头内容）。
     */
    private String clipHead(String text, int maxChars) {
        String normalized = safeText(text);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        if (maxChars <= 1) {
            return "";
        }
        return normalized.substring(0, maxChars - 1) + "…";
    }

    /**
     * 从文本末尾截取指定长度（保留最近内容）。
     */
    private String clipTail(String text, int maxChars) {
        String normalized = safeText(text);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        if (maxChars <= 1) {
            return "";
        }
        int start = Math.max(0, normalized.length() - (maxChars - 1));
        return "…" + normalized.substring(start);
    }

    /** 拼接两个非空文本，中间用换行分隔 */
    private String joinNonBlank(String left, String right) {
        if (left == null || left.isBlank()) {
            return safeText(right);
        }
        if (right == null || right.isBlank()) {
            return safeText(left);
        }
        return left.trim() + "\n\n" + right.trim();
    }

    /** 安全文本处理：null 转为空字符串 */
    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    /** 返回第一个非空的字符串 */
    private String firstNonBlank(String left, String right) {
        if (StrUtil.isNotBlank(left)) {
            return left.trim();
        }
        return safeText(right);
    }

    /**
     * 选择自动文档候选。
     *
     * 如果知识路由服务给出了高置信度的候选，直接使用；
     * 否则合并路由候选和基于文档元数据的兜底候选。
     */
    private List<DocumentRouteCandidate> selectAutoCandidates(KnowledgeRouteDecision routeDecision,
                                                              String question,
                                                              String rewriteQuestion) {
        if (routeDecision == null || routeDecision.getDocuments() == null || routeDecision.getDocuments().isEmpty()) {
            return fallbackDocuments(question, rewriteQuestion, resolveRouteDocumentTopK());
        }
        int candidateLimit = resolveRouteDocumentTopK();
        List<DocumentRouteCandidate> candidates = routeDecision.getDocuments().stream()
            .filter(item -> StrUtil.isNotBlank(item.getDocumentId()) && StrUtil.isNotBlank(item.getLastIndexTaskId()))
            .limit(candidateLimit)
            .toList();
        if (candidates.isEmpty()) {
            return fallbackDocuments(question, rewriteQuestion, candidateLimit);
        }
        // 低置信度时合并路由候选和兜底候选
        if (routeDecision.getConfidence() != null && routeDecision.getConfidence().doubleValue() < 0.55D) {
            return mergeCandidates(candidates, fallbackDocuments(question, rewriteQuestion, candidateLimit), candidateLimit);
        }
        return candidates;
    }

    private int resolveRouteDocumentTopK() {
        return Math.max(1, properties.getRouteDocumentTopK());
    }

    /**
     * 兜底文档候选：基于文档元数据（名称、标签等）做简单的关键词匹配打分。
     *
     * 当知识路由服务没有给出稳定候选时使用，至少给用户一个可确认的范围。
     */
    private List<DocumentRouteCandidate> fallbackDocuments(String question,
                                                           String rewriteQuestion,
                                                           int limit) {
        // 路由服务没有给出稳定候选时，用文档元数据做保守兜底，至少给用户一个可确认的范围。
        List<KnowledgeDocumentDescriptor> descriptors = documentKnowledgeService.listRetrievableDocuments();
        if (descriptors == null || descriptors.isEmpty()) {
            return List.of();
        }
        List<String> queryTerms = extractFallbackTerms(question, rewriteQuestion);
        return descriptors.stream()
            .sorted((left, right) -> Double.compare(
                fallbackDescriptorScore(right, queryTerms),
                fallbackDescriptorScore(left, queryTerms)
            ))
            .limit(Math.max(1, limit))
            .map(item -> new DocumentRouteCandidate(
                String.valueOf(item.getDocumentId()),
                item.getDocumentName(),
                item.getLastIndexTaskId() == null ? "" : String.valueOf(item.getLastIndexTaskId()),
                StrUtil.blankToDefault(item.getKnowledgeScopeCode(), ""),
                StrUtil.blankToDefault(item.getKnowledgeScopeName(), ""),
                StrUtil.blankToDefault(item.getBusinessCategory(), ""),
                StrUtil.blankToDefault(item.getDocumentTags(), ""),
                BigDecimal.valueOf(fallbackDescriptorScore(item, queryTerms)).setScale(4, RoundingMode.HALF_UP),
                "低置信度时基于文档元数据进行保守扩范围候选"
            ))
            .toList();
    }

    /**
     * 合并两组候选文档，按 documentId 去重，primary 优先。
     */
    private List<DocumentRouteCandidate> mergeCandidates(List<DocumentRouteCandidate> primary,
                                                         List<DocumentRouteCandidate> secondary,
                                                         int limit) {
        LinkedHashMap<String, DocumentRouteCandidate> merged = new LinkedHashMap<>();
        primary.forEach(item -> merged.put(item.getDocumentId(), item));
        secondary.forEach(item -> merged.putIfAbsent(item.getDocumentId(), item));
        return merged.values().stream().limit(Math.max(1, limit)).toList();
    }

    /**
     * 判断是否需要向用户发起澄清。
     *
     * 澄清的条件：
     * 1. 没有任何候选文档
     * 2. 路由服务没有给出候选
     * 3. 路由置信度低于 0.55
     * 4. 前两个候选的分数差距很小，且属于不同知识域（说明问题有歧义）
     *
     * 澄清机制保护回答质量：范围不清时先问清楚，比误选文档后编造答案更安全。
     */
    private boolean shouldAskClarification(KnowledgeRouteDecision routeDecision,
                                           List<DocumentRouteCandidate> candidateDocuments) {
        if (!properties.isClarificationEnabled() || properties.isForceRetrievalMode()) {
            return false;
        }
        // 澄清判断保护回答质量：范围不清时先问清楚，比误选文档后编造答案更安全。
        if (candidateDocuments == null || candidateDocuments.isEmpty()) {
            return true;
        }
        if (routeDecision == null || routeDecision.getDocuments() == null || routeDecision.getDocuments().isEmpty()) {
            return true;
        }
        if (routeDecision.getConfidence() == null || routeDecision.getConfidence().doubleValue() < 0.55D) {
            return true;
        }
        if (candidateDocuments.size() < 2) {
            return false;
        }
        // 检查前两个候选的分数差距和知识域差异
        BigDecimal topScore = candidateDocuments.get(0).getScore();
        BigDecimal secondScore = candidateDocuments.get(1).getScore();
        if (topScore == null || secondScore == null) {
            return false;
        }
        return topScore.subtract(secondScore).doubleValue() <= 3D
            && !Objects.equals(candidateDocuments.get(0).getKnowledgeScopeCode(), candidateDocuments.get(1).getKnowledgeScopeCode());
    }

    /**
     * 构建澄清回复文本，列出候选文档让用户选择。
     */
    private String buildClarificationReply(String originalQuestion,
                                           KnowledgeRouteDecision routeDecision,
                                           List<DocumentRouteCandidate> candidateDocuments) {
        List<DocumentRouteCandidate> topCandidates = candidateDocuments == null ? List.of() : candidateDocuments.stream().limit(3).toList();
        if (topCandidates.isEmpty()) {
            return "当前我还不能稳定判断你想问哪份知识文档。请补充更具体的文档名、主题词，或者直接切换到“当前文档问答”后指定文档。";
        }
        StringBuilder builder = new StringBuilder("这个问题目前存在文档范围歧义，我先确认你想问哪一份：\n");
        for (int index = 0; index < topCandidates.size(); index++) {
            DocumentRouteCandidate item = topCandidates.get(index);
            builder.append(index + 1)
                .append(". 《")
                .append(StrUtil.blankToDefault(item.getDocumentName(), item.getDocumentId()))
                .append("》");
            if (StrUtil.isNotBlank(item.getKnowledgeScopeName()) || StrUtil.isNotBlank(item.getKnowledgeScopeCode())) {
                builder.append("（")
                    .append(StrUtil.blankToDefault(item.getKnowledgeScopeName(), item.getKnowledgeScopeCode()))
                    .append("）");
            }
            builder.append('\n');
        }
        builder.append("你可以直接回复文档名，或者改用“当前文档问答”模式明确指定文档。");
        return builder.toString();
    }

    /**
     * 构建澄清选项列表（供前端展示快捷选择按钮）。
     */
    private List<String> buildClarificationOptions(List<DocumentRouteCandidate> candidateDocuments) {
        if (candidateDocuments == null || candidateDocuments.isEmpty()) {
            return List.of();
        }
        return candidateDocuments.stream()
            .limit(3)
            .map(item -> "我想问《" + StrUtil.blankToDefault(item.getDocumentName(), item.getDocumentId()) + "》")
            .toList();
    }

    /**
     * 构建澄清原因说明（用于日志和调试）。
     */
    private String buildClarificationReason(KnowledgeRouteDecision routeDecision,
                                            List<DocumentRouteCandidate> candidateDocuments) {
        if (routeDecision == null || routeDecision.getDocuments() == null || routeDecision.getDocuments().isEmpty()) {
            return "当前自动知识路由没有形成稳定候选，已改为先向用户确认文档范围。";
        }
        String confidenceText = routeDecision.getConfidence() == null ? "-" : routeDecision.getConfidence().toPlainString();
        int candidateCount = candidateDocuments == null ? 0 : candidateDocuments.size();
        return "当前自动知识路由置信度为 " + confidenceText + "，候选文档数为 " + candidateCount + "，为避免误选文档，先返回澄清问题。";
    }

    /**
     * 提取兜底匹配的关键词（包括 n-gram 子串）。
     */
    private List<String> extractFallbackTerms(String question, String rewriteQuestion) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String routingText = (safeText(question) + " " + safeText(rewriteQuestion)).trim();
        for (String segment : routingText.split("[\\s、，,；;：:（）()\\-]的和及与或]+")) {
            String trimmed = segment.trim();
            if (trimmed.length() >= 2) {
                terms.add(trimmed);
                // 对长度>=4的词生成 2-6 gram 子串，提高模糊匹配能力
                if (trimmed.length() >= 4) {
                    int maxGram = Math.min(6, trimmed.length());
                    for (int gram = 2; gram <= maxGram; gram++) {
                        for (int start = 0; start + gram <= trimmed.length(); start++) {
                            terms.add(trimmed.substring(start, start + gram));
                        }
                    }
                }
            }
        }
        return terms.stream().limit(40).toList();
    }

    /**
     * 基于文档元数据计算兜底匹配分数。
     *
     * 匹配规则：长词匹配分数更高（8字以上+12分，5字以上+8分，3字以上+4分，2字+2分）。
     * 已被更长词覆盖的短词不重复计分。
     */
    private double fallbackDescriptorScore(KnowledgeDocumentDescriptor descriptor, List<String> queryTerms) {
        String content = normalizeFallbackText(String.join(" ",
            StrUtil.blankToDefault(descriptor.getDocumentName(), ""),
            StrUtil.blankToDefault(descriptor.getKnowledgeScopeCode(), ""),
            StrUtil.blankToDefault(descriptor.getKnowledgeScopeName(), ""),
            StrUtil.blankToDefault(descriptor.getBusinessCategory(), ""),
            StrUtil.blankToDefault(descriptor.getDocumentTags(), "")
        ));
        if (queryTerms == null || queryTerms.isEmpty() || content.isBlank()) {
            return 0D;
        }
        double score = 0D;
        // 按长度降序排列，优先匹配长词
        List<String> sortedTerms = queryTerms.stream()
            .map(this::normalizeFallbackText)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
        List<String> matched = new ArrayList<>();
        for (String term : sortedTerms) {
            if (term.length() < 2) {
                continue;
            }
            // 如果当前词已被更长的已匹配词覆盖，跳过
            boolean covered = matched.stream().anyMatch(existing -> existing.contains(term));
            if (covered) {
                continue;
            }
            if (content.contains(term)) {
                matched.add(term);
                if (term.length() >= 8) {
                    score += 12D;
                }
                else if (term.length() >= 5) {
                    score += 8D;
                }
                else if (term.length() >= 3) {
                    score += 4D;
                }
                else {
                    score += 2D;
                }
            }
        }
        return score;
    }

    /** 标准化兜底文本：去除标点符号和空白，转小写 */
    private String normalizeFallbackText(String value) {
        return StrUtil.blankToDefault(value, "")
            .replaceAll("[\\s>`*#_\\-，,。；;：:（）()“”\"'\\[\\]]+", "")
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 构建文档问答模式下"没有检索到证据"时的兜底回复。
     *
     * 根据问题类型给出不同的引导：
     * - 能力询问：引导用户切换到开放式提问模式
     * - 开放式问题（天气、新闻等）：引导用户切换到开放式提问模式
     * - 普通文档问题：提示用户补充更具体的关键词
     */
    private String buildDocumentModeNoEvidenceReply(String question, boolean requiresFreshSearch) {
        String normalizedQuestion = safeText(question);
        if (looksLikeCapabilityQuestion(normalizedQuestion)) {
            return "当前你正在使用“当前文档问答”模式，我会优先基于所选文档回答。这个问题更像是在询问助手能力，而不是当前文档内容。如果你想了解我能做什么，请切换到“开放式提问”模式。";
        }
        if (looksLikeOpenChatQuestion(normalizedQuestion, requiresFreshSearch)) {
            return "当前你正在使用“当前文档问答”模式，我只能基于所选文档回答。这个问题更像开放式提问，例如天气、最新信息或一般交流。如果你想继续问这类问题，请切换到“开放式提问”模式。";
        }
        return StrUtil.blankToDefault(
            properties.getNoEvidenceReply(),
            "当前没有从当前文档中检索到足够证据，暂时不能给出可靠结论。你可以补充更具体的标题、术语或关键词后再试。"
        );
    }

    /** 判断是否为能力询问类问题 */
    private boolean looksLikeCapabilityQuestion(String normalizedQuestion) {
        if (StrUtil.isBlank(normalizedQuestion)) {
            return false;
        }
        return CAPABILITY_HINTS.stream().anyMatch(normalizedQuestion::contains);
    }

    /** 判断是否为开放式聊天类问题（天气、新闻、闲聊等） */
    private boolean looksLikeOpenChatQuestion(String normalizedQuestion, boolean requiresFreshSearch) {
        if (StrUtil.isBlank(normalizedQuestion)) {
            return false;
        }
        if (requiresFreshSearch) {
            return true;
        }
        if (OPEN_CHAT_HINTS.stream().anyMatch(normalizedQuestion::contains)) {
            return true;
        }
        return CHITCHAT_HINTS.stream().anyMatch(normalizedQuestion::contains);
    }
}
