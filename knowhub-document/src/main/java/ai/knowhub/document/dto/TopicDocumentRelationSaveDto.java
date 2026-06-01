package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 主题-文档关联保存DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"建立主题与文档的关联关系"请求时所需的参数。
 * 在RAG系统中，将文档关联到知识主题后，系统在检索时可以根据主题精准定位相关文档，
 * 提高检索的准确性和相关性。
 *
 * 使用场景：管理端在主题文档关联管理页面，将某个文档关联到某个主题时使用。
 * 关联时可以指定关联的相关度分数、关联来源和关联原因等附加信息。
 *
 * 关联关系存储在数据库的topic_document_relation表中，
 * 关联后该文档在该主题下的检索权重会受到影响（relationScore字段）。
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class TopicDocumentRelationSaveDto {

    /**
     * 知识主题编码
     *
     * 要关联的知识主题的唯一编码。
     */
    private String topicCode;

    /**
     * 文档ID
     *
     * 要关联的文档的唯一标识。
     */
    private String documentId;

    /**
     * 关联分数
     *
     * 该文档与该主题的关联相关度分数。分数越高表示文档与主题越相关，
     * 在检索时可能获得更高的排名权重。使用String类型，
     * 后端可能需要转换为数值类型进行存储和计算。
     */
    private String relationScore;

    /**
     * 关联来源
     *
     * 记录该关联关系的创建来源。例如"人工关联"、"AI推荐"、"系统自动关联"等。
     * 用于区分关联是由人工操作还是系统自动建立的。
     */
    private String relationSource;

    /**
     * 关联原因
     *
     * 建立该关联关系的原因说明。例如"该文档详细介绍了Spring Boot的配置方法，
     * 与Spring Boot主题高度相关"。有助于后续维护和审计。
     */
    private String reason;

    /**
     * 操作人ID
     *
     * 执行保存操作的用户标识，用于记录操作日志。
     */
    private String operatorId;
}
