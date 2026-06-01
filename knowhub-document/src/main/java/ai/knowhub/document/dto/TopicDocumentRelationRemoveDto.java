package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 主题-文档关联移除DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"解除主题与文档的关联关系"请求时所需的参数。
 * 在RAG系统中，知识主题和文档之间存在关联关系（存储在topic_document_relation表中），
 * 本DTO用于移除（删除）某个主题与某个文档之间的关联。
 *
 * 使用场景：管理端在主题文档关联管理页面，取消某个文档与主题的关联时使用。
 * 注意：移除关联不会删除文档本身，只是解除它们之间的关系。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class TopicDocumentRelationRemoveDto {

    /**
     * 知识主题编码
     *
     * 要解除关联的知识主题的唯一编码。
     */
    private String topicCode;

    /**
     * 文档ID
     *
     * 要解除关联的文档的唯一标识。
     */
    private String documentId;

    /**
     * 操作人ID
     *
     * 执行移除操作的用户标识，用于记录操作日志和审计追踪。
     */
    private String operatorId;
}
