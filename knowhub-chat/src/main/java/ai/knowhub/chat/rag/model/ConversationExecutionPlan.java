package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.enums.ChatQueryMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话执行计划 —— RAG 流水线的"作战地图"，记录路由决策和执行所需的全部信息。
 *
 * 在 RAG 流水线中的角色
 * 当用户发送一条消息时，系统会先经过"路由规划"阶段，分析用户意图并做出一系列决策：
 * 用哪种执行模式？检索哪些文档？是否需要改写查询？是否需要拆分子问题？
 * 所有这些决策结果都记录在本对象中，供后续执行阶段使用。
 *
 * 可以把它理解为一份"执行计划书"——各执行器（ai.knowhub.chat.rag.executor.ConversationExecutor）
 * 根据这份计划书知道自己该做什么、怎么做。
 *
 * 核心字段分组
 * 
 *   <b>执行模式</b>：#mode、#chatMode
 *   <b>问题文本</b>：#originalQuestion、#agentQuestion、#rewriteQuestion、#retrievalQuestion
 *   <b>子问题</b>：#rewriteSubQuestions、#retrievalSubQuestions
 *   <b>历史上下文</b>：#historySummary、#recentHistoryTranscript、#historyPlanningContext、#answerHistoryContext
 *   <b>文档范围</b>：#selectedDocumentId、#retrievalDocumentIds
 *   <b>导航决策</b>：#navigationDecision
 *   <b>澄清信息</b>：#clarificationReply、#clarificationOptions、#clarificationReason
 * @see ExecutionMode 执行模式枚举
 * @see DocumentNavigationDecision 文档导航决策
 * @see HistoryPlanningContext 历史规划上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationExecutionPlan {

    /**
     * 执行模式 —— 决定走哪条执行路径。
     *
     * 由路由规划阶段根据用户问题的特征选择。不同模式对应不同的执行器：
     * RETRIEVAL → RagChatExecutor，GRAPH_ONLY → GraphOnlyExecutor，等等。
     *
     * @see ExecutionMode
     */
    private ExecutionMode mode;

    /**
     * 聊天查询模式。
     *
     * 更高层级的查询模式分类，例如普通对话、知识库问答等。
     * 由业务层定义，与 RAG 内部的 ExecutionMode 配合使用。
     */
    private ChatQueryMode chatMode;

    /**
     * 用户的原始问题文本。
     *
     * 用户在前端输入的原始文本，未经任何改写或处理。
     * 用于日志记录和调试追踪。
     */
    private String originalQuestion;

    /**
     * 交给 ReAct Agent 执行的问题文本。
     *
     * 在 REACT_AGENT 模式下，路由阶段可能会对原始问题做补充或改写，
     * 然后把改写后的问题交给 Agent 自主推理。
     *
     * @see ai.knowhub.chat.rag.executor.ReactAgentExecutor
     */
    private String agentQuestion;

    /**
     * 查询改写后的问题文本。
     *
     * 经过大模型改写后的问题，补充了省略的上下文信息。
     * 例如原始问题"它的价格呢"可能被改写成"XX产品的价格是多少"。
     *
     * @see ChatRagProperties#rewriteEnabled 改写开关
     */
    private String rewriteQuestion;

    /**
     * 改写阶段拆分的子问题列表。
     *
     * 复杂问题会被拆成多个子问题分别检索。
     * 例如"比较 A 和 B 的优缺点"可能拆成"A 的优缺点"和"B 的优缺点"两个子问题。
     */
    @Builder.Default
    private List<String> rewriteSubQuestions = new ArrayList<>();

    /**
     * 最终用于检索的问题文本。
     *
     * 在改写后的问题基础上可能还会做进一步调整（如加入历史上下文关键词），
     * 最终用于检索的问题文本。
     */
    private String retrievalQuestion;

    /**
     * 最终用于检索的子问题列表。
     *
     * 与 #rewriteSubQuestions 类似，但可能经过进一步筛选或合并。
     */
    @Builder.Default
    private List<String> retrievalSubQuestions = new ArrayList<>();

    /**
     * 会话历史摘要。
     *
     * 当对话轮数较多时，早期的对话历史会被压缩成摘要，
     * 释放 Prompt 上下文空间。摘要文本存储在此字段中。
     */
    private String historySummary;

    /**
     * 长期记忆摘要。
     *
     * 跨会话的长期记忆信息，记录用户在历史会话中积累的知识偏好、
     * 已确认的事实等。与 #historySummary（单次会话内）不同。
     */
    private String longTermSummary;

    /**
     * 历史规划上下文。
     *
     * 从历史对话中提取的规划信息，包括对话目标、已确认事实、
     * 待解答问题、检索提示等。帮助当前轮次的检索和生成更有针对性。
     *
     * @see HistoryPlanningContext
     */
    @Builder.Default
    private HistoryPlanningContext historyPlanningContext = new HistoryPlanningContext();

    /**
     * 最近对话的原文记录。
     *
     * 最近 N 轮用户和助手的完整对话原文，用于查询改写时提供上下文。
     */
    private String recentHistoryTranscript;

    /**
     * 回答阶段使用的最近对话记录。
     *
     * 与 #recentHistoryTranscript 可能不同——回答阶段需要的历史上下文
     * 可能比改写阶段更少或更多。
     */
    private String answerRecentTranscript;

    /**
     * 回答历史上下文。
     *
     * 封装了组装回答 Prompt 时需要用到的历史信息，
     * 包括渲染后的文本、结构化上下文、字符预算等。
     *
     * @see AnswerHistoryContext
     */
    private AnswerHistoryContext answerHistoryContext;

    /**
     * 文档导航决策。
     *
     * 在 GRAPH_ONLY 或 GRAPH_THEN_EVIDENCE 模式下，记录要查询哪个文档的
     * 哪个章节、哪个编号项等导航信息。
     *
     * @see DocumentNavigationDecision
     */
    private DocumentNavigationDecision navigationDecision;

    /**
     * 历史压缩是否已应用。
     *
     * true 表示本轮对话已经触发了历史压缩（把早期对话压缩成摘要）。
     * 用于防止重复压缩。
     */
    private boolean historyCompressionApplied;

    /**
     * 历史压缩覆盖到的最新交换 ID。
     *
     * 记录压缩到了哪一轮对话（exchange = 一轮用户问 + 助手答），
     * 下次压缩时从这个位置之后开始。
     */
    private Long historyCoveredExchangeId;

    /**
     * 历史压缩覆盖的交换数量。
     *
     * 本次压缩处理了多少轮对话。
     */
    private Integer historyCoveredExchangeCount;

    /**
     * 历史压缩次数。
     *
     * 记录到目前为止触发了多少次历史压缩。
     */
    private Integer historyCompressionCount;

    /**
     * 当前日期。
     *
     * 用于时间敏感型问题（例如"今天的新闻"），让模型知道"今天"是哪天。
     */
    private LocalDate currentDate;

    /**
     * 当前日期的文本表示。
     *
     * 例如 "2026-05-27"，方便直接放入 Prompt。
     */
    private String currentDateText;

    /**
     * 是否需要最新搜索。
     *
     * true 表示用户问题涉及最新信息（如"今天"、"最新"等关键词），
     * 检索时应该优先考虑时效性。
     */
    private boolean requiresFreshSearch;

    /**
     * 是否需要日期锚定。
     *
     * true 表示在 Prompt 中需要明确告诉模型当前日期，
     * 避免模型用训练数据中的旧日期回答时间敏感问题。
     */
    private boolean requiresCurrentDateAnchoring;

    /**
     * 选中的文档 ID。
     *
     * 路由阶段确定的用户最可能在问的知识库文档 ID。
     * 检索时会优先在这个文档范围内搜索。
     */
    private Long selectedDocumentId;

    /**
     * 选中的文档名称。
     *
     * 与 #selectedDocumentId 对应的文档名称，用于日志和调试。
     */
    private String selectedDocumentName;

    /**
     * 选中的任务 ID。
     *
     文档可能关联一个任务（task），此字段记录任务 ID。
     */
    private Long selectedTaskId;

    /**
     * 检索涉及的文档 ID 列表。
     *
     * 可能有多个文档参与检索（例如跨文档搜索），此字段记录所有涉及的文档 ID。
     */
    @Builder.Default
    private List<Long> retrievalDocumentIds = new ArrayList<>();

    /**
     * 检索涉及的任务 ID 列表。
     *
     * 与 #retrievalDocumentIds 配套，记录关联的任务 ID。
     */
    @Builder.Default
    private List<Long> retrievalTaskIds = new ArrayList<>();

    /**
     * 澄清回复话术。
     *
     * 在 CLARIFICATION 模式下，系统返回给用户的澄清问题文本。
     * 例如"您是想问 A 文档还是 B 文档？"
     *
     * @see ai.knowhub.chat.rag.executor.ClarificationExecutor
     */
    private String clarificationReply;

    /**
     * 澄清选项列表。
     *
     * 在澄清模式下，系统可能提供多个选项供用户选择，
     * 例如 ["文档A", "文档B", "文档C"]。
     */
    @Builder.Default
    private List<String> clarificationOptions = new ArrayList<>();

    /**
     * 澄清原因。
     *
     * 解释为什么需要澄清（例如"候选文档分数过于接近"），
     * 用于调试和日志记录。
     */
    private String clarificationReason;

    /**
     * 无证据时的兜底回复话术。
     *
     * 当检索引擎没有找到足够相关的证据时，返回此话术给用户。
     * 避免大模型在没有依据的情况下编造答案。
     */
    private String noEvidenceReply;
}
