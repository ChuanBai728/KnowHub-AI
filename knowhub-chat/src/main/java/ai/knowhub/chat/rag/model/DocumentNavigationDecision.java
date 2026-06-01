package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档导航决策 —— 路由规划阶段产出的"导航指令"，告诉执行器如何在文档结构中定位信息。
 *
 * 在 RAG 流水线中的角色
 * 当路由规划阶段判断用户问题涉及文档结构（如问章节关系、编号项内容等）时，
 * 会生成一个 DocumentNavigationDecision 对象，包含：
 * 
 *   <b>导航动作</b>（#navigationAction）：用户想做什么（查子章节？查编号项？查相邻章节？）
 *   <b>执行模式</b>（#executionMode）：应该用哪种执行器处理
 *   <b>结构锚点</b>（#structureAnchor）：定位到哪个章节
 *   <b>编号项锚点</b>（#itemAnchor）：定位到章节内的哪个编号项
 *   <b>检索计划</b>（#retrievalPlan）：如果需要检索，检索什么问题
 * 执行器（如 ai.knowhub.chat.rag.executor.GraphOnlyExecutor）
 * 根据这些信息执行相应的图查询或检索操作。
 *
 * @see DocumentNavigationAction 导航动作枚举
 * @see ConversationStructureAnchor 章节锚点
 * @see ConversationItemAnchor 编号项锚点
 * @see RetrievalQuestionPlan 检索问题计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentNavigationDecision {

    /**
     * 导航动作类型。
     *
     * 描述用户在文档结构中想做什么样的"移动"，
     * 例如查子章节、查编号项、查相邻章节等。
     *
     * @see DocumentNavigationAction
     */
    private DocumentNavigationAction navigationAction;

    /**
     * 建议的执行模式。
     *
     * 路由规划阶段根据导航动作决定应该用哪种执行器。
     * 例如 SECTION_ADJACENCY_LOOKUP → GRAPH_ONLY，ITEM_REFERENCE → GRAPH_THEN_EVIDENCE。
     *
     * @see ExecutionMode
     */
    private ExecutionMode executionMode;

    /**
     * 结构锚点 —— 定位到文档中的某个章节。
     *
     * 包含章节编号、标题、节点 ID 等定位信息。
     *
     * @see ConversationStructureAnchor
     */
    private ConversationStructureAnchor structureAnchor;

    /**
     * 编号项锚点 —— 定位到章节内的某个编号项。
     *
     * 包含编号项索引、文本、节点 ID 等定位信息。
     * 可能为 null（当导航动作不涉及编号项时）。
     *
     * @see ConversationItemAnchor
     */
    private ConversationItemAnchor itemAnchor;

    /**
     * 检索问题计划。
     *
     * 如果导航决策还需要额外的文本检索（如 GRAPH_THEN_EVIDENCE 模式），
     * 此字段记录检索的问题和子问题。
     *
     * @see RetrievalQuestionPlan
     */
    private RetrievalQuestionPlan retrievalPlan;

    /**
     * 导航决策的摘要文本。
     *
     * 人类可读的决策摘要，用于日志和调试。
     * 例如"定位到第 5 章第 3 节，查找第 2 个编号项"。
     */
    private String summaryText;

    /**
     * 查询上下文提示列表。
     *
     * 路由规划阶段提取的额外上下文信息，帮助检索时更好地理解问题。
     * 例如用户问题中的关键词、同义词、相关概念等。
     */
    @Builder.Default
    private List<String> queryContextHints = new ArrayList<>();

    /**
     * 软章节提示列表。
     *
     * 非强制性的章节范围提示。当无法精确确定用户问的是哪个章节时，
     * 提供一些"可能相关"的章节作为软约束。
     *
     * "软"意味着检索时会参考这些提示，但不会严格限制在这些章节内。
     */
    @Builder.Default
    private List<String> softSectionHints = new ArrayList<>();
}
