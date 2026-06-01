package ai.knowhub.document.dto;

import lombok.Data;

/**
 * 文档上传DTO（Data Transfer Object，数据传输对象）
 *
 * 作用：用于前端向后端传递"上传文档"请求时所需的参数。
 * 管理端上传文档到RAG知识库时，除了文档文件本身，还需要提供文档的元数据信息，
 * 本DTO封装了这些元数据。
 *
 * 使用场景：管理端在文档上传页面填写文档信息并选择文件后，点击"上传"按钮时使用。
 * 文件本身通常通过MultipartFile在Controller层单独接收，本DTO只封装附加的表单字段。
 *
 * 文档上传后的处理流程：上传 -> 策略规划 -> 策略确认 -> 索引构建 -> 可被检索
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class DocumentUploadDto {

    /**
     * 文档名称
     *
     * 上传的文档的名称，通常就是原始文件名，也可以由用户自定义修改。
     */
    private String documentName;

    /**
     * 操作人ID
     *
     * 执行上传操作的用户标识，用于记录操作日志和文档的所有者信息。
     */
    private String operatorId;

    /**
     * 知识域编码
     *
     * 文档所属的知识域（Knowledge Scope）的编码。知识域是对知识的分类体系，
     * 例如"技术文档"、"产品手册"、"FAQ"等。将文档归类到对应的知识域，
     * 有助于后续的精准检索。与 KnowledgeScopeSaveDto 中的scopeCode对应。
     */
    private String knowledgeScopeCode;

    /**
     * 知识域名称
     *
     * 文档所属知识域的显示名称，方便前端展示。虽然可以通过scopeCode查询得到，
     * 但这里冗余存储可以减少查询次数。
     */
    private String knowledgeScopeName;

    /**
     * 业务分类
     *
     * 文档的业务分类标签，用于进一步细分文档类型。
     * 例如在"技术文档"知识域下，还可以按"Java"、"Python"、"数据库"等业务分类。
     */
    private String businessCategory;

    /**
     * 文档标签
     *
     * 文档的标签信息，多个标签之间可能用逗号或其他分隔符连接。
     * 标签用于辅助文档的分类和检索，例如"入门"、"高级"、"FAQ"等。
     */
    private String documentTags;
}
