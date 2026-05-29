package ai.knowhub.chat.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【搜索引用】
 *
 * 作用：表示 AI 回答时引用的一个知识来源。
 * 可以来自知识库文档（Document）或网络搜索（Web Search），
 * 前端用于展示「参考来源」列表，让用户了解 AI 的回答依据。
 *
 * 所属架构位置：属于 RAG 模块的输出层，是检索结果面向用户的最终展示形态。
 * 每个 SearchReference 代表一个被引用的知识片段。
 *
 * 设计模式说明：「值对象（Value Object）」模式，同时提供了「工厂方法」构造函数
 * （三参数构造函数用于创建网络搜索引用）和「唯一键生成」方法（uniqueKey），
 * 用于引用去重。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
public class SearchReference {

    /**
     * 引用 ID（referenceId）
     * 唯一标识一个搜索引用记录。
     */
    private String referenceId;

    /**
     * 来源类型（sourceType）
     * 标识引用来源的类型：
     * - "DOCUMENT"：来自知识库文档
     * - "WEB"：来自网络搜索
     */
    private String sourceType;

    /**
     * 标题（title）
     * 引用来源的标题，如文档标题或网页标题。
     */
    private String title;

    /**
     * 链接地址（url）
     * 引用来源的 URL 地址（网络搜索结果时有值）。
     */
    private String url;

    /**
     * 文本摘要（snippet）
     * 引用内容的摘要片段，展示给用户的关键信息。
     */
    private String snippet;

    /** 文档 ID（来自知识库时有值） */
    private Long documentId;

    /** 文档名称 */
    private String documentName;

    /** 文档片段 ID */
    private Long chunkId;

    /** 父块 ID */
    private Long parentBlockId;

    /** 父块编号 */
    private Integer parentBlockNo;

    /** 片段编号 */
    private Integer chunkNo;

    /** 章节路径 */
    private String sectionPath;

    /**
     * 结构节点 ID（structureNodeId）
     * 知识图谱或文档结构树中的节点 ID。
     */
    private Long structureNodeId;

    /**
     * 结构节点类型（structureNodeType）
     * 标识结构节点的类型，如章节、段落、列表等。
     */
    private Integer structureNodeType;

    /**
     * 规范路径（canonicalPath）
     * 引用来源的标准化路径，用于唯一标识和去重。
     */
    private String canonicalPath;

    /**
     * 条目索引（itemIndex）
     * 该引用在搜索结果列表中的位置索引。
     */
    private Integer itemIndex;

    /**
     * 相关性分数（score）
     * 检索系统计算的相关性分数，越高表示与用户问题越相关。
     */
    private Double score;

    /** 子问题索引（当问题被拆分时） */
    private Integer subQuestionIndex;

    /** 子问题文本 */
    private String subQuestion;

    /**
     * 检索通道（channel）
     * 该引用来自哪个检索通道，如 "keyword"、"vector"、"web-search" 等。
     */
    private String channel;

    /**
     * 工具名称（toolName）
     * 产生该引用的工具名称，如 "tavily_search"（Tavily 网络搜索）。
     */
    private String toolName;

    /** 知识范围编码 */
    private String knowledgeScopeCode;

    /** 知识范围名称 */
    private String knowledgeScopeName;

    /**
     * 构造函数：创建网络搜索引用
     *
     * 设计模式：工厂方法模式的简化版，为最常见的网络搜索引用提供便捷构造。
     *
     * @param title   网页标题
     * @param url     网页链接
     * @param snippet 网页内容摘要
     */
    public SearchReference(String title, String url, String snippet) {
        this.sourceType = "WEB";
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.channel = "web-search";
        this.toolName = "tavily_search";
    }

    /**
     * 生成唯一键（uniqueKey）
     *
     * 用于引用去重：当同一个文档片段或网页被多次检索到时，
     * 通过唯一键来判断是否为重复引用。
     *
     * 优先级：
     * 1. 如果有父块 ID，使用 "PARENT:父块ID" —— 同一父块下的不同片段视为同一来源
     * 2. 如果有片段 ID，使用 "DOCUMENT:片段ID" —— 精确到文档片段
     * 3. 如果有 URL，使用 "WEB:url" —— 网页引用
     * 4. 兜底方案：使用 "来源类型:标题:摘要" 的组合
     *
     * @return 唯一键字符串
     */
    public String uniqueKey() {
        if (parentBlockId != null) {
            return "PARENT:" + parentBlockId;
        }
        if (chunkId != null) {
            return "DOCUMENT:" + chunkId;
        }
        if (url != null && !url.isBlank()) {
            return "WEB:" + url;
        }
        return (sourceType == null ? "UNKNOWN" : sourceType)
            + ":" + (title == null ? "" : title)
            + ":" + (snippet == null ? "" : snippet);
    }
}
