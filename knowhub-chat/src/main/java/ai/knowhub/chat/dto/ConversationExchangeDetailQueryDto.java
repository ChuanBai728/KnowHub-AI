package ai.knowhub.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会话交换详情查询 DTO。
 *
 * 该类用于封装查询某次"对话交换"（Exchange）详情的请求参数。
 * 在本系统中，一次"交换"（Exchange）指的是用户提问和 AI 回答的完整配对，
 * 一个会话（Conversation）可以包含多次交换。
 *
 * 在架构中的角色
 * 作为查询请求的参数载体，传递给控制器中的详情查询接口。
 * 通过会话 ID + 交换 ID 的组合，唯一定位到某次具体的对话交换记录。
 *
 * Lombok 注解说明
 * 
 *   @Data - 自动生成 getter/setter/toString/equals/hashCode 方法
 * 
 */
@Data
public class ConversationExchangeDetailQueryDto {

    /**
     * 会话 ID，标识所属的对话会话。
     *
     * 每个用户发起的聊天会话都有一个唯一的 conversationId，
     * 用于将多次交换（Exchange）关联到同一个会话上下文中。
     */
    @NotBlank(message = "conversationId 不能为空")
    private String conversationId;

    /**
     * 交换 ID（exchangeId），标识会话中的某一次具体交换。
     *
     * 每次用户提问 + AI 回答构成一次交换，exchangeId 是该次交换的唯一标识。
     * 通过 conversationId + exchangeId 可以精确定位到某次对话的详细信息，
     * 包括检索结果、推理过程、工具调用痕迹等。
     */
    @NotBlank(message = "exchangeId 不能为空")
    private String exchangeId;
}
