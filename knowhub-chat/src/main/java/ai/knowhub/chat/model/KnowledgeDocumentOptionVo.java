package ai.knowhub.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【知识文档选项展示】
 *
 * 作用：表示可供用户选择的知识文档信息。
 * 在「文档对话模式」下，用户可以选择特定文档与 AI 进行对话，
 * 此展示用于在前端展示可选文档列表。
 *
 * 所属架构位置：属于 RAG（检索增强生成）模块的知识库管理部分，
 * 作为文档选择器（Document Picker）的数据模型。
 *
 * 设计模式说明：「返回值对象（VO）」模式，仅包含文档的基本展示信息，
 * 用于前端下拉选择框或文档列表的渲染。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentOptionVo {

    /**
     * 文档 ID（documentId）
     * 唯一标识知识库中的一篇文档。
     */
    private String documentId;

    /**
     * 文档名称（documentName）
     * 文档的显示名称，通常是文件名或文档标题。
     */
    private String documentName;

    /**
     * 知识范围名称（knowledgeScopeName）
     * 文档所属的知识范围/知识域名称，
     * 例如："产品文档"、"技术手册"、"FAQ" 等。
     */
    private String knowledgeScopeName;

    /**
     * 业务分类（businessCategory）
     * 文档所属的业务类别，用于文档的分类管理。
     */
    private String businessCategory;

    /**
     * 文档标签（documentTags）
     * 文档的标签信息，多个标签之间通常以逗号分隔。
     * 用于文档的筛选和分类。
     */
    private String documentTags;
}
