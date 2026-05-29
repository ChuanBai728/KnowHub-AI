package ai.knowhub.chat.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Flux;

/**
 * 【会话引导结果】
 *
 * 封装会话启动（bootstrap）阶段的产出结果，是一个典型的"结果对象"（Result Object）模式。
 * 会话启动可能有两种结局：
 *   1. 成功（ready）：拿到一个 Flux<String> 输出流，后续 SSE 推送给前端。
 *   2. 被拒绝（rejected）：拿到一条拒绝原因文本，前端收到错误事件。
 *
 * 使用场景：BusinessChatService.bootstrapConversation() 方法会返回此对象，
 * 调用方根据 rejectionMessage 是否为空来判断启动是否成功。
 *
 * 设计模式：使用静态工厂方法（ready / rejected）代替 new，语义更清晰。
 */
@Data
@AllArgsConstructor
public class BootstrapResult {

    /**
     * SSE 输出流（Server-Sent Events）。
     * 当会话启动成功时，这个 Flux 会持续推送文本片段给前端浏览器。
     * 如果启动被拒绝，这里会是 Flux.empty()，即空流。
     */
    private final Flux<String> outbound;

    /**
     * 拒绝消息。
     * 当会话启动成功时，这里为空字符串 ""；
     * 当启动被拒绝时（例如同一会话正在执行中），这里存放拒绝原因。
     */
    private final String rejectionMessage;

    /**
     * 静态工厂方法：创建一个"启动成功"的结果。
     *
     * @param outbound SSE 输出流，包含后续模型生成的文本内容
     * @return 成功的 BootstrapResult，rejectionMessage 为空字符串
     */
    public static BootstrapResult ready(Flux<String> outbound) {
        return new BootstrapResult(outbound, "");
    }

    /**
     * 静态工厂方法：创建一个"启动被拒绝"的结果。
     *
     * @param rejectionMessage 拒绝原因，例如"该会话当前正在执行中，请稍后再试"
     * @return 被拒绝的 BootstrapResult，outbound 为空流
     */
    public static BootstrapResult rejected(String rejectionMessage) {
        return new BootstrapResult(Flux.empty(), rejectionMessage);
    }
}
