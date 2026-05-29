package ai.knowhub.chat.model.trace;

/**
 * 【会话追踪阶段编码枚举】
 *
 * 作用：定义对话处理流程中所有可能的处理阶段。
 * 每个阶段代表对话处理管道（Pipeline）中的一个步骤，
 * 用于追踪和监控每个阶段的执行状态和性能。
 *
 * 所属架构位置：属于会话追踪系统（Conversation Trace System），
 * 定义了对话处理的完整流水线阶段。这些阶段按顺序编号，
 * 反映了从用户输入到 AI 输出的完整处理链路。
 *
 * 设计模式说明：「枚举模式（Enum Pattern）」，
 * 每个枚举值包含三个属性：编码（code）、中文标签（label）、执行顺序（order）。
 *
 * 处理流程说明（按 order 排序）：
 * 1. MEMORY(10)       -> 加载会话记忆（历史对话、摘要等）
 * 2. INTENT(20)       -> 分析用户意图（判断问题类型和需求）
 * 3. REWRITE(30)      -> 改写用户问题（优化检索效果）
 * 4. ROUTE(40)        -> 路由判定（决定使用哪种检索策略）
 * 5. GRAPH_QUERY(45)  -> 知识图谱查询（结构化知识检索）
 * 6. RAG_RETRIEVE(50) -> RAG 文档检索（向量/关键词检索）
 * 7. EVIDENCE_BUDGET(60) -> 证据评估与预算控制（筛选和排序检索结果）
 * 8. ANSWER_GENERATE(70) -> 生成回答（调用 LLM 生成最终回答）
 * 9. REACT_AGENT(75)  -> ReAct Agent 执行（推理-行动循环）
 * 10. RECOMMENDATION(80) -> 生成推荐问题（引导用户继续探索）
 * 11. FINALIZE(90)    -> 收尾归档（保存对话记录、更新状态等）
 *
 * @author knowhub
 */
public enum ConversationTraceStageCode {

    /**
     * 会话记忆阶段（order=10）
     * 加载和组装会话记忆上下文，包括历史摘要和近期对话记录。
     */
    MEMORY("MEMORY", "会话记忆", 10),

    /**
     * 意图分析阶段（order=20）
     * 分析用户问题的意图，判断问题类型（如信息查询、任务执行、闲聊等）。
     */
    INTENT("INTENT", "意图分析", 20),

    /**
     * 问题改写阶段（order=30）
     * 对用户原始问题进行改写优化，如补充上下文、纠正错别字、拆分复合问题等。
     */
    REWRITE("REWRITE", "问题改写", 30),

    /**
     * 路由判定阶段（order=40）
     * 根据问题特征决定使用哪种检索策略和执行模式。
     */
    ROUTE("ROUTE", "路由判定", 40),

    /**
     * 知识图谱查询阶段（order=45）
     * 在知识图谱（Neo4j）中进行结构化查询，获取实体关系等知识。
     */
    GRAPH_QUERY("GRAPH_QUERY", "结构图查询", 45),

    /**
     * RAG 文档检索阶段（order=50）
     * 使用向量检索和关键词检索从知识库中检索相关文档片段。
     */
    RAG_RETRIEVE("RAG_RETRIEVE", "RAG 检索", 50),

    /**
     * 证据评估与预算控制阶段（order=60）
     * 对检索结果进行质量评估、重排序和预算控制，
     * 确保最终选入上下文的文档片段既相关又不超出 Token 预算。
     */
    EVIDENCE_BUDGET("EVIDENCE_BUDGET", "证据评估与预算控制", 60),

    /**
     * 回答生成阶段（order=70）
     * 调用大语言模型（LLM）生成最终回答。
     */
    ANSWER_GENERATE("ANSWER_GENERATE", "回答生成", 70),

    /**
     * ReAct Agent 阶段（order=75）
     * 执行 ReAct（Reasoning + Acting）Agent 的推理-行动循环，
     * AI 可以多轮调用工具来解决问题。
     */
    REACT_AGENT("REACT_AGENT", "ReAct Agent", 75),

    /**
     * 推荐问题阶段（order=80）
     * 根据当前对话上下文生成推荐的后续问题。
     */
    RECOMMENDATION("RECOMMENDATION", "推荐问题", 80),

    /**
     * 收尾归档阶段（order=90）
     * 保存对话记录、更新会话状态、清理临时数据等收尾工作。
     */
    FINALIZE("FINALIZE", "收尾归档", 90);

    /** 阶段编码，用于数据库存储和日志标识 */
    private final String code;

    /** 阶段中文标签，用于前端展示 */
    private final String label;

    /**
     * 执行顺序（order）
     * 数值越小越先执行，用于排序和流程控制。
     * 阶段之间的 order 留有间隔（如10,20,30），便于后续插入新阶段。
     */
    private final int order;

    ConversationTraceStageCode(String code, String label, int order) {
        this.code = code;
        this.label = label;
        this.order = order;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getOrder() {
        return order;
    }
}
