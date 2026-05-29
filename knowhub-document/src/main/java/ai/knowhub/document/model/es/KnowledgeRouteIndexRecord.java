package ai.knowhub.document.model.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识路由索引记录（Knowledge Route Index Record）
 *
 * 【类的作用】
 * 映射 Elasticsearch 中的知识路由索引记录。
 * 每个实例代表一个可被路由命中的知识实体（文档、范围、主题），
 * 用于在用户提问时快速判断应该检索哪些知识库范围和文档。
 *
 * 【在架构中的角色】
 * 属于 RAG 流程中"路由决策"阶段的核心数据模型：
 * 1. 系统在初始化或文档更新时，为每个知识实体创建路由索引记录
 * 2. 用户提问时，检索引擎通过语义匹配找到相关的路由记录
 * 3. 路由引擎根据匹配结果决定检索哪些文档（见 KnowledgeRouteDecision）
 *
 * 【路由层级结构】
 * 系统的知识库采用三层路由结构：
 * - Scope（范围）：最粗粒度，例如"人力资源"、"财务管理"
 * - Topic（主题）：中等粒度，例如"考勤管理"、"薪酬福利"
 * - Document（文档）：最细粒度，例如"2024年考勤制度.docx"
 *
 * 【entityType 字段的作用】
 * 通过 entityType 区分同一条记录代表的是哪个层级的实体，
 * 使得一个 ES 索引可以同时存储三个层级的路由数据。
 *
 * 【设计模式】
 * - 多态索引模式：通过 entityType 字段区分不同类型的实体，
 *   在同一个 ES 索引中存储多种类型的数据，简化索引管理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRouteIndexRecord {

    /**
     * 路由记录唯一标识
     * 由实体类型和实体编码组合生成，确保全局唯一。
     * 同时也是 Elasticsearch 文档的 _id 字段。
     */
    private String routeId;

    /**
     * 实体类型
     * 标识该路由记录代表的知识实体层级：
     * - "SCOPE"：知识范围（最粗粒度）
     * - "TOPIC"：主题（中等粒度）
     * - "DOCUMENT"：文档（最细粒度）
     */
    private String entityType;

    /**
     * 实体编码
     * 该知识实体的唯一编码标识，
     * 对于 SCOPE 类型是 scopeCode，对于 TOPIC 类型是 topicCode，
     * 对于 DOCUMENT 类型是 documentId。
     */
    private String entityCode;

    /**
     * 文档 ID
     * 当 entityType 为 "DOCUMENT" 时，记录对应的文档 ID。
     * 对于 SCOPE 和 TOPIC 类型，此字段可能为 null。
     */
    private Long documentId;

    /**
     * 知识范围编码
     * 该实体所属的知识范围编码。
     */
    private String scopeCode;

    /**
     * 知识范围名称
     * scopeCode 的人类可读形式，用于路由结果展示。
     */
    private String scopeName;

    /**
     * 主题编码
     * 该实体所属的主题编码。
     * 对于 SCOPE 类型的记录，此字段可能为 null。
     */
    private String topicCode;

    /**
     * 主题名称
     * topicCode 的人类可读形式。
     */
    private String topicName;

    /**
     * 文档名称
     * 该实体关联的文档名称。
     */
    private String documentName;

    /**
     * 业务分类
     * 该实体所属的业务分类标签。
     */
    private String businessCategory;

    /**
     * 显示名称
     * 该知识实体的友好显示名称，
     * 用于路由结果的前端展示。
     */
    private String displayName;

    /**
     * 描述文本
     * 对该知识实体的详细描述，
     * 用于语义匹配时增加上下文信息，提高路由准确率。
     */
    private String descriptionText;

    /**
     * 别名文本
     * 该知识实体的别名或同义词，
     * 例如"HR"的别名可以是"人事部"、"人力资源部"，
     * 用于扩大语义匹配的覆盖范围。
     */
    private String aliasesText;

    /**
     * 示例文本
     * 该知识实体的典型问题示例，
     * 例如对于"考勤管理"，示例可以是"如何请假"、"迟到怎么算"，
     * 用于通过示例匹配提高路由准确率。
     */
    private String examplesText;

    /**
     * 摘要文本
     * 该知识实体的内容摘要，
     * 用于语义匹配时提供概要信息。
     */
    private String summaryText;

    /**
     * 路由文本
     * 综合了 displayName、descriptionText、aliasesText 等信息的合并文本，
     * 是 Elasticsearch 中被索引和搜索的核心字段。
     */
    private String routeText;

    /**
     * 实体术语列表
     * 该知识实体关联的关键词术语，
     * 用于精确匹配和过滤。
     */
    @Builder.Default
    private List<String> entityTerms = new ArrayList<>();

    /**
     * 标签列表
     * 该知识实体的标签，用于多维度过滤。
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
