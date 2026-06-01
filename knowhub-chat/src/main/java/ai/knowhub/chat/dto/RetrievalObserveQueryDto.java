package ai.knowhub.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 检索观察查询 DTO。
 *
 * 该类用于封装查询 RAG 检索过程观察数据的请求参数。
 * 在本系统的 RAG（Retrieval-Augmented Generation，检索增强生成）流程中，
 * 每次对话交换都会产生检索结果、重排序结果等中间数据，
 * 该 DTO 用于查询这些中间过程的详细信息，便于调试和质量分析。
 *
 * 在架构中的角色
 * 作为检索观察查询接口的入参，支持用户查看某次对话交换中的
 * 检索过程细节（如检索了哪些文档、重排序分数等），是 RAG 流程透明化的关键。
 *
 * Lombok 注解说明
 * 
 *   @Data - 自动生成 getter/setter/toString/equals/hashCode 方法
 * 
 */
@Data
public class RetrievalObserveQueryDto {

    /**
     * 会话 ID，标识所属的对话会话。
     *
     * 用于定位到具体的会话，结合 exchangeId 查询该会话中某次交换的检索观察数据。
     */
    @NotBlank(message = "conversationId 不能为空")
    private String conversationId;

    /**
     * 交换 ID（exchangeId），标识会话中的某一次具体交换。
     *
     * 每次交换都有独立的检索过程，通过此字段可以查看特定交换的
     * 检索结果、文档片段、重排序分数等详细信息。
     * 注意：此处使用 @NotNull 而非 @NotBlank，
     * 说明该字段不允许为 null，但可以接受空字符串（与 ConversationExchangeDetailQueryDto 中的校验方式略有不同）。
     */
    @NotNull(message = "exchangeId 不能为空")
    private String exchangeId;
}
