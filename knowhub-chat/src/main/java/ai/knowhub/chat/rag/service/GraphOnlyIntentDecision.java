package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.rag.model.DocumentNavigationAction;

/**
 * 【结构图直答意图判断结果 — GRAPH_ONLY 模式的"准入证"】
 *
 * 这个类是 {@link DocumentQuestionRouter} 用来判断一个问题是否适合
 * 直接用文档结构图回答（GRAPH_ONLY 模式）的结果对象。
 *
 * 什么是 GRAPH_ONLY？
 * 当用户问的是纯结构问题（如"第三章包含哪些小节？"、"上一节是什么？"），
 * 不需要检索正文内容，只需要查询文档的章节树结构（Neo4j 图数据库）即可回答。
 * 这种模式比标准 RAG 检索更快、更准确。
 *
 * 设计模式：值对象模式（Value Object），所有字段在构造后不可变。
 * 使用 Java record 的风格（只有 getter，无 setter），但因为不是 record 类型所以手动实现。
 *
 * 在 RAG 流水线中的位置：
 * 用户问题 -> DocumentQuestionRouter.detectGraphOnlyIntentByRules() -> 【本类：判断结果】
 * -> 如果 matched=true，路由器会选择 ExecutionMode.GRAPH_ONLY
 */
final class GraphOnlyIntentDecision {

    /**
     * 是否已经明确命中 GRAPH_ONLY 意图。
     * true：问题可以用结构图直接回答
     * false：问题需要走标准 RAG 检索
     */
    private final boolean matched;

    /**
     * 命中后要交给图查询执行器的结构导航动作。
     * - SECTION_ADJACENCY_LOOKUP：相邻章节查询（上一节/下一节）
     * - CHILD_SECTION_DESCEND：子章节展开（包含哪些小节）
     * 仅在 matched=true 时有值。
     */
    private final DocumentNavigationAction action;

    /**
     * 命中或未命中的原因说明。
     * 会写入路由决策摘要，便于排查问题。
     * 例如："命中明确相邻章节表达，结构型问题直接走图查询。"
     */
    private final String reason;

    /**
     * 本次判断的置信度（0.0 ~ 1.0）。
     * 本地规则通常是固定高分（如 1.0），LLM 结果会按阈值（0.75）校验。
     */
    private final double confidence;

    /**
     * 本次判断的来源。
     * - "rule-adjacency-hint"：本地规则命中相邻章节提示词
     * - "rule-outline-hint"：本地规则命中目录展开提示词
     * - "llm-ADJACENCY"：LLM 兜底分类为相邻章节
     * - "none"：未命中
     */
    private final String source;

    /**
     * 构造函数。
     *
     * @param matched    是否命中 GRAPH_ONLY
     * @param action     导航动作（仅 matched=true 时有值）
     * @param reason     原因说明
     * @param confidence 置信度
     * @param source     判断来源
     */
    GraphOnlyIntentDecision(boolean matched,
                            DocumentNavigationAction action,
                            String reason,
                            double confidence,
                            String source) {
        this.matched = matched;
        this.action = action;
        this.reason = reason;
        this.confidence = confidence;
        this.source = source;
    }

    /** 是否命中 GRAPH_ONLY 意图 */
    boolean matched() {
        return matched;
    }

    /** 获取导航动作 */
    DocumentNavigationAction action() {
        return action;
    }

    /** 获取原因说明 */
    String reason() {
        return reason;
    }

    /** 获取置信度 */
    double confidence() {
        return confidence;
    }

    /** 获取判断来源 */
    String source() {
        return source;
    }
}
