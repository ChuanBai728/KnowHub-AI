package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话编号项锚点 —— 定位文档中某个章节下的具体编号项（如列表中的第几条）。
 *
 * 在 RAG 流水线中的角色
 * 当用户问到"第三章第 2 步是什么"时，系统不仅需要定位到"第三章"（由 ConversationStructureAnchor 负责），
 * 还需要定位到章节内的"第 2 个编号项"。本类就是用来描述这种"章节内编号项"的定位信息。
 *
 * 编号项的概念
 * 文档中的章节通常包含有序列表（如操作步骤、要求条款等），每个列表项就是一个"编号项"。
 * 例如"5.3 操作步骤"章节下可能有：1. 准备材料 → 2. 检查设备 → 3. 开始操作。
 * 本类通过索引、文本、节点 ID 等方式定位到具体的编号项。
 *
 * @see ConversationStructureAnchor 章节锚点，定位到章节级别
 * @see DocumentNavigationDecision 导航决策，同时包含章节锚点和编号项锚点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationItemAnchor {

    /**
     * 编号项的索引位置。
     *
     * 从 0 开始的整数，表示编号项在章节列表中的位置。
     * 例如"第 2 步"对应 itemIndex = 1（0-based）。
     */
    private Integer itemIndex;

    /**
     * 编号项的文本内容或关键词。
     *
     * 当无法通过索引精确定位时，可以通过文本匹配来查找。
     * 例如用户问"哪一步要求检查设备"，itemText 可能存储"检查设备"。
     */
    private String itemText;

    /**
     * 编号项在结构图中的节点 ID。
     *
     * Neo4j 图数据库中该编号项节点的唯一标识。
     * 有了节点 ID 就可以直接定位，不需要再通过索引或文本匹配。
     */
    private Long structureNodeId;

    /**
     * 编号项的规范化路径。
     *
     * 从根节点到当前编号项的完整路径，例如 "5.3.2" 表示第 5 章第 3 节第 2 项。
     * 用于唯一标识文档中的位置。
     */
    private String canonicalPath;

    /**
     * 判断编号项锚点是否为空（没有任何定位信息）。
     *
     * @return true 表示所有字段都为空或空白，无法定位到任何编号项
     */
    public boolean isEmpty() {
        return itemIndex == null
            && (itemText == null || itemText.isBlank())
            && structureNodeId == null
            && (canonicalPath == null || canonicalPath.isBlank());
    }
}
