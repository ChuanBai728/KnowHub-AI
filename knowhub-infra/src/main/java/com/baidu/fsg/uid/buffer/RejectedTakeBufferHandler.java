package com.baidu.fsg.uid.buffer;

/**
 * 拒绝从缓冲区取出的处理器接口
 *
 * 【接口的作用】
 * 当环形缓冲区为空，无法取出UID时，通过此接口处理被拒绝的情况。
 * 这是一个函数式接口（@FunctionalInterface），可以用Lambda表达式实现。
 *
 * 【设计模式】
 * 使用了"策略模式"（Strategy Pattern）：
 * - 定义了处理"取出被拒绝"情况的策略
 * - 默认实现是抛出RuntimeException
 * - 可以自定义实现，比如阻塞等待、返回默认值等
 *
 * 【使用场景】
 * 当调用RingBuffer.take()失败时（缓冲区为空），会调用此处理器。
 * 通常发生在：
 * 1. 消费速度太快，超过了填充速度
 * 2. 填充任务出现异常，未能及时补充
 * 3. 系统刚启动，缓冲区还未初始化完成
 *
 * 【示例实现】
 * - 异常策略：(ringBuffer) -> { throw new RuntimeException("No available UID"); }
 * - 阻塞策略：(ringBuffer) -> Thread.sleep(100); // 等待后重试
 * - 降级策略：(ringBuffer) -> generateFallbackId() // 使用备用方案生成ID
 */
@FunctionalInterface
public interface RejectedTakeBufferHandler {

    /**
     * 处理被拒绝取出的情况
     *
     * @param ringBuffer 环形缓冲区对象，可用于获取缓冲区状态信息
     */
    void rejectTakeBuffer(RingBuffer ringBuffer);
}
