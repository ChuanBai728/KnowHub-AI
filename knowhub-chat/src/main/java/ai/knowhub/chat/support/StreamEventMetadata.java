package ai.knowhub.chat.support;

/**
 * 【流式事件元数据】
 *
 * 作用：封装 SSE（Server-Sent Events）流式事件的元数据信息。
 * 每个 SSE 事件可以携带此元数据，用于标识事件所属的会话和交换。
 *
 * 所属架构位置：属于 SSE 流式通信层，与 StreamEventWriter 配合使用。
 *
 * 设计模式说明：「记录类（Record）」模式（Java 16+），
 * Record 是不可变的数据载体，自动生成构造函数、getter、equals、hashCode、toString。
 * 比传统的 POJO 更简洁，适合用作简单的数据传输对象。
 *
 * 字段说明：
 * - conversationId：事件所属的会话 ID
 * - exchangeId：事件所属的交换 ID（一次问答的唯一标识）
 *
 * @param conversationId 会话 ID
 * @param exchangeId     交换 ID
 * @author knowhub
 */
public record StreamEventMetadata(
    String conversationId,
    Long exchangeId
) {
}
