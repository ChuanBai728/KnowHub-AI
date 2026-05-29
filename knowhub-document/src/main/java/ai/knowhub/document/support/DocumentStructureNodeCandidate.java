package ai.knowhub.document.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档结构节点候选（Document Structure Node Candidate）
 *
 * 【类的作用】
 * 表示文档结构树中的一个最终候选节点。经过信号提取、歧义消解、层级构建和树验证后，
 * 每个节点被转换为本对象，包含了节点的完整信息（位置、类型、内容、路径等）。
 *
 * 【在架构中的角色】
 * 是文档结构分析流程的最终输出。DocumentStructureTreeValidator 将
 * DocumentStructureNodeDraft 列表转换为本对象列表，作为文档结构树的表示。
 *
 * 【与 DocumentStructureNodeDraft 的区别】
 * - NodeDraft 是中间产物，包含构建过程中的临时数据（如 numericPath、sourceFamily）
 * - NodeCandidate 是最终产物，只包含持久化和使用所需的数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStructureNodeCandidate {

    /**
     * 节点编号（Node No）
     * 节点的唯一标识，根节点为 1，后续节点递增
     */
    private Integer nodeNo;

    /**
     * 节点类型（Node Type）
     * 对应 DocumentStructureNodeTypeEnum 的编码值
     * 如：文档节点、章节节点、列表项节点、步骤节点等
     */
    private Integer nodeType;

    /**
     * 父节点编号（Parent Node No）
     * 指向父节点的编号，根节点的父节点为 null
     */
    private Integer parentNodeNo;

    /**
     * 前一个兄弟节点编号（Previous Sibling Node No）
     * 同级前一个节点的编号，如果是第一个子节点则为 0
     */
    private Integer prevSiblingNodeNo;

    /**
     * 后一个兄弟节点编号（Next Sibling Node No）
     * 同级后一个节点的编号，如果是最后一个子节点则为 0
     */
    private Integer nextSiblingNodeNo;

    /**
     * 节点深度（Depth）
     * 节点在树中的深度，根节点为 0
     */
    private Integer depth;

    /**
     * 节点编码（Node Code）
     * 节点的编号标识，如 "1.1"、"第一章"、"附录A" 等
     */
    private String nodeCode;

    /**
     * 节点标题（Title）
     * 节点的标题文本
     */
    private String title;

    /**
     * 锚文本（Anchor Text）
     * 用于显示和检索的锚文本，通常包含编码和标题
     */
    private String anchorText;

    /**
     * 规范路径（Canonical Path）
     * 节点在结构树中的标准化路径，如 "/document/chapter-1/section-2"
     */
    private String canonicalPath;

    /**
     * 章节路径（Section Path）
     * 节点的章节路径，如 "第一章 > 第一节 > 背景"
     */
    private String sectionPath;

    /**
     * 内容文本（Content Text）
     * 节点关联的正文内容
     */
    private String contentText;

    /**
     * 项目索引（Item Index）
     * 列表项在列表中的序号
     */
    private Integer itemIndex;
}
