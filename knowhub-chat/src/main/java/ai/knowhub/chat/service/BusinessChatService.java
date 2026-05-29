package ai.knowhub.chat.service;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.config.ChatAgentProperties;
import ai.knowhub.chat.dto.ChatRequestDto;
import ai.knowhub.chat.dto.ConversationSessionListQueryDto;
import ai.knowhub.chat.model.ConversationExchangeDetailVo;
import ai.knowhub.chat.model.ConversationExchangeVo;
import ai.knowhub.chat.model.KnowledgeDocumentOptionVo;
import ai.knowhub.chat.model.ConversationMemorySummaryVo;
import ai.knowhub.chat.model.ConversationSessionVo;
import ai.knowhub.chat.model.ChannelExecutionVo;
import ai.knowhub.chat.model.RetrievalResultVo;
import ai.knowhub.chat.model.StageBenchmarkVo;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.model.debug.ChatDebugTrace;
import ai.knowhub.chat.rag.executor.ConversationExecutor;
import ai.knowhub.chat.rag.executor.ConversationExecutorRegistry;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.document.model.KnowledgeDocumentDescriptor;
import ai.knowhub.document.service.DocumentKnowledgeService;
import ai.knowhub.chat.rag.service.ChatPreparationOrchestrator;
import ai.knowhub.chat.support.ChatContextKeys;
import ai.knowhub.chat.support.SinkEmitHelper;
import ai.knowhub.chat.support.StreamEventMetadata;
import ai.knowhub.chat.support.StreamEventWriter;
import ai.knowhub.chat.vo.ConversationResetVo;
import ai.knowhub.chat.vo.ConversationSessionListVo;
import ai.knowhub.chat.vo.ConversationStopVo;
import ai.knowhub.prompt.PromptTemplateNames;
import ai.knowhub.prompt.PromptTemplateService;
import ai.knowhub.enums.ChatTurnStatus;
import ai.knowhub.enums.ChatQueryMode;
import ai.knowhub.exception.KnowHubFrameException;
import ai.knowhub.lease.RedisLeaseManager;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【业务聊天服务】
 *
 * 整个 AI Agent 聊天系统的核心服务类，负责协调一次完整的会话流程。
 * 从用户发起提问到 AI 生成回答，所有环节都在这个服务中编排。
 *
 * 整体架构概览：
 * ┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
 * │  Controller  │────>│ BusinessChatService│────>│ ReactAgent / RAG │
 * │  (SSE 接口)  │<────│  (编排调度中心)    │<────│  (执行引擎)      │
 * └─────────────┘     └──────────────────┘     └─────────────────┘
 *                              │
 *                    ┌─────────┼─────────┐
 *                    v         v         v
 *              ┌────────┐ ┌────────┐ ┌────────┐
 *              │归档存储│ │记忆服务│ │追踪记录│
 *              └────────┘ └────────┘ └────────┘
 *
 * 一次完整的会话流程：
 * 1. 用户通过 SSE 接口发起提问（openConversationStream）
 * 2. 构建启动计划（buildLaunchPlan）：校验参数、解析查询模式
 * 3. 申请 Redis 租约（claimConversationLease）：防止并发执行
 * 4. 引导会话（bootstrapConversation）：创建回合记录、注册运行时任务
 * 5. 绑定客户端通道（bindClientChannel）：将 SSE 流绑定到 Reactor Sink
 * 6. 激活生成（activateGeneration）：浏览器连接后才真正启动模型调用
 * 7. 编排执行计划（prepareExecutionPlan）：决定使用哪种执行策略
 * 8. 执行回答生成（buildConversationExecution）：调用 Agent/RAG 生成回答
 * 9. 收尾（finishSuccessfully/finishWithFailure）：保存数据、生成推荐
 * 10. 清理（cleanup）：释放租约、移除运行时注册
 *
 * 核心设计原则：
 * - 响应式编程：使用 Project Reactor 的 Flux/Mono 实现非阻塞的 SSE 流
 * - 租约机制：Redis 分布式锁 + JVM 内存注册表双重保护
 * - 策略模式：通过 ConversationExecutor 接口支持多种执行策略
 * - 可观测性：完整的追踪记录、性能基准、调试轨迹
 *
 * 依赖的外部服务：
 * - MySQL: 会话归档、检查点、追踪、摘要、检索观察
 * - Redis: 分布式租约（Redisson）
 * - DashScope/SiliconFlow: 大模型调用（通过 Spring AI）
 *
 * @see StreamLaunchPlan 启动计划
 * @see TaskInfo 任务信息
 * @see BootstrapResult 引导结果
 * @see ChatRuntimeRegistry 运行时注册表
 */
@Slf4j
@AllArgsConstructor
@Service
public class BusinessChatService {

    // ==================== 常量定义 ====================

    /** 时区：上海（用于日期计算和格式化） */
    private static final ZoneId CHAT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /** Redis 租约键前缀 */
    private static final String CHAT_RUNNING_LEASE_PREFIX = "chat:running:";

    /** 租约过期时间：30 秒（服务异常退出时自动释放） */
    private static final Duration CHAT_RUNNING_LEASE_TTL = Duration.ofSeconds(30);

    /** 租约续期间隔：10 秒（长回答时周期性续租） */
    private static final Duration CHAT_RUNNING_LEASE_RENEW_INTERVAL = Duration.ofSeconds(10);

    // ==================== 依赖注入（通过 @AllArgsConstructor 自动注入） ====================

    /** ReactAgent 实例，Spring AI Alibaba 的 ReAct 模式 Agent */
    private final ReactAgent businessChatReactAgent;

    /** 检查点管理器，管理 Agent 的状态存档 */
    private final ChatCheckpointManager checkpointManager;

    /** 聊天 Agent 配置属性 */
    private final ChatAgentProperties chatAgentProperties;

    /** 会话归档存储接口，负责会话和回合的持久化 */
    private final ConversationArchiveStore conversationArchiveStore;

    /** 聊天运行时注册表，维护正在执行的任务 */
    private final ChatRuntimeRegistry chatRuntimeRegistry;

    /** 推荐追问服务，生成引导用户继续提问的建议 */
    private final RecommendationService recommendationService;

    /** SSE 事件写入器，将各种事件格式化为 SSE 格式的字符串 */
    private final StreamEventWriter streamEventWriter;

    /** Redis 租约管理器，提供分布式锁功能 */
    private final RedisLeaseManager redisLeaseManager;

    /** 聊天准备编排器，负责查询改写、执行计划编排 */
    private final ChatPreparationOrchestrator chatPreparationOrchestrator;

    /** 会话执行器注册表，根据执行模式选择对应的执行器 */
    private final ConversationExecutorRegistry conversationExecutorRegistry;

    /** 会话记忆服务，管理对话历史的摘要压缩 */
    private final ConversationMemoryService conversationMemoryService;

    /** 文档知识服务，查询可检索的文档列表 */
    private final DocumentKnowledgeService documentKnowledgeService;

    /** 追踪阶段存储，持久化执行过程中的阶段数据 */
    private final ConversationTraceStageStore conversationTraceStageStore;

    /** 检索观察存储，持久化检索结果和通道执行详情 */
    private final RetrievalObserveStore retrievalObserveStore;

    /** 阶段性能基准服务，收集各阶段的耗时统计 */
    private final StageBenchmarkService stageBenchmarkService;

    /** 提示词模板服务，渲染模板化的提示词 */
    private final PromptTemplateService promptTemplateService;

    // ==================== 公开 API 方法 ====================

    /**
     * 打开一个会话的 SSE 流（入口方法）。
     *
     * 使用 Flux.defer 延迟执行：只有当 Controller 返回的 Flux 被浏览器真正订阅时，
     * 才会触发 openDeferredConversationStream() 的执行。
     * 这样做的好处是：避免 Controller 方法返回时就提前执行模型调用。
     *
     * @param request 聊天请求 DTO，包含问题、会话 ID、查询模式等
     * @return SSE 事件流（Flux<String>），每个元素是一个 SSE 事件字符串
     */
    public Flux<String> openConversationStream(ChatRequestDto request) {

        // Flux.defer 表示"有人订阅时才真正启动任务"，避免 Controller 返回时就提前执行。
        return Flux.defer(() -> openDeferredConversationStream(request));
    }

    /**
     * 延迟执行的会话流（内部方法）。
     *
     * 核心流程：
     * 1. 构建启动计划
     * 2. 申请 Redis 租约（防止并发）
     * 3. 引导会话（创建回合、注册任务）
     * 4. 返回 SSE 流
     */
    private Flux<String> openDeferredConversationStream(ChatRequestDto request) {

        log.info("======request内容：{}", JSON.toJSONString(request));
        StreamLaunchPlan launchPlan = null;
        boolean leaseClaimed = false;
        try {

            // 启动计划会把用户问题、会话模式、当前日期和选中文档统一整理好。
            launchPlan = buildLaunchPlan(request);

            // 同一个 conversationId 同一时间只允许跑一个回答，防止用户连点导致上下文互相覆盖。
            leaseClaimed = claimConversationLease(launchPlan);
            if (!leaseClaimed) {
                return rejectionFlux("该会话当前正在执行中，请稍后再试", launchPlan.getConversationId(), null);
            }

            BootstrapResult bootstrapResult = bootstrapConversation(launchPlan);
            if (StrUtil.isNotBlank(bootstrapResult.getRejectionMessage())) {
                return rejectionFlux(bootstrapResult.getRejectionMessage(), launchPlan.getConversationId(), null);
            }
            return bootstrapResult.getOutbound();
        }
        catch (RuntimeException exception) {
            log.error("会话启动失败, conversationId={}, question={}",
                launchPlan == null ? "" : launchPlan.getConversationId(),
                request.getQuestion(),
                exception);
            if (leaseClaimed && launchPlan != null) {
                releaseLeaseQuietly(launchPlan.getLeaseKey(), launchPlan.getLeaseOwnerToken());
            }
            return rejectionFlux(
                buildErrorMessage(exception),
                launchPlan == null ? null : launchPlan.getConversationId(),
                null
            );
        }
    }

    /**
     * 引导会话：创建问答回合记录，注册运行时任务，绑定 SSE 通道。
     *
     * @param launchPlan 启动计划
     * @return 引导结果（成功时包含 SSE 流，失败时包含拒绝消息）
     */
    private BootstrapResult bootstrapConversation(StreamLaunchPlan launchPlan) {

        ConversationExchangeVo exchangeVo = null;
        try {

            // 每次用户提问都会创建一个 exchange，可以理解成一次"问答回合"的归档记录。
            exchangeVo = conversationArchiveStore.startExchange(
                launchPlan.getConversationId(),
                launchPlan.getQuestion(),
                launchPlan.getChatMode(),
                launchPlan.getSelectedDocumentId(),
                launchPlan.getSelectedDocumentName()
            );

            TaskInfo taskInfo = createTaskInfo(launchPlan, exchangeVo);

            if (!chatRuntimeRegistry.register(taskInfo)) {

                failBootstrappedExchange(launchPlan.getConversationId(), exchangeVo.getExchangeId(), "该会话当前正在执行中，请稍后再试");

                releaseLeaseQuietly(launchPlan.getLeaseKey(), launchPlan.getLeaseOwnerToken());
                return BootstrapResult.rejected("该会话当前正在执行中，请稍后再试");
            }

            return BootstrapResult.ready(bindClientChannel(taskInfo));
        }
        catch (RuntimeException exception) {

            releaseLeaseQuietly(launchPlan.getLeaseKey(), launchPlan.getLeaseOwnerToken());
            if (exchangeVo != null) {

                failBootstrappedExchange(launchPlan.getConversationId(), exchangeVo.getExchangeId(), buildErrorMessage(exception));
            }
            return BootstrapResult.rejected(buildErrorMessage(exception));
        }
    }

    /**
     * 创建任务信息对象。
     *
     * 将启动计划和回合信息组装成 TaskInfo，并初始化所有运行时上下文：
     * - 创建 SSE Sink（unicast 模式）
     * - 创建 RunnableConfig（Agent 运行配置）
     * - 初始化思考步骤、引用来源、已用工具等线程安全集合
     * - 创建追踪记录器
     * - 将所有上下文数据注入到 RunnableConfig.context 中
     */
    private TaskInfo createTaskInfo(StreamLaunchPlan launchPlan, ConversationExchangeVo exchangeVo) {

        // unicast sink 是本轮 SSE 的出口，后续模型输出、思考步骤、错误事件都会写到这里。
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        RunnableConfig runnableConfig = buildSessionConfig(launchPlan.getConversationId());

        List<String> thinkingSteps = Collections.synchronizedList(new ArrayList<>());
        List<SearchReference> references = Collections.synchronizedList(new ArrayList<>());
        Set<String> usedTools = ConcurrentHashMap.newKeySet();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        ConversationTraceRecorder traceRecorder = new ConversationTraceRecorder(
            conversationTraceStageStore,
            retrievalObserveStore,
            launchPlan.getConversationId(),
            exchangeVo.getExchangeId(),
            traceId
        );
        StreamEventMetadata eventMetadata = new StreamEventMetadata(
            launchPlan.getConversationId(),
            exchangeVo.getExchangeId()
        );

        // RunnableConfig.context 是跨组件共享的小型上下文，Agent、执行器、工具都能从中取值。
        runnableConfig.context().put(ChatContextKeys.EVENT_SINK, sink);
        runnableConfig.context().put(ChatContextKeys.EVENT_METADATA, eventMetadata);
        runnableConfig.context().put(ChatContextKeys.THINKING_STEPS, thinkingSteps);
        runnableConfig.context().put(ChatContextKeys.REFERENCES, references);
        runnableConfig.context().put(ChatContextKeys.USED_TOOLS, usedTools);
        runnableConfig.context().put(ChatContextKeys.TRACE_ID, traceId);

        runnableConfig.context().put(ChatContextKeys.QUESTION, launchPlan.getQuestion());

        runnableConfig.context().put(ChatContextKeys.CHAT_MODE, launchPlan.getChatMode().name());

        runnableConfig.context().put(ChatContextKeys.CURRENT_DATE, launchPlan.getCurrentDate().toString());
        runnableConfig.context().put(ChatContextKeys.CURRENT_DATE_TEXT, launchPlan.getCurrentDateText());

        putContextIfNotNull(runnableConfig, ChatContextKeys.SELECTED_DOCUMENT_ID, launchPlan.getSelectedDocumentId());
        putContextIfNotBlank(runnableConfig, ChatContextKeys.SELECTED_DOCUMENT_NAME, launchPlan.getSelectedDocumentName());
        putContextIfNotNull(runnableConfig, ChatContextKeys.SELECTED_TASK_ID, launchPlan.getSelectedTaskId());

        ChatDebugTrace debugTrace = initializeDebugTrace(null);
        runnableConfig.context().put(ChatContextKeys.DEBUG_TRACE, debugTrace);

        return new TaskInfo(
            launchPlan.getConversationId(),
            exchangeVo.getExchangeId(),
            launchPlan.getQuestion(),
            launchPlan.getChatMode(),
            traceId,
            launchPlan.getSelectedDocumentId(),
            launchPlan.getSelectedDocumentName(),
            launchPlan.getSelectedTaskId(),
            launchPlan.getCurrentDate(),
            launchPlan.getCurrentDateText(),
            null,
            debugTrace,
            runnableConfig,
            traceRecorder,
            sink,
            eventMetadata,
            launchPlan.getLeaseKey(),
            launchPlan.getLeaseOwnerToken(),
            thinkingSteps,
            references,
            usedTools,
            System.currentTimeMillis()
        );
    }

    /**
     * 绑定客户端 SSE 通道。
     *
     * 将 Sink 的 Flux 暴露给前端，并在订阅时激活生成任务，
     * 在取消时停止任务。
     */
    private Flux<String> bindClientChannel(TaskInfo taskInfo) {

        return taskInfo.sink().asFlux()

            // 浏览器真正连上 SSE 后才激活生成任务，这样可以避免无人接收时浪费模型调用。
            .doOnSubscribe(ignored -> activateGeneration(taskInfo))

            .doOnCancel(() -> stopTask(taskInfo, "客户端已取消请求"));
    }

    /**
     * 激活生成任务。
     *
     * 浏览器订阅 SSE 流后触发此方法，开始真正的模型调用。
     * 先启动租约续期任务，再启动会话执行流。
     */
    private void activateGeneration(TaskInfo taskInfo) {
        try {
            if (taskInfo.finalized().get()) {
                return;
            }

            // 启动租约续期任务（周期性续租 Redis 锁）
            Disposable leaseRenewalDisposable = startLeaseRenewal(taskInfo);
            taskInfo.setLeaseRenewalDisposable(leaseRenewalDisposable);
            if (taskInfo.finalized().get() && !leaseRenewalDisposable.isDisposed()) {
                leaseRenewalDisposable.dispose();
                return;
            }

            // 启动会话执行流并订阅
            Disposable disposable = buildConversationExecution(taskInfo).subscribe();

            taskInfo.setDisposable(disposable);
            if (taskInfo.finalized().get() && !disposable.isDisposed()) {
                disposable.dispose();
            }
        }
        catch (RuntimeException exception) {

            finishWithFailure(taskInfo, exception);
        }
    }

    /**
     * 构建会话执行流。
     *
     * 核心流程：
     * 1. 发送"正在分析"的思考事件
     * 2. 在独立线程中编排执行计划
     * 3. 根据执行计划选择执行器
     * 4. 执行器生成回答并逐步推送到 Sink
     * 5. 完成或失败时执行收尾逻辑
     */
    private Flux<String> buildConversationExecution(TaskInfo taskInfo) {
        return Flux.defer(() -> {

                safeEmit(taskInfo.sink(), streamEventWriter.thinking("正在分析问题上下文。", taskInfo.eventMetadata()));
                // 先由编排器产出执行计划，再根据计划选择 RAG、图查询或 ReAct Agent 执行器。
                return Mono.fromCallable(() -> prepareExecutionPlan(taskInfo))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(plan -> {

                        ConversationExecutor executor = conversationExecutorRegistry.get(plan.getMode());
                        return executor.execute(taskInfo);
                    });
            })
            .publishOn(Schedulers.boundedElastic())

            .doOnNext(chunk -> emitModelChunk(taskInfo, chunk))
            .doOnError(error -> finishWithFailure(taskInfo, error))
            .doOnComplete(() -> finishSuccessfully(taskInfo));
    }

    /**
     * 构建启动计划。
     *
     * 将原始请求参数校验、转换后封装为不可变的 StreamLaunchPlan 对象。
     */
    private StreamLaunchPlan buildLaunchPlan(ChatRequestDto request) {

        String question = normalizeQuestion(request.getQuestion());

        String conversationId = normalizeConversationId(request.getConversationId());
        ChatQueryMode chatMode = parseRequiredChatMode(request.getChatMode());

        KnowledgeDocumentDescriptor selectedDocument = resolveSelectedDocument(chatMode, request.getSelectedDocumentId());

        LocalDate currentDate = LocalDate.now(CHAT_ZONE_ID);
        String currentDateText = formatCurrentDate(currentDate);
        return new StreamLaunchPlan(
            question,
            conversationId,
            chatMode,
            selectedDocument == null ? null : selectedDocument.getDocumentId(),
            selectedDocument == null ? "" : selectedDocument.getDocumentName(),
            selectedDocument == null ? null : selectedDocument.getLastIndexTaskId(),

            buildChatLeaseKey(conversationId),

            UUID.randomUUID().toString(),
            currentDate,
            currentDateText
        );
    }

    /**
     * 申请会话的 Redis 租约。
     *
     * Redis lease 是一个带过期时间的运行锁；服务异常退出时，锁也会自动过期释放。
     *
     * @return true 表示成功获取锁，false 表示该会话已有其他任务在执行
     */
    private boolean claimConversationLease(StreamLaunchPlan launchPlan) {

        return redisLeaseManager.acquire(
            launchPlan.getLeaseKey(),
            launchPlan.getLeaseOwnerToken(),
            CHAT_RUNNING_LEASE_TTL
        );
    }

    /**
     * 标记引导阶段已创建的回合为失败状态。
     */
    private void failBootstrappedExchange(String conversationId, long exchangeId, String errorMessage) {

        conversationArchiveStore.completeExchange(
            conversationId,
            exchangeId,
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ChatTurnStatus.FAILED,
            errorMessage,
            null,
            null
        );
    }

    /**
     * 创建一个包含错误信息的 SSE 流（简化版本）。
     */
    private Flux<String> rejectionFlux(String message) {
        return rejectionFlux(message, null, null);
    }

    /**
     * 创建一个包含错误信息的 SSE 流。
     */
    private Flux<String> rejectionFlux(String message, String conversationId, Long exchangeId) {

        return Flux.just(streamEventWriter.error(message, new StreamEventMetadata(conversationId, exchangeId)));
    }

    // ==================== 会话控制方法 ====================

    /**
     * 停止会话（使用默认原因"用户已停止生成"）。
     */
    public ConversationStopVo stopConversation(String conversationId) {
        return stopConversation(conversationId, "用户已停止生成");
    }

    /**
     * 停止会话（自定义原因）。
     */
    public ConversationStopVo stopConversation(String conversationId, String reason) {
        Optional<TaskInfo> taskInfoOptional = chatRuntimeRegistry.get(conversationId);
        if (taskInfoOptional.isEmpty()) {
            return new ConversationStopVo(conversationId, false, "没有找到正在执行的会话");
        }
        return stopTask(taskInfoOptional.get(), reason);
    }

    /**
     * 停止任务的内部实现。
     *
     * 使用 AtomicBoolean.compareAndSet 保证只执行一次停止操作。
     * 停止流程：
     * 1. 标记 finalized = true
     * 2. 检查是否有新的任务已接管
     * 3. 中断 ReactAgent
     * 4. 取消执行流订阅
     * 5. 发送停止事件到 SSE
     * 6. 保存回合数据到数据库
     * 7. 异步刷新会话摘要
     * 8. 清理资源
     */
    private ConversationStopVo stopTask(TaskInfo taskInfo, String reason) {

        if (!taskInfo.finalized().compareAndSet(false, true)) {
            return new ConversationStopVo(taskInfo.conversationId(), false, "会话已经结束");
        }

        Optional<TaskInfo> currentTask = chatRuntimeRegistry.get(taskInfo.conversationId());
        if (currentTask.isPresent() && currentTask.get() != taskInfo) {

            return new ConversationStopVo(taskInfo.conversationId(), false, "会话已由新的执行接管");
        }

        try {

            businessChatReactAgent.interrupt(taskInfo.runnableConfig());
        }
        catch (RuntimeException exception) {
            log.debug("中断 ReactAgent 时出现异常，继续释放资源", exception);
        }

        Disposable disposable = taskInfo.disposable();
        if (disposable != null && !disposable.isDisposed()) {

            disposable.dispose();
        }

        String responseMessage = "已停止会话生成";
        ConversationTraceRecorder.StageHandle finalizeStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(
                ai.knowhub.chat.model.trace.ConversationTraceStageCode.FINALIZE,
                taskInfo.executionPlan() == null || taskInfo.executionPlan().getMode() == null ? "" : taskInfo.executionPlan().getMode().name(),
                "正在收尾停止中的会话。",
                null
            );
        try {
            safeEmit(taskInfo.sink(), streamEventWriter.status("⏹ " + reason, taskInfo.eventMetadata()));
        }
        catch (RuntimeException exception) {
            log.warn("发送停止事件失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
            responseMessage = "会话已停止，停止事件发送失败";
        }
        finally {
            try {
                safeComplete(taskInfo.sink());
            }
            catch (RuntimeException exception) {
                log.warn("关闭停止中的 SSE 流失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
            }
            try {
                refreshDebugTraceRuntimeStats(taskInfo);
                conversationArchiveStore.completeExchange(
                    taskInfo.conversationId(),
                    taskInfo.exchangeId(),
                    taskInfo.answerBuffer().toString(),
                    snapshotStringList(taskInfo.thinkingSteps()),
                    deduplicateReferences(snapshotReferenceList(taskInfo.references())),
                    List.of(),
                    snapshotUsedTools(taskInfo.usedTools()),
                    taskInfo.debugTrace(),
                    ChatTurnStatus.STOPPED,
                    reason,
                    toNullable(taskInfo.firstResponseTimeMs().get()),
                    System.currentTimeMillis() - taskInfo.startTime()
                );
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().completeStage(finalizeStage, "会话已按停止状态收尾。", Map.of(
                        "finalStatus", ChatTurnStatus.STOPPED.name(),
                        "reason", reason,
                        "answerLength", taskInfo.answerBuffer().length()
                    ));
                }
            }
            catch (RuntimeException exception) {
                log.error("停止会话落库失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
                responseMessage = "会话已停止，收尾落库失败";
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().failStage(finalizeStage, "停止态收尾失败。", exception.getMessage(), null);
                }
            }
            finally {
                safeRefreshConversationSummary(taskInfo.conversationId());
                cleanup(taskInfo);
            }
        }
        return new ConversationStopVo(taskInfo.conversationId(), true, responseMessage);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取会话详情。
     *
     * 查询数据库中的会话记录，并叠加运行时快照（如果会话正在执行中）。
     */
    public ConversationSessionVo getSession(String conversationId) {
        ConversationArchiveStore.ConversationArchiveRecord archiveRecord = conversationArchiveStore.getSessionRecord(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + conversationId));
        return overlayRuntimeSnapshot(toSessionVo(archiveRecord, true, true));
    }

    /**
     * 获取问答回合的详细信息（包含追踪阶段数据）。
     */
    public ConversationExchangeDetailVo getExchangeDetail(String conversationId, String exchangeId) {
        long resolvedExchangeId = parseRequiredLong(exchangeId, "exchangeId");
        ConversationSessionVo sessionVo = getSession(conversationId);
        ConversationExchangeVo exchangeVo = sessionVo.getExchanges().stream()
            .filter(item -> item != null && item.getExchangeId() == resolvedExchangeId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("轮次不存在: " + exchangeId));
        return new ConversationExchangeDetailVo(
            conversationId,
            exchangeVo,
            conversationTraceStageStore.listStageViews(conversationId, resolvedExchangeId)
        );
    }

    /**
     * 分页查询会话列表。
     */
    public ConversationSessionListVo listSessions(ConversationSessionListQueryDto dto) {
        int pageNo = parsePositiveInt(dto == null ? null : dto.getPageNo(), 1);
        int pageSize = parsePositiveInt(dto == null ? null : dto.getPageSize(), 20);
        String keyword = normalizeOptionalText(dto == null ? null : dto.getKeyword());
        ChatQueryMode chatMode = parseOptionalChatMode(dto == null ? null : dto.getChatMode());
        ChatTurnStatus turnStatus = parseOptionalTurnStatus(dto == null ? null : dto.getTurnStatus());

        ConversationArchiveStore.ConversationArchivePage archivePage = conversationArchiveStore.listSessionRecordPage(
            pageNo,
            pageSize,
            keyword,
            chatMode,
            turnStatus
        );
        List<ConversationSessionVo> sessions = archivePage.records()
            .stream()
            .map(record -> toSessionVo(record, false, false))
            .toList();

        long totalPages = archivePage.totalSize() <= 0
            ? 0
            : (archivePage.totalSize() + archivePage.pageSize() - 1) / archivePage.pageSize();
        return new ConversationSessionListVo(
            archivePage.pageNo(),
            archivePage.pageSize(),
            archivePage.totalSize(),
            totalPages,
            sessions
        );
    }

    /**
     * 查询可检索的知识文档列表。
     */
    public List<KnowledgeDocumentOptionVo> listKnowledgeDocumentOptions() {
        return documentKnowledgeService.listRetrievableDocuments().stream()
            .map(this::toKnowledgeDocumentOptionVo)
            .toList();
    }

    /**
     * 强制重建会话摘要。
     */
    public ConversationMemorySummaryVo rebuildConversationSummary(String conversationId) {
        return conversationMemoryService.rebuildConversationSummary(conversationId);
    }

    /**
     * 重置会话：停止执行、删除所有数据（回合、摘要、追踪、检索观察、检查点）。
     */
    public ConversationResetVo resetConversation(String conversationId) {

        ConversationStopVo stopResult = stopConversation(conversationId, "会话被重置");

        ConversationArchiveStore.ConversationRemovalResult removalResult = conversationArchiveStore.deleteSession(conversationId);

        conversationMemoryService.deleteConversationSummary(conversationId);
        conversationTraceStageStore.deleteStages(conversationId);
        retrievalObserveStore.deleteByConversation(conversationId);
        int removedCheckpointCount = checkpointManager.clearThread(conversationId);
        return new ConversationResetVo(
            conversationId,
            stopResult.isStopped(),
            removalResult.removedDialogueCount(),
            removalResult.removedExchangeCount(),
            removedCheckpointCount,
            "会话已重置"
        );
    }

    /**
     * 获取检索结果（用于前端调试展示）。
     */
    public List<RetrievalResultVo> getRetrievalResults(String conversationId, long exchangeId) {
        return retrievalObserveStore.listResults(conversationId, exchangeId);
    }

    /**
     * 获取通道执行详情（用于前端调试展示）。
     */
    public List<ChannelExecutionVo> getChannelExecutions(String conversationId, long exchangeId) {
        return retrievalObserveStore.listChannelExecutions(conversationId, exchangeId);
    }

    /**
     * 获取所有阶段的性能基准数据。
     */
    public List<StageBenchmarkVo> getStageBenchmarks() {
        return stageBenchmarkService.listAll();
    }

    // ==================== 收尾方法 ====================

    /**
     * 发送模型生成的文本片段到 SSE 流。
     *
     * 同时将片段追加到 answerBuffer，并记录首次响应时间。
     */
    private void emitModelChunk(TaskInfo taskInfo, String chunk) {

        taskInfo.answerBuffer().append(chunk);

        if (taskInfo.firstResponseTimeMs().get() == 0L) {

            taskInfo.firstResponseTimeMs().compareAndSet(0L, System.currentTimeMillis() - taskInfo.startTime());
        }

        safeEmit(taskInfo.sink(), streamEventWriter.text(chunk, taskInfo.eventMetadata()));
    }

    /**
     * 成功完成会话的收尾逻辑。
     *
     * 使用 compareAndSet 保证只执行一次。
     * 收尾流程：
     * 1. 生成推荐追问
     * 2. 发送引用来源和推荐事件到 SSE
     * 3. 保存回合数据到数据库
     * 4. 异步刷新会话摘要
     * 5. 清理资源
     */
    private void finishSuccessfully(TaskInfo taskInfo) {
        if (!taskInfo.finalized().compareAndSet(false, true)) {
            return;
        }

        String answer = taskInfo.answerBuffer().toString();
        List<SearchReference> uniqueReferences = deduplicateReferences(snapshotReferenceList(taskInfo.references()));
        ConversationTraceRecorder.StageHandle finalizeStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(
                ai.knowhub.chat.model.trace.ConversationTraceStageCode.FINALIZE,
                taskInfo.executionPlan() == null || taskInfo.executionPlan().getMode() == null ? "" : taskInfo.executionPlan().getMode().name(),
                "正在收尾已完成会话。",
                null
            );
        ConversationTraceRecorder.StageHandle recommendationStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(
                ai.knowhub.chat.model.trace.ConversationTraceStageCode.RECOMMENDATION,
                taskInfo.executionPlan() == null || taskInfo.executionPlan().getMode() == null ? "" : taskInfo.executionPlan().getMode().name(),
                "正在生成推荐追问。",
                null
            );
        List<String> recommendations;
        if (taskInfo.executionPlan() != null
            && taskInfo.executionPlan().getMode() == ai.knowhub.chat.rag.model.ExecutionMode.CLARIFICATION) {
            recommendations = taskInfo.executionPlan().getClarificationOptions() == null
                ? List.of()
                : new ArrayList<>(taskInfo.executionPlan().getClarificationOptions());
        }
        else {
            recommendations = recommendationService.generateRecommendations(
                taskInfo.question(),
                answer,
                historicalRecentExchanges(taskInfo),
                taskInfo.traceRecorder()
            );
        }
        if (taskInfo.traceRecorder() != null) {
            taskInfo.traceRecorder().completeStage(recommendationStage, "推荐追问生成完成。", Map.of(
                "recommendationCount", recommendations.size(),
                "recommendations", recommendations
            ));
        }

        try {
            if (!uniqueReferences.isEmpty()) {
                safeEmit(taskInfo.sink(), streamEventWriter.references(uniqueReferences, taskInfo.eventMetadata()));
            }
            if (!recommendations.isEmpty()) {
                safeEmit(taskInfo.sink(), streamEventWriter.recommendations(recommendations, taskInfo.eventMetadata()));
            }
        }
        catch (RuntimeException exception) {
            log.warn("补发引用或推荐事件失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
        }
        finally {
            try {
                safeComplete(taskInfo.sink());
            }
            catch (RuntimeException exception) {
                log.warn("关闭成功完成的 SSE 流失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
            }
            try {
                refreshDebugTraceRuntimeStats(taskInfo);
                conversationArchiveStore.completeExchange(
                    taskInfo.conversationId(),
                    taskInfo.exchangeId(),
                    answer,
                    snapshotStringList(taskInfo.thinkingSteps()),
                    uniqueReferences,
                    recommendations,
                    snapshotUsedTools(taskInfo.usedTools()),
                    taskInfo.debugTrace(),
                    ChatTurnStatus.COMPLETED,
                    "",
                    toNullable(taskInfo.firstResponseTimeMs().get()),
                    System.currentTimeMillis() - taskInfo.startTime()
                );
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().completeStage(finalizeStage, "会话已按完成状态收尾。", Map.of(
                        "finalStatus", ChatTurnStatus.COMPLETED.name(),
                        "referenceCount", uniqueReferences.size(),
                        "recommendationCount", recommendations.size(),
                        "answerLength", answer.length()
                    ));
                }
            }
            catch (RuntimeException exception) {
                log.error("成功会话收尾落库失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().failStage(finalizeStage, "完成态收尾失败。", exception.getMessage(), null);
                }
            }
            finally {
                safeRefreshConversationSummary(taskInfo.conversationId());
                cleanup(taskInfo);
            }
        }
    }

    /**
     * 失败会话的收尾逻辑。
     *
     * 使用 compareAndSet 保证只执行一次。
     */
    private void finishWithFailure(TaskInfo taskInfo, Throwable error) {
        if (!taskInfo.finalized().compareAndSet(false, true)) {
            return;
        }

        String errorMessage = buildErrorMessage(error);
        ConversationTraceRecorder.StageHandle finalizeStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(
                ai.knowhub.chat.model.trace.ConversationTraceStageCode.FINALIZE,
                taskInfo.executionPlan() == null || taskInfo.executionPlan().getMode() == null ? "" : taskInfo.executionPlan().getMode().name(),
                "正在收尾失败会话。",
                null
            );

        log.error("会话执行失败, conversationId={}, exchangeId={}, error={}",
            taskInfo.conversationId(),
            taskInfo.exchangeId(),
            errorMessage,
            error);

        try {
            safeEmit(taskInfo.sink(), streamEventWriter.error(errorMessage, taskInfo.eventMetadata()));
        }
        catch (RuntimeException exception) {
            log.warn("发送失败事件失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
        }
        finally {
            try {
                safeComplete(taskInfo.sink());
            }
            catch (RuntimeException exception) {
                log.warn("关闭失败中的 SSE 流失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
            }
            try {
                refreshDebugTraceRuntimeStats(taskInfo);
                conversationArchiveStore.completeExchange(
                    taskInfo.conversationId(),
                    taskInfo.exchangeId(),
                    taskInfo.answerBuffer().toString(),
                    snapshotStringList(taskInfo.thinkingSteps()),
                    deduplicateReferences(snapshotReferenceList(taskInfo.references())),
                    List.of(),
                    snapshotUsedTools(taskInfo.usedTools()),
                    taskInfo.debugTrace(),
                    ChatTurnStatus.FAILED,
                    errorMessage,
                    toNullable(taskInfo.firstResponseTimeMs().get()),
                    System.currentTimeMillis() - taskInfo.startTime()
                );
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().completeStage(finalizeStage, "会话已按失败状态收尾。", Map.of(
                        "finalStatus", ChatTurnStatus.FAILED.name(),
                        "errorMessage", errorMessage,
                        "answerLength", taskInfo.answerBuffer().length()
                    ));
                }
            }
            catch (RuntimeException exception) {
                log.error("失败会话收尾落库失败, conversationId={}, exchangeId={}", taskInfo.conversationId(), taskInfo.exchangeId(), exception);
                if (taskInfo.traceRecorder() != null) {
                    taskInfo.traceRecorder().failStage(finalizeStage, "失败态收尾失败。", exception.getMessage(), null);
                }
            }
            finally {
                safeRefreshConversationSummary(taskInfo.conversationId());
                cleanup(taskInfo);
            }
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 构建错误消息。
     *
     * 遍历异常链，优先提取 WebClientResponseException 的详细信息。
     */
    private String buildErrorMessage(Throwable error) {

        Throwable current = error;
        while (current != null) {

            if (current instanceof WebClientResponseException responseException) {
                String responseBody = responseException.getResponseBodyAsString();
                if (StrUtil.isNotBlank(responseBody)) {
                    return responseException.getStatusCode()
                        + " from "
                        + responseException.getRequest().getMethod()
                        + " "
                        + responseException.getRequest().getURI()
                        + " | responseBody="
                        + responseBody;
                }

                return responseException.getMessage();
            }
            current = current.getCause();
        }

        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    /**
     * 刷新调试追踪的运行时统计数据。
     */
    private void refreshDebugTraceRuntimeStats(TaskInfo taskInfo) {
        if (taskInfo == null || taskInfo.debugTrace() == null || taskInfo.traceRecorder() == null) {
            return;
        }
        taskInfo.debugTrace().setModelUsageTraces(taskInfo.traceRecorder().snapshotModelUsageTraces());
        ai.knowhub.chat.model.debug.ChatLimitStats limitStats = taskInfo.traceRecorder().limitStats();
        limitStats.setModelCallsUsed(taskInfo.traceRecorder().snapshotModelUsageTraces().size());
        limitStats.setModelCallsRunLimit(chatAgentProperties.getMaxModelCallsPerRun());
        limitStats.setModelCallsThreadLimit(chatAgentProperties.getMaxModelCallsPerThread());
        limitStats.setToolCallsUsed(snapshotUsedTools(taskInfo.usedTools()).size());
        limitStats.setToolCallsRunLimit(chatAgentProperties.getMaxToolCallsPerRun());
        limitStats.setToolCallsThreadLimit(chatAgentProperties.getMaxToolCallsPerThread());
        taskInfo.debugTrace().setLimitStats(limitStats);
    }

    /**
     * 清理任务资源。
     *
     * 释放 Disposable 订阅、Redis 租约、运行时注册。
     */
    private void cleanup(TaskInfo taskInfo) {

        Disposable disposable = taskInfo.disposable();
        Disposable leaseRenewalDisposable = taskInfo.leaseRenewalDisposable();

        if (leaseRenewalDisposable != null && !leaseRenewalDisposable.isDisposed()) {

            leaseRenewalDisposable.dispose();
        }

        if (disposable != null && !disposable.isDisposed()) {

            disposable.dispose();
        }

        releaseLeaseQuietly(taskInfo.leaseKey(), taskInfo.leaseOwnerToken());

        chatRuntimeRegistry.remove(taskInfo.conversationId(), taskInfo);
    }

    /** 引用来源去重（基于 uniqueKey） */
    private List<SearchReference> deduplicateReferences(List<SearchReference> references) {
        Map<String, SearchReference> unique = new LinkedHashMap<>();

        for (SearchReference reference : references) {
            if (reference == null) {
                continue;
            }
            unique.putIfAbsent(reference.uniqueKey(), reference);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * 初始化调试追踪信息。
     *
     * 如果有执行计划，填充执行计划中的各种调试数据；
     * 否则创建一个空的追踪对象。
     */
    private ChatDebugTrace initializeDebugTrace(ConversationExecutionPlan executionPlan) {
        if (executionPlan == null) {
            return ChatDebugTrace.builder()
                .retrievalNotes(Collections.synchronizedList(new ArrayList<>()))
                .usedChannels(Collections.synchronizedList(new ArrayList<>()))
                .build();
        }
        return ChatDebugTrace.builder()

            .executionMode(executionPlan.getMode() == null ? "" : executionPlan.getMode().name())
            .chatMode(executionPlan.getChatMode())

            .originalQuestion(executionPlan.getOriginalQuestion())
            .rewriteQuestion(executionPlan.getRewriteQuestion())
            .rewriteSubQuestions(executionPlan.getRewriteSubQuestions() == null ? List.of() : new ArrayList<>(executionPlan.getRewriteSubQuestions()))
            .retrievalQuestion(executionPlan.getRetrievalQuestion())
            .agentQuestion(executionPlan.getAgentQuestion())
            .navigationDecision(executionPlan.getNavigationDecision())

            .historySummary(executionPlan.getHistorySummary())
            .longTermSummary(executionPlan.getLongTermSummary())
            .recentHistoryTranscript(executionPlan.getRecentHistoryTranscript())
            .answerRecentTranscript(executionPlan.getAnswerRecentTranscript())
            .answerHistoryContext(executionPlan.getAnswerHistoryContext() == null
                ? ""
                : executionPlan.getAnswerHistoryContext().getRenderedText())
            .answerHistoryFollowUpQuestion(executionPlan.getAnswerHistoryContext() != null
                && executionPlan.getAnswerHistoryContext().isFollowUpQuestion())
            .historyCompressionApplied(executionPlan.isHistoryCompressionApplied())
            .historyCoveredExchangeId(executionPlan.getHistoryCoveredExchangeId())
            .historyCoveredExchangeCount(executionPlan.getHistoryCoveredExchangeCount())
            .historyCompressionCount(executionPlan.getHistoryCompressionCount())
            .currentDateText(executionPlan.getCurrentDateText())
            .requiresFreshSearch(executionPlan.isRequiresFreshSearch())
            .requiresCurrentDateAnchoring(executionPlan.isRequiresCurrentDateAnchoring())

            .retrievalSubQuestions(executionPlan.getRetrievalSubQuestions() == null ? List.of() : new ArrayList<>(executionPlan.getRetrievalSubQuestions()))
            .selectedDocumentId(executionPlan.getSelectedDocumentId())
            .selectedTaskId(executionPlan.getSelectedTaskId())

            .retrievalNotes(Collections.synchronizedList(new ArrayList<>()))
            .usedChannels(Collections.synchronizedList(new ArrayList<>()))
            .toolTraces(Collections.synchronizedList(new ArrayList<>()))
            .noEvidenceReply(executionPlan.getNoEvidenceReply())
            .build();
    }

    /**
     * 编排执行计划。
     *
     * 编排器负责"决定怎么答"，执行器负责"真正去答"，这里把两者连接起来。
     */
    private ConversationExecutionPlan prepareExecutionPlan(TaskInfo taskInfo) {

        ConversationExecutionPlan executionPlan = chatPreparationOrchestrator.prepare(taskInfo);

        executionPlan.setAgentQuestion(buildAgentQuestion(executionPlan));
        if (executionPlan.getSelectedDocumentId() != null
            && !Objects.equals(executionPlan.getSelectedDocumentId(), taskInfo.selectedDocumentId())) {
            conversationArchiveStore.refreshSessionScope(
                taskInfo.conversationId(),
                executionPlan.getChatMode(),
                executionPlan.getSelectedDocumentId(),
                executionPlan.getSelectedDocumentName()
            );
            putContextIfNotNull(taskInfo.runnableConfig(), ChatContextKeys.SELECTED_DOCUMENT_ID, executionPlan.getSelectedDocumentId());
            putContextIfNotBlank(taskInfo.runnableConfig(), ChatContextKeys.SELECTED_DOCUMENT_NAME, executionPlan.getSelectedDocumentName());
            putContextIfNotNull(taskInfo.runnableConfig(), ChatContextKeys.SELECTED_TASK_ID, executionPlan.getSelectedTaskId());
        }
        taskInfo.setExecutionPlan(executionPlan);
        taskInfo.setDebugTrace(initializeDebugTrace(executionPlan));
        taskInfo.runnableConfig().context().put(ChatContextKeys.DEBUG_TRACE, taskInfo.debugTrace());
        return executionPlan;
    }

    /**
     * 将归档记录转换为会话返回值对象。
     */
    private ConversationSessionVo toSessionVo(ConversationArchiveStore.ConversationArchiveRecord archiveRecord,
                                                  boolean includeMemorySummary,
                                                  boolean includeExchanges) {

        RunnableConfig runnableConfig = RunnableConfig.builder()
            .threadId(archiveRecord.conversationId())
            .build();

        Map<String, Object> state = checkpointManager.get(runnableConfig)
            .map(Checkpoint::getState)
            .orElseGet(Map::of);
        Object messages = state.getOrDefault("messages", List.of());
        List<?> messageList = messages instanceof List<?> list ? list : List.of();
        List<ConversationExchangeVo> archiveExchanges = archiveRecord.exchanges() == null ? List.of() : archiveRecord.exchanges();
        List<ConversationExchangeVo> exchanges = includeExchanges ? archiveExchanges : List.of();
        int businessMessageCount = businessMessageCount(archiveExchanges);
        String businessLatestUserMessage = latestExchangeQuestion(archiveExchanges);
        String businessLatestAssistantMessage = latestExchangeAnswer(archiveExchanges);
        ConversationExchangeVo latestExchange = latestExchange(archiveExchanges);

        return new ConversationSessionVo(
            archiveRecord.conversationId(),
            archiveRecord.running(),

            checkpointManager.list(runnableConfig).size(),

            businessMessageCount > 0 ? businessMessageCount : messageList.size(),
            StrUtil.isNotBlank(businessLatestUserMessage) ? businessLatestUserMessage : latestMessage(messageList, MessageType.USER),
            StrUtil.isNotBlank(businessLatestAssistantMessage) ? businessLatestAssistantMessage : latestMessage(messageList, MessageType.ASSISTANT),
            latestExchange == null ? null : latestExchange.getExchangeId(),
            latestExchange == null || latestExchange.getStatus() == null ? "" : latestExchange.getStatus().name(),
            latestExchange == null || latestExchange.getErrorMessage() == null ? "" : latestExchange.getErrorMessage(),
            archiveRecord.chatMode(),
            archiveRecord.selectedDocumentId() == null ? "" : String.valueOf(archiveRecord.selectedDocumentId()),
            archiveRecord.selectedDocumentName(),
            archiveRecord.createdAt(),
            archiveRecord.updatedAt(),
            exchanges,
            includeMemorySummary ? conversationMemoryService.getConversationSummary(archiveRecord.conversationId()) : null
        );
    }

    /**
     * 叠加运行时快照到会话展示。
     *
     * 如果会话正在执行中，将 TaskInfo 中的实时数据（回答内容、思考步骤等）
     * 覆盖到从数据库查询的静态数据上，使前端能看到实时进度。
     */
    private ConversationSessionVo overlayRuntimeSnapshot(ConversationSessionVo sessionVo) {
        if (sessionVo == null || sessionVo.getExchanges() == null || sessionVo.getExchanges().isEmpty()) {
            return sessionVo;
        }
        Optional<TaskInfo> runtimeOptional = chatRuntimeRegistry.get(sessionVo.getConversationId());
        if (runtimeOptional.isEmpty()) {
            return sessionVo;
        }
        TaskInfo taskInfo = runtimeOptional.get();
        List<ConversationExchangeVo> exchanges = new ArrayList<>(sessionVo.getExchanges().size());
        boolean replaced = false;
        for (ConversationExchangeVo exchange : sessionVo.getExchanges()) {
            if (exchange == null) {
                continue;
            }
            if (exchange.getExchangeId() == taskInfo.exchangeId()) {
                exchanges.add(mergeRuntimeExchange(exchange, taskInfo));
                replaced = true;
                continue;
            }
            exchanges.add(exchange);
        }
        if (!replaced) {
            return sessionVo;
        }
        sessionVo.setExchanges(exchanges);
        sessionVo.setMessageCount(businessMessageCount(exchanges));
        sessionVo.setRunning(true);
        sessionVo.setUpdatedAt(Instant.now());
        sessionVo.setLatestExchangeId(taskInfo.exchangeId());
        sessionVo.setLatestTurnStatus(ChatTurnStatus.RUNNING.name());
        String liveAnswer = taskInfo.answerBuffer().toString();
        if (StrUtil.isNotBlank(liveAnswer)) {
            sessionVo.setLatestAssistantMessage(liveAnswer);
        }
        return sessionVo;
    }

    /** 将运行时数据合并到回合展示 */
    private ConversationExchangeVo mergeRuntimeExchange(ConversationExchangeVo exchange,
                                                          TaskInfo taskInfo) {
        return new ConversationExchangeVo(
            exchange.getExchangeId(),
            exchange.getQuestion(),
            taskInfo.answerBuffer().toString(),
            snapshotStringList(taskInfo.thinkingSteps()),
            deduplicateReferences(snapshotReferenceList(taskInfo.references())),
            exchange.getRecommendations() == null ? List.of() : exchange.getRecommendations(),
            snapshotUsedTools(taskInfo.usedTools()),
            taskInfo.debugTrace(),
            ChatTurnStatus.RUNNING,
            exchange.getErrorMessage(),
            toNullable(taskInfo.firstResponseTimeMs().get()),
            System.currentTimeMillis() - taskInfo.startTime(),
            exchange.getCreateTime(),
            exchange.getEditTime()
        );
    }

    /** 解析并验证选中的文档 */
    private KnowledgeDocumentDescriptor resolveSelectedDocument(ChatQueryMode chatMode, String selectedDocumentId) {
        if (chatMode == null) {
            throw new IllegalArgumentException("chatMode 不能为空");
        }
        String normalizedDocumentId = StrUtil.trimToNull(selectedDocumentId);
        if (chatMode == ChatQueryMode.OPEN_CHAT) {

            if (normalizedDocumentId != null) {
                throw new IllegalArgumentException("开放式提问模式下不能传 selectedDocumentId");
            }
            return null;
        }
        if (chatMode == ChatQueryMode.AUTO_DOCUMENT) {
            if (normalizedDocumentId != null) {
                throw new IllegalArgumentException("自动知识问答模式下不能传 selectedDocumentId");
            }
            return null;
        }

        if (normalizedDocumentId == null) {
            throw new IllegalArgumentException("当前文档问答模式下必须选择一个文档");
        }
        final Long resolvedDocumentId = parseRequiredLong(normalizedDocumentId, "selectedDocumentId");
        return documentKnowledgeService.listRetrievableDocuments().stream()
            .filter(item -> Objects.equals(item.getDocumentId(), resolvedDocumentId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("所选文档当前不可检索: " + normalizedDocumentId));
    }

    /** 将文档描述符转换为前端展示 */
    private KnowledgeDocumentOptionVo toKnowledgeDocumentOptionVo(KnowledgeDocumentDescriptor descriptor) {
        return new KnowledgeDocumentOptionVo(
            descriptor.getDocumentId() == null ? "" : String.valueOf(descriptor.getDocumentId()),
            descriptor.getDocumentName(),
            descriptor.getKnowledgeScopeName(),
            descriptor.getBusinessCategory(),
            descriptor.getDocumentTags()
        );
    }

    // ==================== 参数解析辅助方法 ====================

    private Long parseRequiredLong(String value, String fieldName) {
        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " 非法: " + value, exception);
        }
    }

    private int parsePositiveInt(String value, int defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("分页参数非法: " + value, exception);
        }
    }

    private String normalizeOptionalText(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private ChatQueryMode parseOptionalChatMode(String value) {
        if (StrUtil.isBlank(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return ChatQueryMode.valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("chatMode 非法: " + value, exception);
        }
    }

    private ChatQueryMode parseRequiredChatMode(String value) {
        ChatQueryMode chatMode = parseOptionalChatMode(value);
        if (chatMode == null) {
            throw new IllegalArgumentException("chatMode 不能为空");
        }
        return chatMode;
    }

    private ChatTurnStatus parseOptionalTurnStatus(String value) {
        if (StrUtil.isBlank(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return ChatTurnStatus.valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("turnStatus 非法: " + value, exception);
        }
    }

    // ==================== 统计和查询辅助方法 ====================

    /** 统计回合列表中的有效消息数（问题 + 回答） */
    private int businessMessageCount(List<ConversationExchangeVo> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ConversationExchangeVo exchange : exchanges) {
            if (exchange == null) {
                continue;
            }
            if (StrUtil.isNotBlank(exchange.getQuestion())) {
                count++;
            }
            if (StrUtil.isNotBlank(exchange.getAnswer())) {
                count++;
            }
        }
        return count;
    }

    /** 获取最近一个回合的问题 */
    private String latestExchangeQuestion(List<ConversationExchangeVo> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) {
            return "";
        }
        for (int index = exchanges.size() - 1; index >= 0; index--) {
            ConversationExchangeVo exchange = exchanges.get(index);
            if (exchange != null && StrUtil.isNotBlank(exchange.getQuestion())) {
                return exchange.getQuestion();
            }
        }
        return "";
    }

    /** 获取最近一个回合的回答 */
    private String latestExchangeAnswer(List<ConversationExchangeVo> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) {
            return "";
        }
        for (int index = exchanges.size() - 1; index >= 0; index--) {
            ConversationExchangeVo exchange = exchanges.get(index);
            if (exchange != null && StrUtil.isNotBlank(exchange.getAnswer())) {
                return exchange.getAnswer();
            }
        }
        return "";
    }

    /** 获取最近一个非空回合 */
    private ConversationExchangeVo latestExchange(List<ConversationExchangeVo> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) {
            return null;
        }
        for (int index = exchanges.size() - 1; index >= 0; index--) {
            ConversationExchangeVo exchange = exchanges.get(index);
            if (exchange != null) {
                return exchange;
            }
        }
        return null;
    }

    /** 从检查点消息列表中获取指定类型的最新消息 */
    private String latestMessage(List<?> messages, MessageType type) {

        for (int index = messages.size() - 1; index >= 0; index--) {
            Object candidate = messages.get(index);
            if (candidate instanceof AbstractMessage message && message.getMessageType() == type) {
                return message.getText();
            }
        }
        return "";
    }

    /** 获取最近的回合列表（用于历史上下文） */
    private List<ConversationExchangeVo> recentExchanges(String conversationId) {

        return conversationArchiveStore.listRecentExchanges(
            conversationId,
            Math.max(1, chatAgentProperties.getHistoryPreviewTurns())
        );
    }

    /** 获取历史最近回合（排除当前回合） */
    private List<ConversationExchangeVo> historicalRecentExchanges(TaskInfo taskInfo) {
        return recentExchanges(taskInfo.conversationId()).stream()
            .filter(exchange -> exchange.getExchangeId() != taskInfo.exchangeId())
            .toList();
    }

    /** 构建 RunnableConfig */
    private RunnableConfig buildSessionConfig(String conversationId) {

        return RunnableConfig.builder()
            .threadId(conversationId)
            .build();
    }

    /** 条件性地向 context 中放入非 null 值 */
    private void putContextIfNotNull(RunnableConfig runnableConfig, String key, Object value) {
        if (runnableConfig == null || StrUtil.isBlank(key) || value == null) {
            return;
        }
        runnableConfig.context().put(key, value);
    }

    /** 条件性地向 context 中放入非空字符串 */
    private void putContextIfNotBlank(RunnableConfig runnableConfig, String key, String value) {
        if (runnableConfig == null || StrUtil.isBlank(key) || StrUtil.isBlank(value)) {
            return;
        }
        runnableConfig.context().put(key, value.trim());
    }

    // ==================== 租约管理方法 ====================

    /**
     * 启动租约续期任务。
     *
     * 长回答可能超过 30 秒，需要周期性续租，证明当前服务实例还在处理这轮会话。
     */
    private Disposable startLeaseRenewal(TaskInfo taskInfo) {

        return Flux.interval(CHAT_RUNNING_LEASE_RENEW_INTERVAL, CHAT_RUNNING_LEASE_RENEW_INTERVAL)

            .subscribe(ignored -> renewLeaseOrStop(taskInfo), error ->
                log.warn("租约续期任务出现异常, conversationId={}, exchangeId={}",
                    taskInfo.conversationId(),
                    taskInfo.exchangeId(),
                    error)
            );
    }

    /**
     * 续租或停止。
     *
     * 续租失败通常表示锁被释放或被其他实例接管，此时主动停止，避免产生两份答案。
     */
    private void renewLeaseOrStop(TaskInfo taskInfo) {

        boolean renewed = redisLeaseManager.renew(
            taskInfo.leaseKey(),
            taskInfo.leaseOwnerToken(),
            CHAT_RUNNING_LEASE_TTL
        );
        if (renewed) {

            return;
        }

        log.warn("会话租约续期失败，准备停止当前会话, conversationId={}, exchangeId={}",
            taskInfo.conversationId(),
            taskInfo.exchangeId());
        Disposable leaseRenewalDisposable = taskInfo.leaseRenewalDisposable();
        if (leaseRenewalDisposable != null && !leaseRenewalDisposable.isDisposed()) {
            leaseRenewalDisposable.dispose();
        }
        stopTask(taskInfo, "会话租约已失效，已停止生成");
    }

    /** 静默释放 Redis 租约（异常时不抛出） */
    private void releaseLeaseQuietly(String leaseKey, String leaseOwnerToken) {
        try {

            redisLeaseManager.release(leaseKey, leaseOwnerToken);
        }
        catch (RuntimeException exception) {

            log.warn("释放会话租约时出现异常, leaseKey={}", leaseKey, exception);
        }
    }

    /** 构建 Redis 租约键 */
    private String buildChatLeaseKey(String conversationId) {

        return CHAT_RUNNING_LEASE_PREFIX + conversationId;
    }

    /** 将 long 转为 Long（0 时返回 null） */
    private Long toNullable(long value) {

        return value > 0 ? value : null;
    }

    /** 校验并规范化问题文本 */
    private String normalizeQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            throw new KnowHubFrameException("question 不能为空");
        }

        return question.trim();
    }

    /** 校验并规范化会话 ID（为空时自动生成 UUID） */
    private String normalizeConversationId(String conversationId) {
        if (StrUtil.isNotBlank(conversationId)) {

            return conversationId.trim();
        }

        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 使用模板渲染 Agent 的最终问题 */
    private String buildAgentQuestion(ConversationExecutionPlan executionPlan) {
        return promptTemplateService.render(PromptTemplateNames.AGENT_QUESTION, Map.of(
            "currentDateText", StrUtil.blankToDefault(executionPlan.getCurrentDateText(), ""),
            "requiresCurrentDateAnchoring", executionPlan.isRequiresCurrentDateAnchoring(),
            "requiresFreshSearch", executionPlan.isRequiresFreshSearch(),
            "hasHistorySummary", StrUtil.isNotBlank(executionPlan.getHistorySummary()),
            "historySummary", StrUtil.blankToDefault(executionPlan.getHistorySummary(), ""),
            "question", StrUtil.blankToDefault(executionPlan.getOriginalQuestion(), "")
        ));
    }

    /** 格式化当前日期为中文文本 */
    private String formatCurrentDate(LocalDate currentDate) {

        return currentDate + "（" + chineseWeekday(currentDate.getDayOfWeek()) + "）";
    }

    /** 将星期几转换为中文 */
    private String chineseWeekday(DayOfWeek dayOfWeek) {

        return switch (dayOfWeek) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    // ==================== 安全操作辅助方法 ====================

    /** 安全地向 Sink 发送事件（异常时不抛出） */
    private void safeEmit(Sinks.Many<String> sink, String payload) {

        SinkEmitHelper.emitNext(sink, payload);
    }

    /** 安全地关闭 Sink（异常时不抛出） */
    private void safeComplete(Sinks.Many<String> sink) {

        SinkEmitHelper.emitComplete(sink);
    }

    /** 安全地获取字符串列表的快照（synchronized 拷贝） */
    private List<String> snapshotStringList(List<String> source) {
        synchronized (source) {
            return List.copyOf(source);
        }
    }

    /** 安全地获取引用列表的快照（synchronized 拷贝） */
    private List<SearchReference> snapshotReferenceList(List<SearchReference> source) {
        synchronized (source) {
            return new ArrayList<>(source);
        }
    }

    /** 获取已使用工具集合的快照 */
    private List<String> snapshotUsedTools(Set<String> source) {
        return new ArrayList<>(source);
    }

    /** 安全地异步刷新会话摘要（异常时不抛出） */
    private void safeRefreshConversationSummary(String conversationId) {
        try {
            conversationMemoryService.refreshConversationSummaryAsync(conversationId);
        }
        catch (RuntimeException exception) {
            log.warn("刷新会话摘要失败, conversationId={}", conversationId, exception);
        }
    }

}
