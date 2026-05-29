package ai.knowhub.chat.service;

import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.model.ChannelExecutionVo;
import ai.knowhub.chat.model.RetrievalResultVo;
import ai.knowhub.chat.model.debug.ChatLimitStats;
import ai.knowhub.chat.model.debug.ChatModelUsageTrace;
import ai.knowhub.chat.model.trace.ConversationTraceStageCode;
import ai.knowhub.chat.model.trace.ConversationTraceStageState;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【会话追踪记录器】
 *
 * 记录一次问答回合（Exchange）执行过程中的所有追踪信息，包括：
 *   1. 阶段追踪：每个执行阶段（查询改写、检索、重排、生成等）的开始/结束时间和状态
 *   2. 模型调用追踪：每次调用大模型的 token 用量、耗时、费用估算
 *   3. 检索结果追踪：检索到的文档切片和各通道的执行详情
 *   4. 限制统计：模型调用次数、工具调用次数的使用量和限额
 *
 * 生命周期：
 * - 创建：在 BusinessChatService.createTaskInfo() 中，每个 TaskInfo 对应一个 recorder
 * - 使用：在执行过程中，各阶段调用 startStage()/completeStage()/failStage() 记录阶段
 * - 销毁：在 cleanup() 中随 TaskInfo 一起销毁
 *
 * 设计模式：建造者模式的变体 —— 通过逐步调用方法积累追踪数据，
 * 最终通过 snapshot 方法获取快照。
 *
 * 线程安全：
 * - modelUsageTraces 使用 Collections.synchronizedList 保证线程安全
 * - StageHandle 是不可变 record，可以安全地在多线程间传递
 *
 * 使用场景：
 * - ConversationExecutor 执行器在每个阶段调用 startStage/completeStage
 * - ObservedChatModelService 在每次模型调用后调用 addModelUsageTrace
 * - RAG 检索完成后调用 recordRetrievalResults 记录检索结果
 */
@Slf4j
public class ConversationTraceRecorder {

    /** 追踪阶段持久化存储 */
    private final ConversationTraceStageStore traceStageStore;

    /** 检索观察持久化存储 */
    private final RetrievalObserveStore retrievalObserveStore;

    /** 会话 ID */
    private final String conversationId;

    /** 本轮问答回合 ID */
    private final long exchangeId;

    /** 本次追踪的唯一标识（UUID，32位十六进制字符串） */
    private final String traceId;

    /**
     * 模型调用追踪记录列表（线程安全）。
     * 每次调用大模型都会追加一条记录，包含 token 用量、耗时等信息。
     */
    private final List<ChatModelUsageTrace> modelUsageTraces = Collections.synchronizedList(new ArrayList<>());

    /**
     * 限制统计数据。
     * 记录模型调用次数、工具调用次数的使用量和系统配置的限额。
     */
    private final ChatLimitStats limitStats = new ChatLimitStats();

    /**
     * 构造函数。
     *
     * @param traceStageStore      追踪阶段存储（用于持久化阶段数据到数据库）
     * @param retrievalObserveStore 检索观察存储（用于持久化检索结果到数据库）
     * @param conversationId       会话 ID
     * @param exchangeId           本轮问答回合 ID
     * @param traceId              本次追踪的唯一标识
     */
    public ConversationTraceRecorder(ConversationTraceStageStore traceStageStore,
                                     RetrievalObserveStore retrievalObserveStore,
                                     String conversationId,
                                     long exchangeId,
                                     String traceId) {
        this.traceStageStore = traceStageStore;
        this.retrievalObserveStore = retrievalObserveStore;
        this.conversationId = conversationId;
        this.exchangeId = exchangeId;
        this.traceId = traceId;
    }

    /** 获取会话 ID */
    public String conversationId() {
        return conversationId;
    }

    /** 获取本轮问答回合 ID */
    public long exchangeId() {
        return exchangeId;
    }

    /** 获取本次追踪的唯一标识 */
    public String traceId() {
        return traceId;
    }

    /**
     * 开始一个新的追踪阶段。
     *
     * 调用此方法后，会向数据库插入一条"运行中"的阶段记录，
     * 并返回一个 StageHandle 用于后续完成或失败时引用。
     *
     * @param stageCode     阶段代码枚举（如 QUERY_REWRITE、RETRIEVAL 等）
     * @param executionMode 执行模式名称（如 RAG、REACT_AGENT）
     * @param summaryText   阶段摘要描述（如"正在检索知识库"）
     * @param snapshot      阶段快照数据（可选，会被序列化为 JSON）
     * @return 阶段句柄，包含阶段 ID 和开始时间
     */
    public StageHandle startStage(ConversationTraceStageCode stageCode,
                                  String executionMode,
                                  String summaryText,
                                  Object snapshot) {
        long stageId = traceStageStore.startStage(
            conversationId,
            exchangeId,
            traceId,
            stageCode,
            1,
            null,
            executionMode,
            summaryText,
            snapshot
        );
        return new StageHandle(stageId, System.currentTimeMillis(), stageCode);
    }

    /**
     * 成功完成一个追踪阶段。
     *
     * @param stageHandle 阶段句柄（由 startStage 返回）
     * @param summaryText 完成时的摘要描述
     * @param snapshot    完成时的快照数据（可选）
     */
    public void completeStage(StageHandle stageHandle,
                              String summaryText,
                              Object snapshot) {
        if (stageHandle == null) {
            return;
        }
        traceStageStore.finishStage(
            stageHandle.stageId(),
            ConversationTraceStageState.COMPLETED,
            summaryText,
            "",
            snapshot,
            System.currentTimeMillis() - stageHandle.startTimeMs()
        );
    }

    /**
     * 标记一个追踪阶段失败（使用错误消息字符串）。
     *
     * @param stageHandle 阶段句柄
     * @param summaryText 失败时的摘要描述
     * @param errorMessage 错误消息
     * @param snapshot    失败时的快照数据（可选）
     */
    public void failStage(StageHandle stageHandle,
                          String summaryText,
                          String errorMessage,
                          Object snapshot) {
        if (stageHandle == null) {
            return;
        }
        traceStageStore.finishStage(
            stageHandle.stageId(),
            ConversationTraceStageState.FAILED,
            summaryText,
            errorMessage,
            snapshot,
            System.currentTimeMillis() - stageHandle.startTimeMs()
        );
    }

    /**
     * 标记一个追踪阶段失败（使用异常对象）。
     *
     * 与字符串版本的区别：此方法会自动提取异常的类名、消息和堆栈信息，
     * 并附加到快照数据中，便于调试。
     *
     * @param stageHandle 阶段句柄
     * @param summaryText 失败时的摘要描述
     * @param throwable   异常对象
     * @param snapshot    失败时的快照数据（可选，会被增强）
     */
    public void failStage(StageHandle stageHandle,
                          String summaryText,
                          Throwable throwable,
                          Object snapshot) {
        if (stageHandle == null) {
            return;
        }
        String errorMessage = throwable == null ? "" : throwable.getMessage();
        String stackTrace = throwable == null ? "" : getStackTraceAsString(throwable);

        // 将异常信息附加到快照数据中
        Object enhancedSnapshot = snapshot;
        if (throwable != null && snapshot instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshotMap = new LinkedHashMap<>((Map<String, Object>) snapshot);
            snapshotMap.put("exceptionClass", throwable.getClass().getName());
            snapshotMap.put("stackTrace", stackTrace);
            enhancedSnapshot = snapshotMap;
        } else if (throwable != null) {
            enhancedSnapshot = Map.of(
                "exceptionClass", throwable.getClass().getName(),
                "errorMessage", errorMessage,
                "stackTrace", stackTrace
            );
        }

        traceStageStore.finishStage(
            stageHandle.stageId(),
            ConversationTraceStageState.FAILED,
            summaryText,
            errorMessage,
            enhancedSnapshot,
            System.currentTimeMillis() - stageHandle.startTimeMs()
        );
    }

    /**
     * 将异常的堆栈信息转换为字符串。
     *
     * @param throwable 异常对象
     * @return 堆栈信息字符串
     */
    private String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 添加一条模型调用追踪记录。
     *
     * 由 ObservedChatModelService 在每次模型调用完成后调用，
     * 记录本次调用的 token 用量、耗时、费用估算等信息。
     *
     * @param trace 模型调用追踪记录
     */
    public void addModelUsageTrace(ChatModelUsageTrace trace) {
        if (trace != null) {
            modelUsageTraces.add(trace);
        }
    }

    /**
     * 获取模型调用追踪记录的快照（防御性拷贝）。
     *
     * @return 追踪记录列表的拷贝
     */
    public List<ChatModelUsageTrace> snapshotModelUsageTraces() {
        return new ArrayList<>(modelUsageTraces);
    }

    /**
     * 获取限制统计数据对象。
     *
     * @return 限制统计数据
     */
    public ChatLimitStats limitStats() {
        return limitStats;
    }

    /**
     * 记录检索结果快照到数据库。
     *
     * @param results 检索结果列表
     */
    public void recordRetrievalResults(List<RetrievalResultVo> results) {
        if (retrievalObserveStore == null || results == null || results.isEmpty()) {
            return;
        }
        try {
            retrievalObserveStore.batchSaveResults(conversationId, exchangeId, results);
        } catch (RuntimeException exception) {
            log.warn("记录检索结果快照失败, conversationId={}, exchangeId={}", conversationId, exchangeId, exception);
        }
    }

    /**
     * 记录通道执行详情到数据库。
     *
     * @param executions 通道执行详情列表
     */
    public void recordChannelExecutions(List<ChannelExecutionVo> executions) {
        if (retrievalObserveStore == null || executions == null || executions.isEmpty()) {
            return;
        }
        try {
            retrievalObserveStore.batchSaveChannelExecutions(conversationId, exchangeId, executions);
        } catch (RuntimeException exception) {
            log.warn("记录通道执行详情失败, conversationId={}, exchangeId={}", conversationId, exchangeId, exception);
        }
    }

    /**
     * 【阶段句柄】
     *
     * 不可变的 record 对象，代表一个已开始的追踪阶段。
     * 用于在 completeStage/failStage 时引用对应的阶段记录。
     *
     * @param stageId    阶段在数据库中的唯一 ID
     * @param startTimeMs 阶段开始时间戳（毫秒）
     * @param stageCode  阶段代码枚举
     */
    public record StageHandle(long stageId, long startTimeMs, ConversationTraceStageCode stageCode) {
    }
}
