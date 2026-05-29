package ai.knowhub.chat.support;

import reactor.core.publisher.Sinks;

/**
 * 【SSE 事件发射辅助工具类】
 *
 * 作用：封装 Reactor Sinks 的事件发射操作，提供线程安全的事件发送方法。
 * 在 SSE（Server-Sent Events）流式响应中，需要通过 Sinks.Many 向客户端推送事件，
 * 此工具类处理了发射过程中的各种异常情况。
 *
 * 所属架构位置：属于 SSE 流式通信的支持层。
 * 在对话处理过程中，各组件通过此类向 SSE 流中发射事件（如思考步骤、文本片段、引用等）。
 *
 * 设计模式说明：
 * 1. 「工具类模式（Utility Class）」—— 封装通用的事件发射逻辑，避免重复代码。
 * 2. 「不可实例化」—— 私有构造函数，所有方法都是静态的。
 * 3. 「同步保护」—— 使用 synchronized 关键字确保并发安全。
 *
 * 关键概念说明：
 * - Sinks.Many<T>：Reactor 提供的多播发射器，支持多个订阅者。
 * - EmitResult：发射结果，表示发射是否成功。
 * - FAIL_CANCELLED：订阅者已取消
 * - FAIL_TERMINATED：Sink 已终止
 * - FAIL_ZERO_SUBSCRIBER：没有订阅者
 *
 * @author knowhub
 */
public class SinkEmitHelper {

    /**
     * 私有构造函数，防止实例化
     */
    private SinkEmitHelper() {
    }

    /**
     * 安全地发射下一个事件
     *
     * @param sink    SSE 事件发射器，如果为 null 则静默忽略
     * @param payload 事件载荷（通常是 JSON 字符串），如果为 null 则静默忽略
     */
    public static void emitNext(Sinks.Many<String> sink, String payload) {

        if (sink == null || payload == null) {
            return;
        }

        // 使用 synchronized 确保并发安全
        synchronized (sink) {
            Sinks.EmitResult result = sink.tryEmitNext(payload);

            // 对于「已取消」「已终止」「无订阅者」的情况，静默忽略（不抛异常）
            if (result == Sinks.EmitResult.FAIL_CANCELLED
                || result == Sinks.EmitResult.FAIL_TERMINATED
                || result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                return;
            }
            // 其他失败情况抛出异常
            if (result.isFailure()) {
                throw new IllegalStateException("流式事件发送失败: " + result);
            }
        }
    }

    /**
     * 安全地发射完成信号
     *
     * 当 SSE 流结束时调用，通知所有订阅者流已完成。
     *
     * @param sink SSE 事件发射器，如果为 null 则静默忽略
     */
    public static void emitComplete(Sinks.Many<String> sink) {
        if (sink == null) {
            return;
        }
        synchronized (sink) {
            Sinks.EmitResult result = sink.tryEmitComplete();
            if (result == Sinks.EmitResult.FAIL_CANCELLED
                || result == Sinks.EmitResult.FAIL_TERMINATED
                || result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                return;
            }
            if (result.isFailure()) {
                throw new IllegalStateException("流式事件关闭失败: " + result);
            }
        }
    }
}
