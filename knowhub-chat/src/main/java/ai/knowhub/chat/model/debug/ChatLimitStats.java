package ai.knowhub.chat.model.debug;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【聊天限制统计】
 *
 * 作用：记录一次对话过程中模型调用和工具调用的次数限制使用情况。
 * 用于防止 AI Agent 在一次对话中消耗过多资源（如无限循环调用工具）。
 *
 * 所属架构位置：属于 Agent 执行控制层的资源限制机制。
 * 在 ReAct Agent 模式下，AI 可能会多轮调用工具，需要设置上限避免失控。
 *
 * 设计模式说明：「统计信息对象」模式，用于记录和展示资源使用情况。
 * 涉及两个维度的限制：
 * 1. Run Limit（运行限制）—— 单次 Agent 运行的调用上限
 * 2. Thread Limit（线程限制）—— 并发线程级别的调用上限
 *
 * @author knowhub
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatLimitStats {

    /**
     * 已使用的模型调用次数（modelCallsUsed）
     * 记录本次对话中已经调用了多少次大语言模型（LLM）。
     */
    private Integer modelCallsUsed;

    /**
     * 模型调用的运行限制（modelCallsRunLimit）
     * 单次 Agent 运行中允许的最大模型调用次数。
     */
    private Integer modelCallsRunLimit;

    /**
     * 模型调用的线程限制（modelCallsThreadLimit）
     * 线程级别（并发维度）的模型调用次数上限。
     */
    private Integer modelCallsThreadLimit;

    /**
     * 已使用的工具调用次数（toolCallsUsed）
     * 记录本次对话中已经调用了多少次外部工具（如搜索、数据库查询等）。
     */
    private Integer toolCallsUsed;

    /**
     * 工具调用的运行限制（toolCallsRunLimit）
     * 单次 Agent 运行中允许的最大工具调用次数。
     */
    private Integer toolCallsRunLimit;

    /**
     * 工具调用的线程限制（toolCallsThreadLimit）
     * 线程级别的工具调用次数上限。
     */
    private Integer toolCallsThreadLimit;

    /**
     * 是否触发了限制（limitTriggered）
     * 标识本次对话是否因为达到调用上限而被强制终止。
     */
    private boolean limitTriggered;

    /**
     * 触发限制的原因（limitReason）
     * 如果触发了限制，记录具体原因，如 "模型调用次数超过上限" 等。
     */
    private String limitReason;
}
