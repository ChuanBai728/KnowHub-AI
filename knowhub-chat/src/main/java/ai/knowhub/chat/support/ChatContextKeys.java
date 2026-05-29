package ai.knowhub.chat.support;

/**
 * 【聊天上下文键常量类】
 *
 * 作用：定义对话处理过程中用于在上下文（Context）中存取数据的所有键名常量。
 * 这些键用于在不同的处理组件之间共享数据，如事件发射器、调试追踪器、
 * 检索结果等。
 *
 * 所属架构位置：属于聊天支持层（Support Layer），是整个对话处理管道的「共享键注册表」。
 * 各处理组件通过这些常量键在上下文 Map 中读写数据，实现了组件间的松耦合通信。
 *
 * 设计模式说明：
 * 1. 「常量类模式（Constants Class）」—— 集中管理所有上下文键名，避免硬编码。
 * 2. 「不可实例化」—— 私有构造函数防止被实例化，所有成员都是静态常量。
 *
 * 使用场景：
 * - 事件发射器使用 EVENT_SINK 键获取 SSE 事件发射器
 * - 调试追踪器使用 DEBUG_TRACE 键存取调试信息
 * - RAG 检索器使用 REFERENCES 键存储检索结果
 * - 时间敏感查询使用 CURRENT_DATE 键获取当前日期
 *
 * @author knowhub
 */
public final class ChatContextKeys {

    /**
     * 事件发射器键（EVENT_SINK）
     * 存储 SSE（Server-Sent Events）事件发射器，用于向客户端实时推送对话事件。
     * 类型：Sinks.Many<String>
     */
    public static final String EVENT_SINK = "chat.event.sink";

    /**
     * 事件元数据键（EVENT_METADATA）
     * 存储流式事件的元数据信息（会话ID、交换ID等）。
     * 类型：StreamEventMetadata
     */
    public static final String EVENT_METADATA = "chat.event.metadata";

    /**
     * 调试追踪键（DEBUG_TRACE）
     * 存储对话处理的全链路调试追踪信息。
     * 类型：ChatDebugTrace
     */
    public static final String DEBUG_TRACE = "chat.debug.trace";

    /**
     * 追踪 ID 键（TRACE_ID）
     * 存储本次对话请求的唯一追踪 ID，用于日志关联和问题排查。
     * 类型：String
     */
    public static final String TRACE_ID = "chat.trace.id";

    /**
     * 搜索引用键（REFERENCES）
     * 存储 RAG 检索和网络搜索的结果引用列表。
     * 类型：List<SearchReference>
     */
    public static final String REFERENCES = "chat.references";

    /**
     * 使用的工具键（USED_TOOLS）
     * 存储本次对话中使用的工具名称列表。
     * 类型：List<String>
     */
    public static final String USED_TOOLS = "chat.used.tools";

    /**
     * 思考步骤键（THINKING_STEPS）
     * 存储 AI 处理过程中的中间思考步骤。
     * 类型：List<String>
     */
    public static final String THINKING_STEPS = "chat.thinking.steps";

    /**
     * 用户问题键（QUESTION）
     * 存储当前处理的用户问题文本。
     * 类型：String
     */
    public static final String QUESTION = "chat.question";

    /**
     * 对话模式键（CHAT_MODE）
     * 存储当前使用的对话查询模式。
     * 类型：ChatQueryMode
     */
    public static final String CHAT_MODE = "chat.mode";

    /**
     * 当前日期键（CURRENT_DATE）
     * 存储当前日期对象，用于时间敏感型查询的处理。
     * 类型：LocalDate 或类似日期类型
     */
    public static final String CURRENT_DATE = "chat.current.date";

    /**
     * 当前日期文本键（CURRENT_DATE_TEXT）
     * 存储当前日期的文本表示，如 "2026年5月27日"。
     * 类型：String
     */
    public static final String CURRENT_DATE_TEXT = "chat.current.date.text";

    /**
     * 选中的文档 ID 键（SELECTED_DOCUMENT_ID）
     * 在文档对话模式下，存储用户选择的文档 ID。
     * 类型：String
     */
    public static final String SELECTED_DOCUMENT_ID = "chat.selected.document.id";

    /**
     * 选中的文档名称键（SELECTED_DOCUMENT_NAME）
     * 在文档对话模式下，存储用户选择的文档名称。
     * 类型：String
     */
    public static final String SELECTED_DOCUMENT_NAME = "chat.selected.document.name";

    /**
     * 选中的任务 ID 键（SELECTED_TASK_ID）
     * 存储用户选择的任务 ID。
     * 类型：String
     */
    public static final String SELECTED_TASK_ID = "chat.selected.task.id";

    /**
     * 私有构造函数，防止实例化。
     * 这是一个纯常量类，所有成员都是 public static final。
     */
    private ChatContextKeys() {
    }
}
