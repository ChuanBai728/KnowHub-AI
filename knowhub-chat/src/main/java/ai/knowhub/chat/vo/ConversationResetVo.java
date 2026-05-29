package ai.knowhub.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话重置结果返回值对象（VO）。
 *
 * 该类封装了"重置/清空会话"操作的执行结果，返回给前端用于展示操作结果详情。
 * 当用户选择重置某个会话时，系统会清除该会话的所有历史数据，
 * 该 VO 记录了清除的数据统计和状态信息。
 *
 * 在架构中的角色
 * 属于 VO（Value Object）层，是后端返回给前端的响应数据载体。
 * 与 DTO（前端 -> 后端）方向相反，VO 是后端 -> 前端的数据传输对象。
 * 该对象会被包装在 ApiResponse<ConversationResetVo> 中返回。
 *
 * Lombok 注解说明
 * 
 *   @Data - 自动生成 getter/setter/toString/equals/hashCode 方法
 *   @NoArgsConstructor - 自动生成无参构造方法
 *   @AllArgsConstructor - 自动生成包含所有字段的构造方法
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResetVo {

    /**
     * 被重置的会话 ID。
     *
     * 标识哪个会话被执行了重置操作，方便前端确认操作目标。
     */
    private String conversationId;

    /**
     * 是否成功停止了正在运行的任务。
     *
     * 在重置会话前，如果该会话有正在运行的 Agent 任务（如 LLM 推理中），
     * 系统会先尝试停止该任务。此字段表示停止操作是否成功。
     */
    private boolean stoppedRunningTask;

    /**
     * 已删除的对话记录（Dialogue）数量。
     *
     * 重置操作会清除该会话下的所有单条消息记录（包括用户消息、AI 回复等），
     * 此字段记录了删除的总条数。
     */
    private int removedDialogueCount;

    /**
     * 已删除的交换记录（Exchange）数量。
     *
     * 重置操作会清除该会话下的所有对话交换（Exchange）记录，
     * 此字段记录了删除的交换数量。
     */
    private int removedExchangeCount;

    /**
     * 已删除的图检查点（Checkpoint）数量。
     *
     * 重置操作会清除该会话关联的所有图执行检查点数据，
     * 此字段记录了删除的检查点数量。
     */
    private int removedCheckpointCount;

    /**
     * 操作结果提示消息。
     *
     * 返回给前端展示的操作结果描述，例如"会话已重置"或"会话重置完成，共清除 X 条记录"。
     */
    private String message;
}
