package ai.knowhub.chat.model.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【会话记忆上下文】
 *
 * 作用：封装一次对话处理过程中所需的全部记忆/历史上下文信息。
 * 这是记忆管理模块的核心输出对象，包含经过组装的对话历史、摘要、近期记录等，
 * 直接注入到 AI 模型的 Prompt 中。
 *
 * 所属架构位置：属于会话记忆管理模块（Memory Management），
 * 是「上下文组装器（Context Assembler）」的输出产物。
 *
 * 设计模式说明：
 * 1. 「建造者模式（Builder）」—— 使用 @Builder 支持灵活构建。
 * 2. 「上下文对象模式（Context Object）」—— 将分散的记忆信息聚合为一个上下文对象，
 *    供下游的 Prompt 组装阶段使用。
 *
 * 关键概念说明：
 * - 对话记忆压缩（Memory Compression）：当对话历史超过上下文窗口限制时，
 *   系统会将早期对话压缩为摘要，保留关键信息的同时减少 Token 消耗。
 * - 近期对话记录（Recent Transcript）：保留最近几次对话的原始内容，
 *   因为近期对话对当前回答的影响最大。
 *
 * @author knowhub
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemoryContext {

    /**
     * 组装后的历史记录（assembledHistory）
     * 经过记忆管理模块组装后的完整历史上下文文本，
     * 直接作为 Prompt 的一部分注入到 AI 模型中。
     * 通常包含：长期摘要 + 近期对话记录的组合。
     */
    private String assembledHistory;

    /**
     * 长期摘要（longTermSummary）
     * 由 AI 生成的对话历史压缩摘要，替代早期的完整对话记录。
     * 包含对话目标、关键事实、用户偏好等结构化信息。
     */
    private String longTermSummary;

    /**
     * 近期对话记录（recentTranscript）
     * 最近几次对话的原始记录（未经压缩），格式通常为：
     * "用户：xxx\n助手：xxx\n用户：xxx\n助手：xxx"
     */
    private String recentTranscript;

    /**
     * 回答阶段使用的近期对话记录（answerRecentTranscript）
     * 专门用于回答生成阶段的近期对话记录，可能与 recentTranscript 不同，
     * 因为不同阶段对历史信息的需求可能不同。
     */
    private String answerRecentTranscript;

    /**
     * 摘要结构化载荷（summaryPayload）
     * 包含摘要的结构化数据：对话目标、稳定事实、用户偏好、
     * 已解决要点、待解决问题、检索提示等。
     */
    private ConversationSummaryPayload summaryPayload;

    /**
     * 已覆盖的最大交换 ID（coveredExchangeId）
     * 摘要覆盖到哪一次交换为止。比此 ID 更新的交换作为近期历史保留。
     */
    private Long coveredExchangeId;

    /**
     * 已覆盖的交换数量（coveredExchangeCount）
     * 摘要中包含了多少次问答交换的内容。
     */
    private Integer coveredExchangeCount;

    /**
     * 压缩次数（compressionCount）
     * 记录该会话已经执行了多少次摘要压缩操作。
     */
    private Integer compressionCount;

    /**
     * 是否已应用压缩（compressionApplied）
     * 标识本次记忆组装是否触发了新的摘要压缩。
     */
    private boolean compressionApplied;
}
