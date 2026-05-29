package ai.knowhub.chat.rag.executor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import ai.knowhub.chat.model.trace.ConversationTraceStageCode;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.rag.support.ExecutorEventSupport;
import ai.knowhub.chat.service.ConversationTraceRecorder;
import ai.knowhub.chat.service.TaskInfo;
import ai.knowhub.chat.support.StreamEventWriter;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ReAct Agent 执行器 —— 让 Agent 自主推理、调用工具、生成答案。
 *
 * 设计模式：策略模式的具体策略
 * 本类是 ConversationExecutor 接口的一个实现，对应 ExecutionMode#REACT_AGENT 模式。
 *
 * 什么是 ReAct Agent
 * ReAct（Reasoning + Acting）是一种 AI Agent 框架，核心思想是让模型在回答问题时
 * "边推理边行动"：
 * 
 *   <b>思考（Thought）</b>：模型分析当前情况，决定下一步该做什么
 *   <b>行动（Action）</b>：调用工具（如搜索、计算器、API 等）获取信息
 *   <b>观察（Observation）</b>：获取工具返回的结果
 *   循环 1-3 直到模型认为信息足够，给出最终答案
 * 与其他执行器的区别
 * 
 *   <b>RagChatExecutor</b>：固定的"检索 → 组装 → 生成"流程，适合知识库问答
 *   <b>GraphOnlyExecutor / GraphThenEvidenceExecutor</b>：固定的图查询流程
 *   <b>ReactAgentExecutor</b>：Agent 自主决定是否调用工具、调用哪个工具，适合开放式问题
 * 使用场景
 * 当问题不适合固定 RAG 流程时使用，例如：
 * 
 *   需要联网搜索最新信息的问题
 *   需要调用多个工具协作完成的复杂问题
 *   超出知识库范围的开放性问题
 * @see ConversationExecutor 执行器接口
 * @see ReactAgent Spring AI Alibaba 的 ReAct Agent 实现
 */
@Component
public class ReactAgentExecutor implements ConversationExecutor {

    /**
     * ReAct Agent 实例，由 Spring AI Alibaba 框架提供。
     *
     * Agent 内部维护了一个状态图（State Graph），包含推理节点、工具调用节点、
     * 答案生成节点等。执行时 Agent 会自动在这些节点之间流转。
     */
    private final ReactAgent reactAgent;

    /**
     * SSE 流式事件写入器，用于向前端推送思考步骤等中间事件。
     */
    private final StreamEventWriter streamEventWriter;

    /**
     * 构造函数，注入依赖。
     *
     * @param businessChatReactAgent ReAct Agent 实例（Bean 名称为 businessChatReactAgent）
     * @param streamEventWriter      SSE 事件写入器
     */
    public ReactAgentExecutor(ReactAgent businessChatReactAgent,
                              StreamEventWriter streamEventWriter) {
        this.reactAgent = businessChatReactAgent;
        this.streamEventWriter = streamEventWriter;
    }

    /**
     * 返回本执行器对应的执行模式：REACT_AGENT（开放式 Agent）。
     *
     * @return ExecutionMode#REACT_AGENT
     */
    @Override
    public ExecutionMode mode() {
        return ExecutionMode.REACT_AGENT;
    }

    /**
     * 执行 ReAct Agent 推理流程。
     *
     * 把规划阶段得到的 agentQuestion 交给 ReAct Agent，由 Agent 自主进行
     * 推理、工具调用和最终回答输出。Agent 可能会调用搜索工具、MCP 工具等。
     *
     * @param taskInfo 任务上下文，包含执行计划（其中有 agentQuestion）
     * @return 流式文本响应
     */
    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        // ReactAgent 适合开放式问题：它可以边推理边决定是否调用搜索、工具或 MCP 能力。
        // 标记是否已经有文本流式输出（用于决定是否输出最终完成文本）
        AtomicBoolean streamedText = new AtomicBoolean(false);

        // 向前端推送思考事件
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "当前问题进入开放式 Agent 自主执行阶段。");

        // 记录调试信息
        taskInfo.debugTrace().getRetrievalNotes().add("当前问题走 ReactAgent 执行路径，由 Agent 自主决定是否调用联网搜索或其他工具。");

        // 开始追踪"ReAct Agent"阶段
        ConversationTraceRecorder.StageHandle agentStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(
                ConversationTraceStageCode.REACT_AGENT,
                mode().name(),
                "正在执行 ReAct Agent 推理与工具调用。",
                null
            );

        try {
            // 调用 Agent 的流式执行方法，传入问题和运行配置
            return reactAgent.stream(taskInfo.executionPlan().getAgentQuestion(), taskInfo.runnableConfig())
                .publishOn(Schedulers.boundedElastic()) // 在弹性线程池中处理输出
                .concatMap(output -> extractTextChunk(output, streamedText)) // 提取文本片段
                .doOnComplete(() -> {
                    // Agent 执行完成，记录追踪信息
                    if (taskInfo.traceRecorder() != null) {
                        taskInfo.traceRecorder().completeStage(agentStage, "ReAct Agent 执行完成。", Map.of(
                            "toolNames", taskInfo.debugTrace().getToolTraces() == null ? List.of() : taskInfo.debugTrace().getToolTraces(),
                            "usedTools", taskInfo.usedTools() == null ? List.of() : taskInfo.usedTools()
                        ));
                    }
                })
                .doOnError(error -> {
                    // Agent 执行失败，记录错误信息
                    if (taskInfo.traceRecorder() != null) {
                        taskInfo.traceRecorder().failStage(agentStage, "ReAct Agent 执行失败。", error.getMessage(), null);
                    }
                });
        }
        catch (GraphRunnerException exception) {
            // Agent 启动时就抛异常（如状态图构建失败），记录错误并返回 Flux.error
            if (taskInfo.traceRecorder() != null) {
                taskInfo.traceRecorder().failStage(agentStage, "ReAct Agent 执行失败。", exception.getMessage(), null);
            }
            return Flux.error(exception);
        }
    }

    /**
     * 从 Agent 输出中提取文本片段。
     *
     * Agent 在执行过程中会输出多种类型的事件：
     * 
     *   AGENT_MODEL_STREAMING：模型正在流式生成文本（中间推理或最终答案）
     *   AGENT_MODEL_FINISHED：模型生成完成
     *   其他：工具调用事件、状态变更事件等（不返回给用户）
     * 本方法只把模型文本片段提取出来返回给前端，工具调用等事件由追踪链路记录。
     *
     * @param output        Agent 的输出节点
     * @param streamedText  标记是否已经有流式文本输出
     * @return 包含文本的 Mono，如果没有文本则返回 Mono.empty()
     */
    private Mono<String> extractTextChunk(NodeOutput output, AtomicBoolean streamedText) {
        // Agent 会输出多种事件，这里只把模型文本片段转成最终回答流，工具事件由追踪链路记录。
        if (!(output instanceof StreamingOutput<?> streamingOutput)) {
            // 不是流式输出类型，忽略
            return Mono.empty();
        }

        // 提取文本内容
        String content = extractStreamingText(streamingOutput);
        if (StrUtil.isBlank(content)) {
            return Mono.empty();
        }

        // AGENT_MODEL_STREAMING：模型正在流式生成文本，直接返回
        if (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
            streamedText.set(true);
            return Mono.just(content);
        }

        // AGENT_MODEL_FINISHED：模型生成完成
        if (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_FINISHED) {
            // 如果已经有流式文本输出过，完成事件的文本不再重复返回
            if (streamedText.get()) {
                return Mono.empty();
            }
            // 如果没有流式文本（例如模型直接返回了完整文本），返回完成事件的文本
            return Mono.just(content);
        }

        // 其他类型的事件（工具调用等）不返回给用户
        return Mono.empty();
    }

    /**
     * 从 StreamingOutput 对象中提取文本内容。
     *
     * StreamingOutput 可能以不同方式携带文本：
     * 
     *   通过 message() 方法获取 Message 对象
     *   通过 getOriginData() 获取原始数据（可能是 Message 或 String）
     * @param streamingOutput 流式输出对象
     * @return 提取到的文本内容，如果没有则返回空字符串
     */
    private String extractStreamingText(StreamingOutput<?> streamingOutput) {
        // 优先从 message() 获取文本
        Message message = streamingOutput.message();
        if (message != null && StrUtil.isNotBlank(message.getText())) {
            return message.getText();
        }

        // 从原始数据中提取文本
        Object originData = streamingOutput.getOriginData();
        if (originData instanceof Message originMessage && StrUtil.isNotBlank(originMessage.getText())) {
            return originMessage.getText();
        }
        if (originData instanceof String text && StrUtil.isNotBlank(text)) {
            return text;
        }
        return "";
    }
}
