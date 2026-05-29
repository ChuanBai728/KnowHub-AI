package ai.knowhub.chat.rag.service;

/**
 * 【文档问题意图判断结果 — 路由决策的"多维度诊断报告"】
 *
 * 这个类是 {@link DocumentQuestionRouter} 在判断用户问题意图时使用的统一结果对象。
 * 它从多个维度描述一个问题的特征，帮助路由器决定应该走哪条执行路径。
 *
 * 意图维度包括：
 * - graphOnlyIntent：是否可以直接用文档结构图回答（不需要检索正文）
 * - analytic：是否需要解释、分析、对比或原因推理
 * - outline：是否在询问章节目录展开
 * - itemLookup：是否在询问步骤、条目、编号项定位
 * - structureHint：是否有章节、目录、标题等结构线索
 * - contentQuestion：是否在询问正文内容（要求、流程等需要证据的问题）
 *
 * 设计模式：值对象模式（Value Object），所有字段在构造后不可变。
 *
 * 在 RAG 流水线中的位置：
 * 用户问题 -> DocumentQuestionRouter.detectQuestionIntent() -> 【本类：意图判断结果】
 * -> 路由器根据这些维度选择 ExecutionMode（GRAPH_ONLY / GRAPH_THEN_EVIDENCE / RETRIEVAL）
 */
final class DocumentQuestionIntentDecision {

    /**
     * GRAPH_ONLY 意图判断结果。
     * 如果命中了 GRAPH_ONLY，说明问题可以用文档结构图直接回答，不需要检索正文。
     * 例如："第三章的上一节是什么？" -> 直接查结构图的相邻节点即可。
     */
    private final GraphOnlyIntentDecision graphOnlyIntent;

    /**
     * 是否为分析型问题。
     * 分析型问题需要解释、对比、推理，通常不能只靠结构图回答。
     * 例如："为什么需要配置这个参数？" -> 需要检索正文中的解释内容。
     */
    private final boolean analytic;

    /**
     * 是否在询问章节目录展开。
     * 例如："第三章包含哪些小节？" -> 需要查结构图的子节点。
     */
    private final boolean outline;

    /**
     * 是否在询问步骤/条目/编号项定位。
     * 例如："第三步是什么？" -> 需要先定位到具体章节，再找编号项。
     */
    private final boolean itemLookup;

    /**
     * 是否带有结构线索（可用于辅助定位章节）。
     * 结构线索包括：章节编号（如 1.2）、中文章节引用（如"第三章"）、引号标题等。
     * 即使不走 GRAPH_ONLY，结构线索也可以作为检索的软提示。
     */
    private final boolean structureHint;

    /**
     * 是否明显在询问正文内容。
     * 例如："部署流程是什么？"、"有什么要求？" -> 这类问题需要正文证据。
     */
    private final boolean contentQuestion;

    /**
     * 本次意图判断的置信度（0.0 ~ 1.0）。
     * 本地规则通常是固定高分（如 1.0），LLM 结果会按阈值校验（如 0.75）。
     */
    private final double confidence;

    /**
     * 本次意图判断的原因说明，写入路由决策摘要，便于排查问题。
     */
    private final String reason;

    /**
     * 本次意图判断的来源，例如 "local-rules"（本地规则）或 "llm-ADJACENCY"（LLM 分类）。
     */
    private final String source;

    /**
     * 构造函数，初始化所有意图维度。
     *
     * @param graphOnlyIntent  GRAPH_ONLY 意图判断结果
     * @param analytic         是否为分析型问题
     * @param outline          是否在询问章节目录展开
     * @param itemLookup       是否在询问步骤/条目定位
     * @param structureHint    是否带有结构线索
     * @param contentQuestion  是否在询问正文内容
     * @param confidence       置信度
     * @param reason           原因说明
     * @param source           判断来源
     */
    DocumentQuestionIntentDecision(GraphOnlyIntentDecision graphOnlyIntent,
                                   boolean analytic,
                                   boolean outline,
                                   boolean itemLookup,
                                   boolean structureHint,
                                   boolean contentQuestion,
                                   double confidence,
                                   String reason,
                                   String source) {
        this.graphOnlyIntent = graphOnlyIntent;
        this.analytic = analytic;
        this.outline = outline;
        this.itemLookup = itemLookup;
        this.structureHint = structureHint;
        this.contentQuestion = contentQuestion;
        this.confidence = confidence;
        this.reason = reason;
        this.source = source;
    }

    /** 获取 GRAPH_ONLY 意图判断结果 */
    GraphOnlyIntentDecision graphOnlyIntent() {
        return graphOnlyIntent;
    }

    /** 是否为分析型问题 */
    boolean analytic() {
        return analytic;
    }

    /** 是否在询问章节目录展开 */
    boolean outline() {
        return outline;
    }

    /** 是否在询问步骤/条目定位 */
    boolean itemLookup() {
        return itemLookup;
    }

    /** 是否带有结构线索 */
    boolean structureHint() {
        return structureHint;
    }

    /** 是否在询问正文内容 */
    boolean contentQuestion() {
        return contentQuestion;
    }

    /** 获取置信度 */
    double confidence() {
        return confidence;
    }

    /** 获取原因说明 */
    String reason() {
        return reason;
    }

    /** 获取判断来源 */
    String source() {
        return source;
    }
}
