package ai.knowhub.chat.model;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.enums.ChatQueryMode;

/**
 * 【会话展示】
 *
 * 作用：展示一个完整的对话会话（Session）信息，包括会话状态、消息列表、
 * 记忆摘要等。这是前端展示「对话窗口」所需的核心数据模型。
 *
 * 所属架构位置：属于会话管理模块的顶层展示，聚合了会话的所有关键信息。
 * 一个会话包含多次「交换（Exchange）」，代表用户与 AI 的持续对话。
 *
 * 设计模式说明：「返回值对象（VO）」聚合模式，将多个子模型聚合为一个完整的会话展示。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionVo {

    /**
     * 会话 ID（conversationId）
     * 唯一标识一个对话会话，通常使用 UUID 或百度 UID生成。
     */
    private String conversationId;

    /**
     * 是否正在运行（running）
     * 标识当前会话是否有正在进行的 AI 对话处理。
     * 前端可根据此字段显示「加载中」状态。
     */
    private boolean running;

    /**
     * 检查点数量（checkpointCount）
     * 记录会话中保存的 LangGraph 检查点数量。
     * 检查点用于支持对话的中断恢复和状态回溯。
     */
    private int checkpointCount;

    /**
     * 消息总数（messageCount）
     * 会话中的消息总数（包括用户消息和 AI 回复）。
     */
    private int messageCount;

    /** 最近一条用户消息的内容 */
    private String latestUserMessage;

    /** 最近一条 AI 回复的内容 */
    private String latestAssistantMessage;

    /** 最近一次交换的 ID */
    private Long latestExchangeId;

    /**
     * 最近一次轮次的状态（latestTurnStatus）
     * 如 "COMPLETED"（已完成）、"FAILED"（失败）等。
     */
    private String latestTurnStatus;

    /** 最近一次轮次的错误信息（如果失败） */
    private String latestTurnErrorMessage;

    /**
     * 对话模式（chatMode）
     * 标识当前使用的对话查询模式，如：
     * - 普通对话模式
     * - RAG 增强模式
     * - Agent 模式 等
     */
    private ChatQueryMode chatMode;

    /**
     * 选中的文档 ID（selectedDocumentId）
     * 在「文档对话模式」下，标识用户选择与哪个特定文档进行对话。
     */
    private String selectedDocumentId;

    /** 选中的文档名称 */
    private String selectedDocumentName;

    /** 会话创建时间 */
    private Instant createdAt;

    /** 会话最后更新时间 */
    private Instant updatedAt;

    /**
     * 对话交换列表（exchanges）
     * 包含该会话中所有的问答交换记录，按时间顺序排列。
     */
    private List<ConversationExchangeVo> exchanges;

    /**
     * 会话记忆摘要（memorySummary）
     * 当对话历史过长时，系统会自动压缩早期历史为摘要，
     * 以在有限的上下文窗口内保留关键信息。
     */
    private ConversationMemorySummaryVo memorySummary;
}
