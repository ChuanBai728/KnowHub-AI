package ai.knowhub.chat.rag.service;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.chat.rag.model.DocumentNavigationAction;
import ai.knowhub.chat.rag.model.DocumentNavigationDecision;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.document.model.graph.GraphItem;
import ai.knowhub.document.model.graph.GraphQueryResult;
import ai.knowhub.document.model.graph.GraphSection;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

/**
 * 【图答案渲染器 — 将图查询结果转换为人类可读的文本】
 *
 * 这个类负责将文档结构图（Graph）的查询结果渲染成自然语言文本，
 * 供后续的 Prompt 组装或直接作为回答使用。
 *
 * 支持两种渲染模式：
 * 1. GRAPH_ONLY 模式：纯结构图查询结果（如"第三章包含哪些小节"）
 *    - 相邻章节查询：渲染目标章节、父章节、上一节、下一节
 *    - 目录展开查询：渲染子章节列表
 *    - 简单标题查询：直接返回章节标题
 *
 * 2. GRAPH_THEN_EVIDENCE 模式：图定位 + 正文取证结果（如"第三步是什么"）
 *    - 精确命中编号项：渲染"第 X 步是：..."
 *    - 多个匹配项：渲染"命中了以下步骤：..."
 *    - 无精确匹配：渲染章节正文内容
 *
 * 设计模式：策略模式（Strategy Pattern），根据执行模式选择不同的渲染策略。
 *
 * 在 RAG 流水线中的位置：
 * StructureGraphQueryEngine -> GraphQueryResult -> 【本类：渲染为文本】-> Prompt 或直接回答
 */
@Service
public class GraphAnswerRenderer {

    /** 左双引号 Unicode 转义 */
    private static final String LQ = "“";
    /** 右双引号 Unicode 转义 */
    private static final String RQ = "”";

    /**
     * 渲染图查询结果为自然语言文本。
     *
     * @param mode       执行模式（GRAPH_ONLY 或 GRAPH_THEN_EVIDENCE）
     * @param decision   导航决策（包含导航动作等信息）
     * @param graphResult 图查询结果（包含目标章节、子章节、编号项等）
     * @return 渲染后的自然语言文本，如果结果为空则返回空字符串
     */
    public String renderGraphAnswer(ExecutionMode mode,
                                    DocumentNavigationDecision decision,
                                    GraphQueryResult graphResult) {
        if (graphResult == null || graphResult.getTargetSection() == null) {
            return "";
        }
        if (mode == ExecutionMode.GRAPH_THEN_EVIDENCE) {
            return renderGraphThenEvidence(decision, graphResult);
        }
        return renderGraphOnly(decision, graphResult);
    }

    /**
     * 渲染 GRAPH_ONLY 模式的结果。
     *
     * 根据问题类型选择渲染策略：
     * - 相邻章节查询：渲染上一节/下一节/父章节信息
     * - 目录展开查询：渲染子章节列表
     * - 简单查询：直接返回章节标题
     */
    private String renderGraphOnly(DocumentNavigationDecision decision, GraphQueryResult graphResult) {
        DocumentNavigationAction action = decision == null ? null : decision.getNavigationAction();
        String question = decision == null || decision.getRetrievalPlan() == null
            ? ""
            : StrUtil.blankToDefault(decision.getRetrievalPlan().getRetrievalQuestion(), "");
        // 相邻章节查询
        if (action == DocumentNavigationAction.SECTION_ADJACENCY_LOOKUP || asksAdjacency(question)) {
            return renderAdjacency(graphResult);
        }
        // 目录展开查询
        if (asksChildren(question) || !graphResult.getChildren().isEmpty()) {
            return renderChildren(graphResult.getTargetSection(), graphResult.getChildren());
        }
        // 简单标题查询
        return graphResult.getTargetSection().displayTitle();
    }

    /**
     * 渲染 GRAPH_THEN_EVIDENCE 模式的结果。
     *
     * 根据查询结果的精确程度选择渲染策略：
     * - 精确命中编号项：渲染"第 X 步是：..."
     * - 多个匹配项：渲染"命中了以下步骤：..."
     * - 无精确匹配：渲染章节正文内容
     */
    private String renderGraphThenEvidence(DocumentNavigationDecision decision, GraphQueryResult graphResult) {
        // 精确命中编号项
        if (graphResult.getTargetItem() != null) {
            GraphItem item = graphResult.getTargetItem();
            return LQ + graphResult.getTargetSection().displayTitle() + RQ + "中的第" + item.getItemIndex() + "步是：\n"
                + formatItem(item);
        }
        // 多个匹配项
        if (graphResult.getMatchedItems() != null && !graphResult.getMatchedItems().isEmpty()) {
            StringJoiner joiner = new StringJoiner("\n");
            joiner.add("在" + LQ + graphResult.getTargetSection().displayTitle() + RQ + "中命中了以下步骤：");
            for (GraphItem item : graphResult.getMatchedItems()) {
                joiner.add(formatItem(item));
            }
            return joiner.toString();
        }
        // 无精确匹配，渲染章节正文内容
        GraphSection targetSection = graphResult.getTargetSection();
        if (StrUtil.isNotBlank(targetSection.getContentText())) {
            return LQ + targetSection.displayTitle() + RQ + "中的相关内容如下：\n" + targetSection.getContentText().trim();
        }
        return targetSection.displayTitle();
    }

    /**
     * 渲染相邻章节信息。
     * 输出格式：
     * 目标章节是："XXX"。
     * 它属于："YYY"。
     * 上一节："ZZZ"
     * 下一节："WWW"
     */
    private String renderAdjacency(GraphQueryResult graphResult) {
        StringJoiner joiner = new StringJoiner("\n");
        GraphSection targetSection = graphResult.getTargetSection();
        GraphSection parentSection = graphResult.getParentSection();
        joiner.add("目标章节是：" + LQ + targetSection.displayTitle() + RQ + "。");
        if (parentSection != null) {
            joiner.add("它属于：" + LQ + parentSection.displayTitle() + RQ + "。");
        }
        joiner.add("上一节：" + formatSectionOrFallback(graphResult.getPreviousSibling()));
        joiner.add("下一节：" + formatSectionOrFallback(graphResult.getNextSibling()));
        return joiner.toString();
    }

    /**
     * 渲染子章节列表。
     * 输出格式：
     * "XXX"包含以下章节：
     * - 子章节1
     * - 子章节2
     */
    private String renderChildren(GraphSection targetSection, List<GraphSection> children) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(LQ + targetSection.displayTitle() + RQ + "包含以下章节：");
        if (children == null || children.isEmpty()) {
            joiner.add("未找到直接子章节。");
            return joiner.toString();
        }
        for (GraphSection child : children) {
            joiner.add("- " + child.displayTitle());
        }
        return joiner.toString();
    }

    /**
     * 格式化编号项为可读文本。
     * 有索引时：第X步：内容
     * 无索引时：内容
     */
    private String formatItem(GraphItem item) {
        if (item == null) {
            return "";
        }
        if (item.getItemIndex() != null) {
            return "第" + item.getItemIndex() + "步：" + StrUtil.blankToDefault(item.displayText(), "");
        }
        return StrUtil.blankToDefault(item.displayText(), "");
    }

    /**
     * 格式化章节节点，如果为 null 则返回"未找到相邻章节"。
     */
    private String formatSectionOrFallback(GraphSection section) {
        return section == null ? "未找到相邻章节" : LQ + section.displayTitle() + RQ;
    }

    /** 判断问题是否在询问相邻章节（上一节/下一节等） */
    private boolean asksAdjacency(String question) {
        return question.contains("上一节")
            || question.contains("下一节")
            || question.contains("前一节")
            || question.contains("后一节")
            || question.contains("属于哪个章节");
    }

    /** 判断问题是否在询问子章节列表（包含哪些章节等） */
    private boolean asksChildren(String question) {
        return question.contains("包含哪些章节")
            || question.contains("都包含哪些章节")
            || question.contains("有哪些小节")
            || question.contains("有哪些章节");
    }
}
