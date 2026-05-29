package ai.knowhub.document.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识文档描述符（Knowledge Document Descriptor）
 *
 * 【类的作用】
 * 作为知识库中文档的"名片"，携带文档的核心元数据信息，
 * 用于文档路由（Routing）、检索过滤、结果展示等场景。
 *
 * 【在架构中的角色】
 * 属于文档管理的模型层，在 RAG 流程中被广泛使用：
 * - 文档路由阶段：根据 documentName、businessCategory 等字段判断用户问题应该检索哪些文档
 * - 检索阶段：作为过滤条件传递给检索引擎
 * - 结果展示：提供文档名称等信息用于前端展示
 *
 * 【Lombok 注解说明】
 * - @Data：自动生成 getter/setter/toString/equals/hashCode
 * - @NoArgsConstructor：生成无参构造器（反序列化时需要）
 * - @AllArgsConstructor：生成全参构造器（方便快速创建对象）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentDescriptor {

    /**
     * 文档唯一标识 ID
     * 数据库中文档表的主键，用于关联检索结果与源文档。
     */
    private Long documentId;

    /**
     * 文档名称
     * 例如"员工手册"、"产品说明书"、"财务管理制度"等，
     * 用于文档路由时的语义匹配和结果展示。
     */
    private String documentName;

    /**
     * 最近一次索引构建任务 ID
     * 记录该文档最近一次成功完成索引构建的任务 ID，
     * 用于判断文档索引是否为最新状态，以及定位索引数据。
     */
    private Long lastIndexTaskId;

    /**
     * 知识范围编码
     * 标识文档所属的知识域，例如"HR"（人力资源）、"FIN"（财务）等，
     * 是路由决策的重要依据之一。
     */
    private String knowledgeScopeCode;

    /**
     * 知识范围名称
     * knowledgeScopeCode 的人类可读形式，例如"人力资源"、"财务管理"，
     * 用于展示和日志记录。
     */
    private String knowledgeScopeName;

    /**
     * 业务分类
     * 文档的二级分类标签，例如"考勤制度"、"报销流程"等，
     * 比 knowledgeScopeCode 更细粒度，用于精确路由。
     */
    private String businessCategory;

    /**
     * 文档标签（JSON 字符串或逗号分隔）
     * 文档的关键词标签，例如"政策,2024,正式版"，
     * 用于多维度检索过滤。
     */
    private String documentTags;
}
