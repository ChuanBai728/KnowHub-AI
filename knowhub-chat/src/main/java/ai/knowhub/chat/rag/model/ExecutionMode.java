package ai.knowhub.chat.rag.model;

/**
 * 执行模式枚举 —— 定义 RAG 流水线中所有可能的执行路径。
 *
 * 设计模式：策略模式的策略标识
 * 在策略模式中，每种执行策略需要一个唯一的标识。本枚举就是这些标识的集合。
 * 路由规划阶段根据用户问题的特征选择一个 ExecutionMode，
 * 然后 ai.knowhub.chat.rag.executor.ConversationExecutorRegistry
 * 根据这个模式找到对应的执行器。
 *
 * 模式选择的决策逻辑
 * 
 *   用户问文档结构关系（上一节/下一节/子章节）→ #GRAPH_ONLY
 *   用户问某个章节或编号项的具体内容 → #GRAPH_THEN_EVIDENCE
 *   用户问需要基于知识文档回答的问题 → #RETRIEVAL
 *   用户问超出知识库范围的开放式问题 → #REACT_AGENT
 *   系统无法确定用户意图，需要用户补充信息 → #CLARIFICATION
 * @see ai.knowhub.chat.rag.executor.ConversationExecutor 执行器接口
 * @see ai.knowhub.chat.rag.executor.ConversationExecutorRegistry 执行器注册表
 */
public enum ExecutionMode {

    /**
     * 结构图直答模式。
     *
     * 适用于用户只问文档结构关系的问题，例如"这个章节包含哪些小节"、"上一节/下一节是什么"、
     * "某章节属于哪个父章节"。该模式通常由 GraphOnlyExecutor 执行，只查询结构图中的章节、
     * 父子关系或兄弟关系，不再进入向量/关键词证据检索，也不调用大模型生成长答案。
     */
    GRAPH_ONLY,

    /**
     * 结构图定位后取证模式。
     *
     * 适用于问题需要先通过结构图定位章节，再读取章节正文或编号项证据的场景，例如"某章节第 3 步是什么"、
     * "哪一步要求执行某个动作"。该模式由 GraphThenEvidenceExecutor 执行，会先根据导航锚点找到
     * 目标章节，再在章节树内递归查找 item、关键词命中的步骤或章节正文，最后把结构化证据渲染成回答。
     */
    GRAPH_THEN_EVIDENCE,

    /**
     * 普通知识库检索问答模式。
     *
     * 适用于大多数需要基于知识文档内容回答的问题。该模式由 RagChatExecutor 执行，会根据规划阶段
     * 得到的检索问题、子问题、文档范围，走向量检索、关键词检索、RRF 融合、父块提升、可选 rerank、Prompt
     * 预算组装，然后调用模型基于证据流式生成答案。
     */
    RETRIEVAL,

    /**
     * 开放式 ReAct Agent 模式。
     *
     * 适用于固定 RAG 或结构图路径无法覆盖的问题，或者需要 Agent 自主判断是否调用工具的场景。
     * 该模式由 ReactAgentExecutor 执行，会把规划后的 agentQuestion 交给 ReAct Agent，
     * 由 Agent 自主进行推理、工具调用和最终回答输出。
     */
    REACT_AGENT,

    /**
     * 澄清模式。
     *
     * 适用于路由阶段发现候选文档、知识范围或用户意图存在歧义，暂时不能稳定选择某个执行路径的场景。
     * 该模式由 ClarificationExecutor 执行，不进行检索或模型生成，而是直接返回澄清问题，
     * 引导用户补充更明确的文档名、主题或关键词。
     */
    CLARIFICATION,

    /**
     * 旧版 RAG 对话模式。
     *
     * 该枚举值已废弃，保留它主要是为了兼容历史数据、历史配置或旧路由结果。新的普通知识库问答应使用
     * #RETRIEVAL，不要再为新逻辑依赖该模式。
     */
    @Deprecated
    RAG_CHAT
}
