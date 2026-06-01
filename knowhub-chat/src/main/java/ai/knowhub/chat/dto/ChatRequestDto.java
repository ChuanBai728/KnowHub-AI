package ai.knowhub.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天请求数据传输对象（DTO）。
 *
 * 该类用于封装前端发送到 /api/v1/chat/stream 等聊天接口的请求参数。
 * 当用户发起一次新的聊天对话时，前端会将用户问题、会话 ID、聊天模式等信息
 * 封装到该对象中，然后通过 HTTP 请求传递给后端控制器（Controller）。
 *
 * 在架构中的角色
 * 属于 DTO（Data Transfer Object）层，位于控制器（Controller）和业务逻辑（Service）之间，
 * 负责接收并校验来自前端的请求数据。使用 Jakarta Bean Validation 进行参数校验。
 *
 * 设计模式
 * 采用 DTO 模式，将前端请求参数与内部业务模型分离，提高安全性和可维护性。
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
public class ChatRequestDto {

    /**
     * 用户提出的问题/消息内容。
     *
     * 这是用户在聊天界面输入的文本，是 AI Agent 处理的核心输入。
     * 使用 @NotBlank 注解确保该字段不能为空或纯空白字符串。
     */
    @NotBlank(message = "question 不能为空")
    private String question;

    /**
     * 会话 ID（conversationId），用于标识一个完整的对话会话。
     *
     * 如果为空，则表示开始一次全新的对话，后端会自动创建新的会话 ID；
     * 如果不为空，则表示在已有会话中继续对话，后端会加载该会话的历史记录。
     */
    private String conversationId;

    /**
     * 聊天模式（chatMode），决定了 AI Agent 使用哪种执行策略来处理用户请求。
     *
     * 不同的聊天模式对应不同的 ConversationExecutor 策略实现，例如：
     * 
     *   React Agent 模式 - 使用 ReAct（Reasoning + Acting）策略
     *   Graph Only 模式 - 仅使用知识图谱进行检索
     *   Clarification 模式 - 先澄清用户意图再回答
     * 
     * 使用 @NotBlank 注解确保该字段必须指定。
     */
    @NotBlank(message = "chatMode 不能为空")
    private String chatMode;

    /**
     * 用户选中的文档 ID（可选）。
     *
     * 当用户在界面上选择了一个特定文档进行提问时，
     * 该字段会携带所选文档的标识符，后端会据此限定检索范围，
     * 仅在该文档内进行 RAG（检索增强生成）检索。
     */
    private String selectedDocumentId;
}
