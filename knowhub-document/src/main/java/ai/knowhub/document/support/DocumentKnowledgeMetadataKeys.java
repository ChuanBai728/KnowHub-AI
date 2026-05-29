package ai.knowhub.document.support;

/**
 * 文档知识元数据键常量（Document Knowledge Metadata Keys）
 *
 * 【类的作用】
 * 定义了文档知识检索结果中所有元数据字段的键名常量。
 * 这些常量用于在向量检索结果的 metadata Map 中存取对应的值。
 *
 * 【在架构中的角色】
 * 属于 RAG（检索增强生成）流程中的元数据管理。当文档切片被向量化存储后，
 * 每个切片都附带元数据（如来源文档、章节路径、相关性分数等）。
 * 本类统一管理这些元数据的键名，避免硬编码字符串散落在代码各处。
 *
 * 【设计模式】
 * 工具类模式（Utility Class）：私有构造器防止实例化，所有字段都是 public static final 常量。
 */
public final class DocumentKnowledgeMetadataKeys {

    /**
     * 来源类型（Source Type）
     * 标识知识片段的来源方式，如正文、表格、列表等
     */
    public static final String SOURCE_TYPE = "sourceType";

    /**
     * 检索通道（Channel）
     * 标识知识片段是通过哪个检索通道获取的，如关键词检索、向量检索等
     */
    public static final String CHANNEL = "channel";

    /**
     * 相关性分数（Score）
     * 检索引擎返回的相关性评分，数值越高表示与查询越相关
     */
    public static final String SCORE = "score";

    /**
     * 文档ID（Document ID）
     * 知识片段所属文档的唯一标识
     */
    public static final String DOCUMENT_ID = "documentId";

    /**
     * 文档名称（Document Name）
     * 知识片段所属文档的名称
     */
    public static final String DOCUMENT_NAME = "documentName";

    /**
     * 任务ID（Task ID）
     * 生成该知识片段的处理任务ID，用于追溯处理过程
     */
    public static final String TASK_ID = "taskId";

    /**
     * 父块ID（Parent Block ID）
     * 知识片段所属父级文本块的ID
     */
    public static final String PARENT_BLOCK_ID = "parentBlockId";

    /**
     * 父块编号（Parent Block No）
     * 父级文本块在文档中的顺序编号
     */
    public static final String PARENT_BLOCK_NO = "parentBlockNo";

    /**
     * 切片ID（Chunk ID）
     * 知识片段（切片）的唯一标识
     */
    public static final String CHUNK_ID = "chunkId";

    /**
     * 切片编号（Chunk No）
     * 切片在文档中的顺序编号
     */
    public static final String CHUNK_NO = "chunkNo";

    /**
     * 章节路径（Section Path）
     * 切片在文档结构树中的路径，如 "第一章 > 第一节"
     */
    public static final String SECTION_PATH = "sectionPath";

    /**
     * 结构节点ID（Structure Node ID）
     * 切片关联的文档结构节点ID
     */
    public static final String STRUCTURE_NODE_ID = "structureNodeId";

    /**
     * 结构节点类型（Structure Node Type）
     * 切片关联的文档结构节点类型
     */
    public static final String STRUCTURE_NODE_TYPE = "structureNodeType";

    /**
     * 规范路径（Canonical Path）
     * 结构节点的标准化路径
     */
    public static final String CANONICAL_PATH = "canonicalPath";

    /**
     * 项目索引（Item Index）
     * 切片在列表中的序号
     */
    public static final String ITEM_INDEX = "itemIndex";

    /**
     * 知识范围编码（Knowledge Scope Code）
     * 知识片段所属的知识范围分类编码
     */
    public static final String KNOWLEDGE_SCOPE_CODE = "knowledgeScopeCode";

    /**
     * 知识范围名称（Knowledge Scope Name）
     * 知识片段所属的知识范围分类名称
     */
    public static final String KNOWLEDGE_SCOPE_NAME = "knowledgeScopeName";

    /**
     * 业务分类（Business Category）
     * 知识片段所属的业务分类
     */
    public static final String BUSINESS_CATEGORY = "businessCategory";

    /**
     * 文档标签（Document Tags）
     * 文档的标签信息，用于分类和筛选
     */
    public static final String DOCUMENT_TAGS = "documentTags";

    /**
     * 标题（Title）
     * 知识片段的标题
     */
    public static final String TITLE = "title";

    /**
     * URL 地址
     * 知识片段的来源 URL（适用于网页类知识）
     */
    public static final String URL = "url";

    /**
     * 工具名称（Tool Name）
     * 生成该知识片段的工具名称（适用于 Agent 工具调用场景）
     */
    public static final String TOOL_NAME = "toolName";

    /**
     * 原始片段（Original Snippet）
     * 检索到的原始文本片段
     */
    public static final String ORIGINAL_SNIPPET = "originalSnippet";

    /**
     * RRF 分数（Reciprocal Rank Fusion Score）
     * 使用 RRF（倒数排名融合）算法计算的综合排序分数
     * 用于合并多个检索通道的结果
     */
    public static final String RRF_SCORE = "rrfScore";

    /**
     * 私有构造器，防止实例化
     * 这是一个纯常量类，只需要通过类名访问静态常量
     */
    private DocumentKnowledgeMetadataKeys() {
    }
}
