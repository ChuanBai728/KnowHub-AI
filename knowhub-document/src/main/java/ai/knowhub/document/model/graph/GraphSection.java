package ai.knowhub.document.model.graph;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图章节节点（Graph Section）
 *
 * 【类的作用】
 * 表示 Neo4j 图数据库中的一个"章节"节点（中间节点）。
 * 章节是文档结构树中的组织单元，可以包含子章节或条目（GraphItem）。
 *
 * 【在架构中的角色】
 * 属于 Graph RAG 的核心数据模型：
 * 1. 文档解析后形成树形结构，章节是树的中间节点
 * 2. 章节之间通过父子关系、兄弟关系形成层级结构
 * 3. 检索时，系统通过章节节点进行图遍历，定位相关内容
 *
 * 【树形结构示例】
 * 文档（Document）
 *   ├── 第一章（GraphSection, depth=0）
 *   │   ├── 1.1 节（GraphSection, depth=1）
 *   │   │   ├── 段落1（GraphItem）
 *   │   │   └── 段落2（GraphItem）
 *   │   └── 1.2 节（GraphSection, depth=1）
 *   └── 第二章（GraphSection, depth=0）
 *
 * 【Neo4j 中的关系】
 * - PARENT_OF：父子关系（parent -> child）
 * - NEXT_SIBLING：兄弟关系（前一个 -> 后一个）
 * - CONTAINS：包含条目关系（section -> item）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphSection {

    /**
     * 节点唯一标识 ID
     * Neo4j 中该节点的内部 ID，用于图遍历和关系查询。
     */
    private Long nodeId;

    /**
     * 所属文档 ID
     * 该章节所属的源文档 ID。
     */
    private Long documentId;

    /**
     * 解析任务 ID
     * 该章节是由哪次解析任务生成的。
     */
    private Long parseTaskId;

    /**
     * 节点序号
     * 章节在同级兄弟节点中的顺序编号。
     */
    private Integer nodeNo;

    /**
     * 节点深度
     * 章节在文档结构树中的层级深度，
     * 例如一级章节深度为 0，二级章节深度为 1，以此类推。
     */
    private Integer depth;

    /**
     * 父节点 ID
     * 指向该章节的父章节节点。
     * 如果为 null，表示该章节是顶级章节。
     */
    private Long parentNodeId;

    /**
     * 前一个兄弟节点 ID
     * 指向同级的前一个章节节点，
     * 用于按顺序遍历兄弟章节。
     */
    private Long prevSiblingNodeId;

    /**
     * 后一个兄弟节点 ID
     * 指向同级的后一个章节节点。
     */
    private Long nextSiblingNodeId;

    /**
     * 节点编码
     * 章节的编号标识，例如"1"、"1.2"、"1.2.3"等，
     * 反映章节在文档中的层级位置。
     */
    private String nodeCode;

    /**
     * 章节标题
     * 例如"项目背景"、"技术方案"、"1.2 系统架构"等。
     */
    private String title;

    /**
     * 锚点文本
     * 文档中用于内部链接的锚点标识文本。
     */
    private String anchorText;

    /**
     * 章节路径
     * 从根节点到当前节点的完整路径，
     * 例如"第一章/第2节/2.1 小节标题"。
     */
    private String sectionPath;

    /**
     * 规范路径
     * 章节的标准化唯一路径标识，
     * 不依赖文档的目录编号格式。
     */
    private String canonicalPath;

    /**
     * 内容文本
     * 该章节节点自身的文本内容（如果有）。
     * 注意：章节节点的主要作用是组织结构，
     * 实际内容通常存储在子节点（GraphItem）中。
     */
    private String contentText;

    /**
     * 获取章节的展示标题
     *
     * 【优先级策略】
     * 按以下优先级选择展示标题：
     * 1. sectionPath（章节路径）- 最完整的路径信息，如"第一章/第2节"
     * 2. nodeCode + title（编码 + 标题）- 如"1.2 系统架构"
     * 3. title（仅标题）- 如"系统架构"
     * 4. 空字符串 - 以上都没有时的兜底
     *
     * @return 章节的展示标题，不会返回 null
     */
    public String displayTitle() {
        if (StrUtil.isNotBlank(sectionPath)) {
            return sectionPath.trim();
        }
        if (StrUtil.isNotBlank(nodeCode) && StrUtil.isNotBlank(title)) {
            return (nodeCode + " " + title).trim();
        }
        return StrUtil.blankToDefault(title, "");
    }
}
