package com.baidu.fsg.uid.buffer;

/**
 * 拒绝放入缓冲区的处理器接口
 *
 * 【接口的作用】
 * 当环形缓冲区已满，无法放入新的UID时，通过此接口处理被拒绝的情况。
 * 这是一个函数式接口（@FunctionalInterface），可以用Lambda表达式实现。
 *
 * 【设计模式】
 * 使用了"策略模式"（Strategy Pattern）：
 * - 定义了处理"放入被拒绝"情况的策略
 * - 默认实现是丢弃UID并记录日志
 * - 可以自定义实现，比如抛出异常、写入备用存储等
 *
 * 【使用场景】
 * 当调用RingBuffer.put()失败时（缓冲区已满），会调用此处理器。
 * 通常发生在：
 * 1. 消费速度太慢，缓冲区来不及消费
 * 2. 填充速度太快，超过了预设的缓冲区大小
 *
 * 【示例实现】
 * - 丢弃策略：(ringBuffer, uid) -> log.warn("UID rejected: {}", uid)
 * - 异常策略：(ringBuffer, uid) -> { throw new RuntimeException("Buffer full"); }
 * - 重试策略：(ringBuffer, uid) -> retryPutLater(ringBuffer, uid)
 */
@FunctionalInterface
public interface RejectedPutBufferHandler {

    /**
     * 处理被拒绝放入的UID
     *
     * @param ringBuffer 环形缓冲区对象，可用于获取缓冲区状态信息
     * @param uid        被拒绝放入的UID值
     */
    void rejectPutBuffer(RingBuffer ringBuffer, long uid);
}
