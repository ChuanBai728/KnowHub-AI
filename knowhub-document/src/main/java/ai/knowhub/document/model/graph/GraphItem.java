package ai.knowhub.document.model.graph;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图条目节点（Graph Item）
 *
 * 【类的作用】
 * 表示 Neo4j 图数据库中的一个"条目"节点（叶子节点）。
 * 条目是文档结构树中最细粒度的内容单元，通常对应段落、
 * 表格行、列表项等不可再分的内容块。
 *
 * 【在架构中的角色】
 * 属于 Graph RAG（图检索增强生成）的数据模型层：
 * 1. 文档解析后，内容被组织为树形结构存入 Neo4j
 * 2. 树的中间节点是 Section（章节），叶子节点是 Item（条目）
 * 3. 检索时，系统通过图遍历找到相关的 Item，再向上回溯获取上下文
 *
 * 【与 GraphSection 的关系】
 * - GraphSection：章节节点，可以包含子章节或条目
 * - GraphItem：条目节点，叶子节点，被 GraphSection 包含
 * - sectionNodeId：指向所属的 GraphSection 节点
 *
 * 【Neo4j 中的表示】
 * 在 Neo4j 中，GraphItem 对应一个节点（Node），
 * 通过 PARENT_OF 或 CONTAINS 等关系（Relationship）连接到 GraphSection。
 *
 * 【设计模式】
 * - 组合模式（Composite Pattern）：GraphSection 和 GraphItem 共同构成树形结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphItem {

    /**
     * 节点唯一标识 ID
     * Neo4j 中该节点的内部 ID，用于图遍历和关系查询。
     */
    private Long nodeId;

    /**
     * 所属文档 ID
     * 该条目所属的源文档 ID，用于结果溯源。
     */
    private Long documentId;

    /**
     * 解析任务 ID
     * 该条目是由哪次解析任务生成的。
     */
    private Long parseTaskId;

    /**
     * 节点序号
     * 条目在其所属章节中的顺序编号。
     */
    private Integer nodeNo;

    /**
     * 节点类型
     * 条目的类型，例如"paragraph"（段落）、"table_row"（表格行）、
     * "list_item"（列表项）等。
     */
    private String nodeType;

    /**
     * 所属章节节点 ID
     * 指向该条目所属的 GraphSection 节点，
     * 用于从条目向上回溯到章节，获取上下文信息。
     */
    private Long sectionNodeId;

    /**
     * 前一个兄弟节点 ID
     * 指向同一章节下的前一个条目节点，
     * 用于按顺序遍历章节内的所有条目。
     */
    private Long prevSiblingNodeId;

    /**
     * 后一个兄弟节点 ID
     * 指向同一章节下的后一个条目节点。
     */
    private Long nextSiblingNodeId;

    /**
     * 条目标题
     * 条目的标题文本，对于段落可能是段落的首句或小标题。
     */
    private String title;

    /**
     * 锚点文本
     * 文档中用于内部链接的锚点标识。
     */
    private String anchorText;

    /**
     * 章节路径
     * 该条目在文档目录中的位置路径。
     */
    private String sectionPath;

    /**
     * 规范路径
     * 条目的标准化唯一路径标识。
     */
    private String canonicalPath;

    /**
     * 内容文本
     * 条目的实际文本内容，是检索和生成的核心数据。
     */
    private String contentText;

    /**
     * 条目索引
     * 条目在文档所有条目中的全局顺序索引。
     */
    private Integer itemIndex;

    /**
     * 获取条目的展示文本
     *
     * 【优先级策略】
     * 按以下优先级选择展示文本：
     * 1. contentText（内容文本）- 最完整的信息
     * 2. anchorText（锚点文本）- 次选
     * 3. title（标题）- 最后选择
     * 4. 空字符串 - 以上都没有时的兜底
     *
     * 使用 Hutool 的 StrUtil.blankToDefault 方法进行空值判断，
     * 如果字符串为 null 或空白，则使用默认值。
     *
     * @return 条目的展示文本，不会返回 null
     */
    public String displayText() {
        return StrUtil.blankToDefault(contentText, StrUtil.blankToDefault(anchorText, StrUtil.blankToDefault(title, "")));
    }
}
