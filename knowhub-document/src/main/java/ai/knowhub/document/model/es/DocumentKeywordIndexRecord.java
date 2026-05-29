package ai.knowhub.document.model.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档关键词索引记录（Document Keyword Index Record）
 *
 * 【类的作用】
 * 映射 Elasticsearch 中的一个文档片段（chunk）索引记录。
 * 每个实例代表文档经过解析和分块后的一个内容块，
 * 用于 Elasticsearch 的全文关键词检索。
 *
 * 【在架构中的角色】
 * 属于 RAG 流程中"索引构建"阶段的数据模型：
 * 1. 文档上传后，经过解析（Parsing）拆分成多个内容块
 * 2. 每个内容块创建一条此记录，写入 Elasticsearch
 * 3. 用户提问时，检索引擎通过关键词匹配（BM25 等算法）查找相关记录
 *
 * 【与向量检索的关系】
 * 本类对应关键词检索通道（keyword retrieval channel）。
 * 系统同时支持向量检索和关键词检索，两种通道的结果会进行融合（Reciprocal Rank Fusion）。
 *
 * 【Elasticsearch 文档概念】
 * 在 Elasticsearch 中，"文档"（Document）是一条 JSON 记录，
 * 本类的实例会被序列化为 JSON 并写入 ES 索引。
 * 字段上的注解（如果有）会映射到 ES 的字段类型（text、keyword、integer 等）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentKeywordIndexRecord {

    /**
     * 内容块唯一标识
     * 由文档 ID + 块序号等组合生成，确保全局唯一。
     * 同时也是 Elasticsearch 文档的 _id 字段。
     */
    private String chunkId;

    /**
     * 所属文档 ID
     * 关联到知识库中的源文档，用于结果溯源。
     */
    private Long documentId;

    /**
     * 解析任务 ID
     * 标识该内容块是由哪次解析任务生成的，
     * 用于区分同一文档的不同版本索引。
     */
    private Long taskId;

    /**
     * 父级块 ID
     * 如果该内容块是从某个大块进一步拆分而来，
     * 此字段记录其父级块的 ID，用于层级关系追溯。
     */
    private Long parentBlockId;

    /**
     * 内容块序号
     * 该块在文档中的顺序编号，从 0 开始，
     * 用于检索结果的排序和上下文重建。
     */
    private Integer chunkNo;

    /**
     * 文档名称
     * 冗余存储文档名称，避免检索时需要关联查询，
     * 同时用于 ES 的高亮显示和结果展示。
     */
    private String documentName;

    /**
     * 章节路径
     * 该内容块在文档目录结构中的位置，
     * 例如"第一章/第2节"，用于结果的上下文展示。
     */
    private String sectionPath;

    /**
     * 结构节点 ID
     * 该内容块对应的文档结构树节点 ID，
     * 用于与 Neo4j 图数据库中的节点关联。
     */
    private Long structureNodeId;

    /**
     * 结构节点类型
     * 节点的类型标识，例如标题节点、段落节点、列表节点等，
     * 用于检索时的类型过滤。
     */
    private Integer structureNodeType;

    /**
     * 规范路径
     * 文档节点的标准化唯一路径标识，
     * 不依赖文档的目录编号格式，使用系统统一的路径表示。
     */
    private String canonicalPath;

    /**
     * 条目索引
     * 内容块在文档所有块中的全局顺序索引，
     * 用于排序和上下文片段的定位。
     */
    private Integer itemIndex;

    /**
     * 知识范围编码
     * 内容块所属的知识域编码，用于检索范围限定。
     */
    private String knowledgeScopeCode;

    /**
     * 知识范围名称
     * knowledgeScopeCode 的人类可读形式，用于结果展示。
     */
    private String knowledgeScopeName;

    /**
     * 业务分类
     * 内容块所属的业务分类，用于检索过滤。
     */
    private String businessCategory;

    /**
     * 文档标签列表
     * 内容块继承自其所属文档的标签，
     * 用于多维度检索过滤和分类。
     */
    @Builder.Default
    private List<String> documentTags = new ArrayList<>();

    /**
     * 内容块文本
     * 经过分块处理后的实际文本内容，
     * 是 Elasticsearch 中被索引和搜索的核心字段。
     * 在 ES 中通常映射为 text 类型，支持全文检索和分词。
     */
    private String chunkText;
}
