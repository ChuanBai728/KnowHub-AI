package ai.knowhub.prompt;

/**
 * Prompt 模板名称常量类。
 *
 * 该类集中定义了系统中所有 Prompt 模板的名称常量。
 * 每个常量对应 resources/prompt/ 目录下的一个 StringTemplate（.st）模板文件。
 * 使用常量类统一管理模板名称，避免硬编码字符串分散在各处，提高代码可维护性。
 *
 * 在架构中的角色
 * 属于"Prompt 管理层"，与 PromptTemplateService 配合使用。
 * Service 负责加载和渲染模板，该类负责定义模板的标识名称。
 * 在业务代码中通过 PromptTemplateNames.XXX 引用模板名称，
 * 再由 PromptTemplateService#render() 方法加载对应模板并填充变量。
 *
 * 设计模式
 * 采用"常量类"模式，将所有模板名称集中管理：
 * 
 *   避免魔法字符串（Magic String）分散在代码各处
 *   IDE 支持重构（重命名常量时自动更新所有引用）
 *   编译期检查，拼写错误会被立即发现
 * 模板分类说明
 * 
 *   <b>agent-question</b> - Agent 生成回答时的提问模板
 *   <b>chat-query-rewrite</b> - 用户查询改写模板（用于 RAG 流程的查询优化）
 *   <b>conversation-summary-*</b> - 对话摘要相关模板（用于记忆压缩策略）
 *   <b>document-*</b> - 文档处理相关模板（用于文档结构分析和意图识别）
 *   <b>rag-answer-*</b> - RAG 回答生成相关模板（用于基于检索结果的答案生成）
 *   <b>recommendation-user</b> - 推荐内容的用户提示模板
 * 
 */
public final class PromptTemplateNames {

    /** Agent 提问模板，用于生成 AI 回答时的系统提示 */
    public static final String AGENT_QUESTION = "agent-question";

    /** 聊天查询改写模板，将用户原始问题改写为更适合检索的形式 */
    public static final String CHAT_QUERY_REWRITE = "chat-query-rewrite";

    /** 对话摘要合并模板，将多段摘要合并为统一的摘要 */
    public static final String CONVERSATION_SUMMARY_MERGE = "conversation-summary-merge";

    /** 对话摘要系统提示模板，指导 LLM 如何生成对话摘要 */
    public static final String CONVERSATION_SUMMARY_SYSTEM = "conversation-summary-system";

    /** 文档图谱意图识别模板，判断是否仅使用图谱进行检索 */
    public static final String DOCUMENT_GRAPH_ONLY_INTENT = "document-graph-only-intent";

    /** 文档 LLM 分割模板，使用 LLM 进行智能文档分块 */
    public static final String DOCUMENT_LLM_SPLIT = "document-llm-split";

    /** 文档结构歧义检测模板，检测文档结构中的歧义内容 */
    public static final String DOCUMENT_STRUCTURE_AMBIGUITY = "document-structure-ambiguity";

    /** 文档结构歧义候选模板，处理歧义检测的候选结果 */
    public static final String DOCUMENT_STRUCTURE_AMBIGUITY_CANDIDATE = "document-structure-ambiguity-candidate";

    /** RAG 回答 - 文档引用模板，基于文档检索结果生成带引用的回答 */
    public static final String RAG_ANSWER_DOCUMENT_REFERENCE = "rag-answer-document-reference";

    /** RAG 回答 - 无证据模板，当没有找到相关证据时的回答策略 */
    public static final String RAG_ANSWER_NO_EVIDENCE = "rag-answer-no-evidence";

    /** RAG 回答 - 省略证据模板，处理被省略的证据信息 */
    public static final String RAG_ANSWER_OMITTED_EVIDENCE = "rag-answer-omitted-evidence";

    /** RAG 回答 - 复用引用模板，复用之前已有的引用信息 */
    public static final String RAG_ANSWER_REUSE_REFERENCE = "rag-answer-reuse-reference";

    /** RAG 回答 - 子问题证据模板，基于子问题的检索证据生成回答 */
    public static final String RAG_ANSWER_SUB_QUESTION_EVIDENCE = "rag-answer-sub-question-evidence";

    /** RAG 回答 - 系统提示模板，RAG 回答生成的系统级提示 */
    public static final String RAG_ANSWER_SYSTEM = "rag-answer-system";

    /** RAG 回答 - 用户提示模板，RAG 回答生成的用户级提示 */
    public static final String RAG_ANSWER_USER = "rag-answer-user";

    /** RAG 回答 - 网页引用模板，基于联网搜索结果生成带引用的回答 */
    public static final String RAG_ANSWER_WEB_REFERENCE = "rag-answer-web-reference";

    /** 推荐内容用户提示模板，生成个性化推荐内容 */
    public static final String RECOMMENDATION_USER = "recommendation-user";

    /**
     * 私有构造方法，防止实例化。
     *
     * 该类仅包含静态常量，不需要创建实例。
     * 私有构造方法是工具类/常量类的标准实践。
     */
    private PromptTemplateNames() {
    }
}
