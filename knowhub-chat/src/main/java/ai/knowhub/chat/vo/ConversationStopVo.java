package ai.knowhub.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话停止结果返回值对象（VO）。
 *
 * 该类封装了"停止会话"操作的执行结果，返回给前端用于展示停止操作的状态。
 * 当用户在对话进行中点击"停止"按钮时，系统会尝试中断正在运行的 Agent 任务，
 * 该 VO 记录了停止操作的结果。
 *
 * 在架构中的角色
 * 属于 VO（Value Object）层，是停止操作的响应数据载体。
 * 该对象会被包装在 ApiResponse<ConversationStopVo> 中返回给前端，
 * 前端据此更新 UI 状态（如将"停止"按钮改为"已停止"）。
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
public class ConversationStopVo {

    /**
     * 被停止的会话 ID。
     *
     * 标识哪个会话被执行了停止操作，方便前端确认操作目标并更新对应 UI。
     */
    private String conversationId;

    /**
     * 是否成功停止。
     *
     * 如果为 true，表示 Agent 任务已被成功中断；
     * 如果为 false，表示停止失败（可能任务已经完成或不存在）。
     */
    private boolean stopped;

    /**
     * 操作结果提示消息。
     *
     * 返回给前端展示的操作结果描述，例如"已停止"或"任务已完成，无法停止"。
     */
    private String message;
}
