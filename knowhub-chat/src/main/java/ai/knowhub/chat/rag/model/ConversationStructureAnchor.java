package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话结构锚点 —— 定位文档中的某个章节位置。
 *
 * 在 RAG 流水线中的角色
 * 在 GRAPH_ONLY 或 GRAPH_THEN_EVIDENCE 模式下，系统需要知道用户问的是文档中的哪个章节。
 * 本类存储章节的定位信息，包括章节编号、标题、节点 ID、路径等。
 *
 * 多维度定位
 * 一个章节可以通过多种方式定位：
 * 
 *   <b>章节编号</b>（rootSectionCode）：如 "5.3"、"3.1.2"
 *   <b>章节标题</b>（rootSectionTitle）：如 "操作步骤"、"安全要求"
 *   <b>目标提示</b>（targetSectionHint）：用户问题中提到的章节描述
 *   <b>节点 ID</b>（structureNodeId）：Neo4j 中的唯一标识，最精确
 *   <b>规范化路径</b>（canonicalPath）：从根到叶的完整路径
 * @see ConversationItemAnchor 编号项锚点，定位章节内的具体编号项
 * @see DocumentNavigationDecision 导航决策，包含本锚点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationStructureAnchor {

    /**
     * 根章节编号。
     *
     * 文档结构中的章节编号，例如 "5"、"5.3"、"3.1.2"。
     * 通常由路由规划阶段从用户问题中提取。
     */
    private String rootSectionCode;

    /**
     * 根章节标题。
     *
     * 章节的标题文本，例如 "操作步骤"、"安全要求"、"配置说明"。
     * 用于通过标题匹配定位章节。
     */
    private String rootSectionTitle;

    /**
     * 目标章节提示。
     *
     * 用户问题中提到的章节描述，可能不是精确的标题。
     * 例如用户说"那个关于安全的章节"，targetSectionHint 可能是"安全"。
     */
    private String targetSectionHint;

    /**
     * 结构图节点 ID。
     *
     * Neo4j 图数据库中该章节节点的唯一标识。
     * 这是最精确的定位方式，有了节点 ID 可以直接查询图数据库。
     */
    private Long structureNodeId;

    /**
     * 规范化路径。
     *
     * 从文档根节点到当前章节的完整路径，例如 "5.3" 表示第 5 章第 3 节。
     * 用于唯一标识文档结构中的位置。
     */
    private String canonicalPath;

    /**
     * 导航范围模式。
     *
     * 控制检索时的范围约束，例如是否严格限定在某个章节内搜索。
     * 对应 NavigationScopeMode 枚举。
     *
     * @see NavigationScopeMode
     */
    private String scopeMode;

    /**
     * 判断结构锚点是否为空（没有任何定位信息）。
     *
     * @return true 表示所有字段都为空或空白，无法定位到任何章节
     */
    public boolean isEmpty() {
        return (rootSectionCode == null || rootSectionCode.isBlank())
            && (rootSectionTitle == null || rootSectionTitle.isBlank())
            && (targetSectionHint == null || targetSectionHint.isBlank())
            && structureNodeId == null
            && (canonicalPath == null || canonicalPath.isBlank());
    }
}
