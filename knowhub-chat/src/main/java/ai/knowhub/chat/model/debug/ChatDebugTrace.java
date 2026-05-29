package ai.knowhub.chat.model.debug;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.chat.rag.model.DocumentNavigationDecision;
import ai.knowhub.enums.ChatQueryMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 【聊天调试追踪信息】
 *
 * 作用：记录一次对话处理过程中的全链路调试信息，是系统可观测性的核心模型。
 * 包含从问题输入到回答输出的每个关键环节的中间状态，帮助开发者排查问题和优化效果。
 *
 * 所属架构位置：属于调试追踪（Debug Trace）子系统，贯穿整个对话处理流程。
 * 当用户请求调试模式时，此对象会被完整填充并通过 ConversationExchangeVo 返回给前端。
 *
 * 设计模式说明：
 * 1. 「建造者模式（Builder Pattern）」—— 使用 Lombok @Builder 注解，
 *    支持链式构建复杂的追踪对象。
 * 2. 「快照模式（Snapshot Pattern）」—— 记录对话处理过程中的关键状态快照。
 *
 * @author knowhub
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDebugTrace {

    /**
     * 执行模式（executionMode）
     * 标识本次对话使用了哪种执行策略，如：
     * - "ReactAgent"：ReAct（Reasoning + Acting）Agent 模式
     * - "GraphOnly"：纯图执行模式
     * - "GraphThenEvidence"：图执行后证据补充模式
     * - "Clarification"：澄清模式（需要追问用户）
     */
    private String executionMode;

    /**
     * 对话模式（chatMode）
     * 标识用户选择的对话查询模式，如普通对话、RAG增强等。
     */
    private ChatQueryMode chatMode;

    /**
     * 原始问题（originalQuestion）
     * 用户发送的原始问题文本，未经任何处理。
     */
    private String originalQuestion;

    /**
     * 改写后的问题（rewriteQuestion）
     * 经过查询改写（Query Rewriting）处理后的问题文本。
     * 查询改写可以优化检索效果，如补充上下文、纠正错别字等。
     */
    private String rewriteQuestion;

    /**
     * 改写后的子问题列表（rewriteSubQuestions）
     * 当一个问题包含多个子问题时，改写阶段会将其拆分。
     * 例如："Python和Java的区别是什么？各自的优缺点？" 可能被拆分为两个子问题。
     *
     * @Builder.Default 设置默认值为空列表，避免空指针。
     */
    @Builder.Default
    private List<String> rewriteSubQuestions = new ArrayList<>();

    /**
     * 检索用问题（retrievalQuestion）
     * 最终用于 RAG 检索的问题文本。可能与改写后的问题相同，
     * 也可能经过进一步处理（如 HyDE 生成假设性文档）。
     *
     * @JsonAlias 允许 JSON 反序列化时使用 "rewrittenQuestion" 作为别名，
     * 兼容不同版本的字段命名。
     */
    @JsonAlias("rewrittenQuestion")
    private String retrievalQuestion;

    /**
     * Agent 用问题（agentQuestion）
     * 传递给 ReAct Agent 的最终问题文本，可能与检索用问题不同。
     */
    private String agentQuestion;

    /**
     * 文档导航决策（navigationDecision）
     * 记录系统对用户问题的文档路由决策，判断应该在哪个知识范围中检索。
     */
    private DocumentNavigationDecision navigationDecision;

    /**
     * 历史摘要（historySummary）
     * 对话历史的压缩摘要，用于在有限的上下文窗口内保留早期对话的关键信息。
     */
    private String historySummary;

    /**
     * 长期记忆摘要（longTermSummary）
     * 跨会话的长期记忆摘要，记录用户的重要偏好和历史交互信息。
     */
    private String longTermSummary;

    /**
     * 近期历史对话记录（recentHistoryTranscript）
     * 最近几次对话的原始记录（未经压缩），作为上下文提供给 AI。
     */
    private String recentHistoryTranscript;

    /**
     * 回答用近期对话记录（answerRecentTranscript）
     * 实际注入到回答生成 Prompt 中的近期对话记录。
     */
    private String answerRecentTranscript;

    /**
     * 回答用历史上下文（answerHistoryContext）
     * 实际注入到回答生成 Prompt 中的历史上下文信息。
     */
    private String answerHistoryContext;

    /**
     * 是否为历史追问（answerHistoryFollowUpQuestion）
     * 判断用户当前问题是否是对之前对话的追问（Follow-up Question）。
     */
    private boolean answerHistoryFollowUpQuestion;

    /**
     * 是否已应用历史压缩（historyCompressionApplied）
     * 标识对话历史是否经过了摘要压缩处理。
     */
    private boolean historyCompressionApplied;

    /** 历史压缩覆盖的最大交换 ID */
    private Long historyCoveredExchangeId;

    /** 历史压缩覆盖的交换数量 */
    private Integer historyCoveredExchangeCount;

    /** 历史压缩执行次数 */
    private Integer historyCompressionCount;

    /**
     * 当前日期文本（currentDateText）
     * 当前日期的文本表示，用于时间敏感型问题的处理。
     */
    private String currentDateText;

    /**
     * 是否需要新鲜搜索（requiresFreshSearch）
     * 判断用户问题是否需要最新信息（如天气、股价、新闻等）。
     * 如果需要，系统会优先使用网络搜索而非知识库检索。
     */
    private boolean requiresFreshSearch;

    /**
     * 是否需要日期锚定（requiresCurrentDateAnchoring）
     * 判断是否需要在搜索查询中加入当前日期，
     * 以确保搜索结果的时效性。
     */
    private boolean requiresCurrentDateAnchoring;

    /**
     * 检索用子问题列表（retrievalSubQuestions）
     * 实际用于检索的子问题列表。
     *
     * @JsonAlias 允许 JSON 反序列化时使用 "subQuestions" 作为别名。
     */
    @JsonAlias("subQuestions")
    @Builder.Default
    private List<String> retrievalSubQuestions = new ArrayList<>();

    /** 选中的文档 ID（文档对话模式下） */
    private Long selectedDocumentId;

    /** 选中的任务 ID */
    private Long selectedTaskId;

    /**
     * 检索备注列表（retrievalNotes）
     * 记录检索过程中的重要提示和决策原因。
     */
    @Builder.Default
    private List<String> retrievalNotes = new ArrayList<>();

    /**
     * 使用的检索通道列表（usedChannels）
     * 记录本次检索使用了哪些通道，如 "keyword"、"vector"、"web-search" 等。
     */
    @Builder.Default
    private List<String> usedChannels = new ArrayList<>();

    /**
     * 工具调用追踪列表（toolTraces）
     * 记录每次工具调用的详细信息：工具名、输入、输出、耗时等。
     */
    @Builder.Default
    private List<ChatToolTrace> toolTraces = new ArrayList<>();

    /**
     * 模型使用追踪列表（modelUsageTraces）
     * 记录每次模型调用的 Token 消耗、耗时等信息，用于成本监控。
     */
    @Builder.Default
    private List<ChatModelUsageTrace> modelUsageTraces = new ArrayList<>();

    /**
     * 限制统计（limitStats）
     * 记录模型调用和工具调用的次数限制使用情况，
     * 防止单次对话消耗过多资源。
     */
    private ChatLimitStats limitStats;

    /**
     * RAG 系统提示词（ragSystemPrompt）
     * 最终发送给 AI 模型的系统提示词（System Prompt）。
     */
    private String ragSystemPrompt;

    /**
     * RAG 用户提示词（ragUserPrompt）
     * 最终发送给 AI 模型的用户提示词（User Prompt）。
     */
    private String ragUserPrompt;

    /**
     * 无证据回复（noEvidenceReply）
     * 当 RAG 检索未找到相关证据时，系统生成的默认回复内容。
     */
    private String noEvidenceReply;
}
