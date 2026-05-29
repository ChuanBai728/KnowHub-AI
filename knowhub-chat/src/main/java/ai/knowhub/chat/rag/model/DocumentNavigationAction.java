package ai.knowhub.chat.rag.model;

/**
 * 文档导航动作枚举 —— 描述用户在文档结构中的"移动方向"。
 *
 * 在 RAG 流水线中的角色
 * 在 GRAPH_ONLY 或 GRAPH_THEN_EVIDENCE 模式下，路由规划阶段需要判断用户想在文档结构中
 * 做什么样的"导航动作"。不同动作对应不同的图查询方式。
 *
 * 各动作说明
 * 
 *   #TOPIC_CONTINUE：继续讨论当前话题，不切换章节
 *   #TOPIC_SWITCH：切换到不同话题（不同章节）
 *   #FRESH_TOPIC：全新的问题，与之前的对话无关
 *   #SIBLING_SECTION_SWITCH：切换到同级章节（如从 5.1 到 5.2）
 *   #CHILD_SECTION_DESCEND：深入到子章节（如从 5 到 5.1）
 *   #ANCESTOR_SECTION_RETURN：回到上级/祖先章节（如从 5.1.2 回到 5）
 *   #ITEM_REFERENCE：引用章节内的某个编号项（如"第 3 步"）
 *   #SECTION_ADJACENCY_LOOKUP：查询相邻章节（上一节/下一节/父章节）
 * @see DocumentNavigationDecision 导航决策，使用本枚举描述动作类型
 * @see GraphOnlyExecutor 使用 SECTION_ADJACENCY_LOOKUP 做邻接查询
 * @see GraphThenEvidenceExecutor 使用 ITEM_REFERENCE 定位编号项证据
 */
public enum DocumentNavigationAction {

    /**
     * 继续讨论当前话题。
     *
     * 用户在当前章节的范围内继续提问，不需要切换章节。
     * 例如用户先问了"5.1 的内容是什么"，然后继续问"那它有什么要求"。
     */
    TOPIC_CONTINUE,

    /**
     * 切换到不同话题。
     *
     * 用户想从当前话题切换到另一个话题（通常是不同章节）。
     * 例如用户先问了"5.1 的内容"，然后问"6.2 的内容是什么"。
     */
    TOPIC_SWITCH,

    /**
     * 全新话题。
     *
     * 用户提出了一个全新的问题，与之前的对话完全没有关系。
     * 通常发生在会话刚开始或用户主动切换到完全不同的主题。
     */
    FRESH_TOPIC,

    /**
     * 切换到同级兄弟章节。
     *
     * 用户想从当前章节切换到同一父章节下的另一个章节。
     * 例如从 5.1 切换到 5.2（都是 5 的子章节）。
     */
    SIBLING_SECTION_SWITCH,

    /**
     * 深入到子章节。
     *
     * 用户想从当前章节深入到它的某个子章节。
     * 例如从第 5 章深入到 5.1（5 的子章节）。
     */
    CHILD_SECTION_DESCEND,

    /**
     * 回到上级或祖先章节。
     *
     * 用户想从当前章节回到它的上级或更高级的祖先章节。
     * 例如从 5.1.2 回到 5.1 或直接回到第 5 章。
     */
    ANCESTOR_SECTION_RETURN,

    /**
     * 引用章节内的编号项。
     *
     * 用户想了解章节内某个具体编号项的内容。
     * 例如"第 3 步是什么"、"哪个步骤要求检查设备"。
     *
     * @see ConversationItemAnchor 编号项锚点
     */
    ITEM_REFERENCE,

    /**
     * 查询相邻章节关系。
     *
     * 用户想了解当前章节的上一节、下一节或父章节是什么。
     * 例如"这一节的上一节是什么"、"它属于哪个父章节"。
     *
     * 这种查询由 GraphOnlyExecutor 处理，直接查结构图，不调用大模型。
     */
    SECTION_ADJACENCY_LOOKUP
}
