package ai.knowhub.chat.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.Data;
import ai.knowhub.chat.model.debug.ChatDebugTrace;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.support.StreamEventMetadata;
import ai.knowhub.enums.ChatQueryMode;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 【任务信息】
 *
 * 封装一次会话任务从启动到完成的全部运行时状态。
 * 可以理解为一个"任务上下文"，贯穿整个会话执行生命周期。
 *
 * 生命周期：
 * 1. 创建：BusinessChatService.createTaskInfo() 中创建
 * 2. 注册：通过 ChatRuntimeRegistry.register() 注册到运行时注册表
 * 3. 执行：绑定到 SSE 流，模型输出写入 answerBuffer
 * 4. 完成/失败/停止：通过 finalized 标志保证只收尾一次
 * 5. 清理：cleanup() 中释放所有资源
 *
 * 关键设计：
 * - 使用 AtomicBoolean finalized 保证会话只结束一次（成功/失败/停止互斥）
 * - 使用 AtomicLong firstResponseTimeMs 记录首次响应时间（线程安全）
 * - 使用 volatile 修饰 executionPlan、disposable 等可能被多线程修改的字段
 * - answerBuffer 使用 StringBuffer（线程安全的字符串拼接）
 * - thinkingSteps、references 使用 Collections.synchronizedList（线程安全列表）
 * - usedTools 使用 ConcurrentHashMap.newKeySet()（线程安全集合）
 *
 * 与 BusinessChatService 的关系：
 * TaskInfo 是 BusinessChatService 的"内部状态容器"，
 * BusinessChatService 的各种私有方法都通过 TaskInfo 传递和共享状态。
 *
 * 注意：虽然使用了 @Data 注解生成 getter/setter，但也手动定义了 record 风格的
 * 访问器方法（如 conversationId()、exchangeId() 等），这是为了兼容不同调用方的风格偏好。
 */
@Data
public class TaskInfo {

    // ==================== 基本会话信息 ====================

    /** 会话唯一标识 */
    private final String conversationId;

    /** 本轮问答回合 ID */
    private final long exchangeId;

    /** 用户提问内容 */
    private final String question;

    /** 查询模式（开放式/自动/文档问答） */
    private final ChatQueryMode chatMode;

    /** 本次追踪的唯一标识（UUID） */
    private final String traceId;

    /** 选中的知识文档 ID（文档问答模式下有值） */
    private final Long selectedDocumentId;

    /** 选中的知识文档名称 */
    private final String selectedDocumentName;

    /** 选中文档的索引任务 ID */
    private final Long selectedTaskId;

    /** 当前日期（上海时区） */
    private final LocalDate currentDate;

    /** 当前日期的中文文本表示 */
    private final String currentDateText;

    // ==================== 执行计划和调试信息 ====================

    /**
     * 执行计划（volatile 保证多线程可见性）。
     * 由 ChatPreparationOrchestrator 编排后设置，
     * 包含执行模式、改写问题、检索问题等信息。
     */
    private volatile ConversationExecutionPlan executionPlan;

    /**
     * 调试追踪信息（volatile 保证多线程可见性）。
     * 记录执行过程中的各种调试数据，如检索笔记、使用的通道等。
     */
    private volatile ChatDebugTrace debugTrace;

    // ==================== 运行时基础设施 ====================

    /**
     * Spring AI Alibaba 的运行配置。
     * 包含 threadId（= conversationId）和 context（跨组件共享的上下文 Map）。
     * Agent、执行器、工具都能从 context 中存取数据。
     */
    private final RunnableConfig runnableConfig;

    /** 追踪记录器，用于记录执行过程中的阶段、模型调用、检索结果等 */
    private final ConversationTraceRecorder traceRecorder;

    /**
     * SSE 输出通道（unicast sink）。
     * 所有需要推送给前端的事件（文本、思考步骤、引用、错误等）都写入这里。
     * 使用 Sinks.Many.unicast() 创建，保证只有一个订阅者。
     */
    private final Sinks.Many<String> sink;

    /** SSE 事件元数据，包含 conversationId 和 exchangeId，附加到每个事件中 */
    private final StreamEventMetadata eventMetadata;

    // ==================== 租约信息 ====================

    /** Redis 租约的键名 */
    private final String leaseKey;

    /** 租约持有者令牌 */
    private final String leaseOwnerToken;

    // ==================== 运行时积累的数据 ====================

    /**
     * 回答缓冲区（线程安全）。
     * 模型生成的文本片段逐步追加到这里，形成完整的回答。
     * 使用 StringBuffer 而非 StringBuilder，因为可能被多线程访问。
     */
    private final StringBuffer answerBuffer = new StringBuffer();

    /**
     * 思考步骤列表（线程安全）。
     * Agent 在推理过程中产生的思考步骤，例如"正在分析问题"、"正在调用搜索工具"等。
     */
    private final List<String> thinkingSteps;

    /**
     * 引用来源列表（线程安全）。
     * RAG 检索返回的文档切片信息，用于前端展示"参考来源"。
     */
    private final List<SearchReference> references;

    /**
     * 已使用的工具集合（线程安全）。
     * 记录本次会话中使用过的所有工具名称，如 "tavily_search"、"calculator" 等。
     */
    private final Set<String> usedTools;

    // ==================== 时间和状态控制 ====================

    /** 任务开始时间戳（毫秒），用于计算总耗时 */
    private final long startTime;

    /**
     * 首次响应时间（毫秒，原子操作）。
     * 从任务开始到模型输出第一个文本片段的耗时，用于衡量响应速度。
     * 使用 AtomicLong 保证多线程安全的 CAS 操作。
     */
    private final AtomicLong firstResponseTimeMs = new AtomicLong(0L);

    /**
     * 是否已终结标志（原子操作）。
     * 保证会话只会被收尾一次（成功、失败、停止三种状态互斥）。
     * 使用 compareAndSet(false, true) 实现无锁的幂等控制。
     */
    private final AtomicBoolean finalized = new AtomicBoolean(false);

    // ==================== 可取消的订阅 ====================

    /**
     * 主执行流的 Disposable（volatile 保证多线程可见性）。
     * 用于在停止会话时取消正在执行的 Flux 订阅。
     */
    private volatile Disposable disposable;

    /**
     * 租约续期任务的 Disposable（volatile 保证多线程可见性）。
     * 用于在停止会话时取消租约续期的定时任务。
     */
    private volatile Disposable leaseRenewalDisposable;

    /**
     * 全参构造函数。
     * 由 BusinessChatService.createTaskInfo() 调用，传入所有初始化参数。
     */
    public TaskInfo(String conversationId,
                    long exchangeId,
                    String question,
                    ChatQueryMode chatMode,
                    String traceId,
                    Long selectedDocumentId,
                    String selectedDocumentName,
                    Long selectedTaskId,
                    LocalDate currentDate,
                    String currentDateText,
                    ConversationExecutionPlan executionPlan,
                    ChatDebugTrace debugTrace,
                    RunnableConfig runnableConfig,
                    ConversationTraceRecorder traceRecorder,
                    Sinks.Many<String> sink,
                    StreamEventMetadata eventMetadata,
                    String leaseKey,
                    String leaseOwnerToken,
                    List<String> thinkingSteps,
                    List<SearchReference> references,
                    Set<String> usedTools,
                    long startTime) {
        this.conversationId = conversationId;
        this.exchangeId = exchangeId;
        this.question = question;
        this.chatMode = chatMode;
        this.traceId = traceId;
        this.selectedDocumentId = selectedDocumentId;
        this.selectedDocumentName = selectedDocumentName;
        this.selectedTaskId = selectedTaskId;
        this.currentDate = currentDate;
        this.currentDateText = currentDateText;
        this.executionPlan = executionPlan;
        this.debugTrace = debugTrace;
        this.runnableConfig = runnableConfig;
        this.traceRecorder = traceRecorder;
        this.sink = sink;
        this.eventMetadata = eventMetadata;
        this.leaseKey = leaseKey;
        this.leaseOwnerToken = leaseOwnerToken;
        this.thinkingSteps = thinkingSteps;
        this.references = references;
        this.usedTools = usedTools;
        this.startTime = startTime;
    }

    // ==================== Record 风格的访问器方法 ====================
    // 以下方法提供了 record 风格的访问接口，与 @Data 生成的 getter 并存

    /** 获取会话 ID */
    public String conversationId() {
        return conversationId;
    }

    /** 获取本轮问答回合 ID */
    public long exchangeId() {
        return exchangeId;
    }

    /** 获取用户提问内容 */
    public String question() {
        return question;
    }

    /** 获取查询模式 */
    public ChatQueryMode chatMode() {
        return chatMode;
    }

    /** 获取追踪 ID */
    public String traceId() {
        return traceId;
    }

    /** 获取运行配置 */
    public RunnableConfig runnableConfig() {
        return runnableConfig;
    }

    /** 获取追踪记录器 */
    public ConversationTraceRecorder traceRecorder() {
        return traceRecorder;
    }

    /** 获取选中的文档 ID */
    public Long selectedDocumentId() {
        return selectedDocumentId;
    }

    /** 获取选中的文档名称 */
    public String selectedDocumentName() {
        return selectedDocumentName;
    }

    /** 获取选中的任务 ID */
    public Long selectedTaskId() {
        return selectedTaskId;
    }

    /** 获取当前日期 */
    public LocalDate currentDate() {
        return currentDate;
    }

    /** 获取当前日期文本 */
    public String currentDateText() {
        return currentDateText;
    }

    /** 获取执行计划 */
    public ConversationExecutionPlan executionPlan() {
        return executionPlan;
    }

    /** 设置执行计划 */
    public void setExecutionPlan(ConversationExecutionPlan executionPlan) {
        this.executionPlan = executionPlan;
    }

    /** 获取调试追踪信息 */
    public ChatDebugTrace debugTrace() {
        return debugTrace;
    }

    /** 设置调试追踪信息 */
    public void setDebugTrace(ChatDebugTrace debugTrace) {
        this.debugTrace = debugTrace;
    }

    /** 获取 SSE 输出通道 */
    public Sinks.Many<String> sink() {
        return sink;
    }

    /** 获取事件元数据 */
    public StreamEventMetadata eventMetadata() {
        return eventMetadata;
    }

    /** 获取租约键名 */
    public String leaseKey() {
        return leaseKey;
    }

    /** 获取租约持有者令牌 */
    public String leaseOwnerToken() {
        return leaseOwnerToken;
    }

    /** 获取回答缓冲区 */
    public StringBuffer answerBuffer() {
        return answerBuffer;
    }

    /** 获取思考步骤列表 */
    public List<String> thinkingSteps() {
        return thinkingSteps;
    }

    /** 获取引用来源列表 */
    public List<SearchReference> references() {
        return references;
    }

    /** 获取已使用的工具集合 */
    public Set<String> usedTools() {
        return usedTools;
    }

    /** 获取任务开始时间 */
    public long startTime() {
        return startTime;
    }

    /** 获取首次响应时间（原子操作） */
    public AtomicLong firstResponseTimeMs() {
        return firstResponseTimeMs;
    }

    /** 获取终结标志（原子操作） */
    public AtomicBoolean finalized() {
        return finalized;
    }

    /** 获取主执行流的 Disposable */
    public Disposable disposable() {
        return disposable;
    }

    /** 获取租约续期任务的 Disposable */
    public Disposable leaseRenewalDisposable() {
        return leaseRenewalDisposable;
    }
}
