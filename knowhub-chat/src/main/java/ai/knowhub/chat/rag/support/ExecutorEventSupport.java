package ai.knowhub.chat.rag.support;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.chat.service.TaskInfo;
import ai.knowhub.chat.support.SinkEmitHelper;
import ai.knowhub.chat.support.StreamEventWriter;

/**
 * 【执行器事件支持工具 — SSE 流式事件的"发射器"】
 *
 * 这个类是一个工具类，提供静态方法用于向客户端发送 SSE（Server-Sent Events）流式事件。
 * 在 RAG 执行过程中，执行器会通过这个类向客户端推送"思考过程"和"状态更新"。
 *
 * 什么是 SSE？
 * SSE 是一种服务器向客户端推送实时更新的技术。
 * 在聊天场景中，用户发送问题后，服务器不是等全部处理完再返回，
 * 而是边处理边推送中间状态（如"正在检索文档..."、"正在思考..."），
 * 让用户看到实时的处理进度。
 *
 * 提供的事件类型：
 * 1. publishThinking：推送"思考步骤"（如"正在分析问题意图..."）
 * 2. publishStatus：推送"状态更新"（如"检索完成，正在生成回答..."）
 *
 * 设计模式：工具类模式（Utility Class），私有构造函数，只有静态方法。
 *
 * 在 RAG 流水线中的位置：
 * 执行器（如 ReactAgentExecutor）-> 【本类：推送 SSE 事件】-> 客户端
 */
public final class ExecutorEventSupport {

    /** 私有构造函数，防止实例化 */
    private ExecutorEventSupport() {
    }

    /**
     * 推送"思考步骤"事件。
     *
     * 思考步骤会被添加到 taskInfo 的 thinkingSteps 列列中，
     * 并通过 SSE 推送给客户端，让用户看到 AI 的"思考过程"。
     *
     * @param taskInfo 任务信息（包含 sink 和 thinkingSteps）
     * @param writer   SSE 事件写入器
     * @param content  思考步骤内容
     */
    public static void publishThinking(TaskInfo taskInfo, StreamEventWriter writer, String content) {
        if (taskInfo == null || writer == null || StrUtil.isBlank(content)) {
            return;
        }
        taskInfo.thinkingSteps().add(content);
        SinkEmitHelper.emitNext(taskInfo.sink(), writer.thinking(content, taskInfo.eventMetadata()));
    }

    /**
     * 推送"状态更新"事件。
     *
     * 状态更新通过 SSE 推送给客户端，用于显示当前处理阶段。
     *
     * @param taskInfo 任务信息
     * @param writer   SSE 事件写入器
     * @param content  状态内容
     */
    public static void publishStatus(TaskInfo taskInfo, StreamEventWriter writer, String content) {
        if (taskInfo == null || writer == null || StrUtil.isBlank(content)) {
            return;
        }
        SinkEmitHelper.emitNext(taskInfo.sink(), writer.status(content, taskInfo.eventMetadata()));
    }
}
