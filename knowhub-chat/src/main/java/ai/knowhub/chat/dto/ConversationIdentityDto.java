package ai.knowhub.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会话身份标识 DTO。
 *
 * 该类仅包含一个会话 ID（conversationId），用于标识需要操作的对话会话。
 * 适用于那些只需要会话 ID 就能完成的操作，例如：
 * 
 *   重置/清空会话历史
 *   停止正在运行的会话任务
 *   查询会话状态
 * 在架构中的角色
 * 作为轻量级的请求参数对象，用于简单的会话级别操作，
 * 避免使用包含多余字段的重量级 DTO。
 *
 * 设计模式
 * 采用"单一职责"设计思想，该 DTO 仅负责承载会话标识信息，
 * 使接口参数更加清晰简洁。
 *
 * Lombok 注解说明
 * 
 *   @Data - 自动生成 getter/setter/toString/equals/hashCode 方法
 * 
 */
@Data
public class ConversationIdentityDto {

    /**
     * 会话 ID（conversationId），用于唯一标识一个对话会话。
     *
     * 该 ID 贯穿整个会话的生命周期，从创建到结束的所有操作都依赖此标识。
     * 使用 @NotBlank 注解确保不能为空。
     */
    @NotBlank(message = "conversationId 不能为空")
    private String conversationId;
}
