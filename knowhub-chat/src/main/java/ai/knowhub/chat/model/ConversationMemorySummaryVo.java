package ai.knowhub.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.chat.model.memory.ConversationSummaryPayload;

import java.time.Instant;

/**
 * 【会话记忆摘要展示】
 *
 * 作用：展示会话的长期记忆摘要信息。
 * 当对话历史过长时，系统会通过「摘要压缩」策略将历史对话压缩为摘要，
 * 以节省 Token 消耗并保留关键信息。
 *
 * 所属架构位置：属于会话记忆管理模块（Memory Management），
 * 对应 Spring AI 的 Conversation Memory 机制。
 *
 * 设计模式说明：「返回值对象（VO）」模式，将内部记忆状态转换为前端可展示的展示。
 * 摘要压缩是 AI 对话系统中常见的「上下文窗口管理」策略。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemorySummaryVo {

    /** 会话 ID */
    private String conversationId;

    /**
     * 是否已应用压缩（compressionApplied）
     * 标识当前会话是否已经触发过历史摘要压缩。
     */
    private boolean compressionApplied;

    /**
     * 已覆盖的最大交换 ID（coveredExchangeId）
     * 摘要覆盖到哪一次交换为止。比此 ID 更新的交换不在摘要中，而是作为近期历史保留。
     */
    private long coveredExchangeId;

    /**
     * 已覆盖的交换数量（coveredExchangeCount）
     * 摘要中包含了多少次问答交换的内容。
     */
    private int coveredExchangeCount;

    /**
     * 压缩次数（compressionCount）
     * 记录该会话已经执行了多少次摘要压缩操作。
     */
    private int compressionCount;

    /**
     * 摘要版本号（summaryVersion）
     * 每次重新生成摘要时版本号递增，用于判断摘要是否为最新版本。
     */
    private int summaryVersion;

    /**
     * 摘要文本（summaryText）
     * 由 AI 生成的对话历史摘要，用于替代早期的对话历史，
     * 作为上下文注入到后续的 Prompt 中。
     */
    private String summaryText;

    /**
     * 摘要结构化载荷（summaryPayload）
     * 除了纯文本摘要外，还包含结构化的信息：对话目标、稳定事实、用户偏好、
     * 已解决要点、待解决问题、检索提示等。
     */
    private ConversationSummaryPayload summaryPayload;

    /** 摘要源数据的最后编辑时间 */
    private Instant lastSourceEditTime;

    /** 摘要最后更新时间 */
    private Instant updatedAt;
}
