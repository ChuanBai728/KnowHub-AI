package com.baidu.fsg.uid.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 填充的原子Long - 解决伪共享问题的高性能原子变量
 *
 * 【类的作用】
 * 这是AtomicLong的子类，通过添加填充字段（padding）来解决"伪共享"（False Sharing）问题，
 * 从而提高高并发场景下的性能。
 *
 * 【什么是伪共享（False Sharing）？】
 * 现代CPU使用缓存行（Cache Line，通常64字节）来加载内存数据。
 * 当两个不同的变量恰好在同一个缓存行中时：
 * - 即使它们被不同的线程访问
 * - 一个线程修改变量A会导致另一个线程的变量B的缓存失效
 * - 这种现象叫做"伪共享"
 *
 * 【伪共享的影响】
 * 在RingBuffer中，tail和cursor是两个不同的AtomicLong：
 * - 生产者线程修改tail
 * - 消费者线程修改cursor
 * - 如果它们在同一个缓存行，会导致频繁的缓存失效，降低性能
 *
 * 【解决方案】
 * 在AtomicLong前后添加填充字段（p1-p6），确保每个PaddedAtomicLong独占一个缓存行。
 * 每个填充字段是8字节（long类型），6个字段共48字节，加上AtomicLong本身的8字节，
 * 总共56字节，足以确保不会与其他变量共享缓存行。
 *
 * 【性能提升】
 * 在高并发场景下（如大量线程同时访问RingBuffer），
 * 使用PaddedAtomicLong可以显著减少缓存行争用，提升吞吐量。
 *
 * 【设计模式】
 * 使用了"装饰器"模式：
 * - 继承AtomicLong，保持原有功能
 * - 添加填充字段，优化并发性能
 *
 * 【参考】
 * - Java 8的@Contended注解也可以解决伪共享问题
 * - 但需要JVM参数 -XX:-RestrictContended 才能生效
 * - 这种手动填充方式兼容性更好
 */
public class PaddedAtomicLong extends AtomicLong {
    private static final long serialVersionUID = -3415778863941386253L;

    /**
     * 填充字段：用于填充缓存行，避免伪共享
     * 每个字段8字节，6个字段共48字节
     * 加上对象头（约16字节）和AtomicLong的value字段（8字节），
     * 总大小约72字节，超过一个缓存行（64字节）
     *
     * volatile关键字确保这些字段不会被JVM优化掉
     */
    public volatile long p1, p2, p3, p4, p5, p6 = 7L;

    /**
     * 无参构造函数
     */
    public PaddedAtomicLong() {
        super();
    }

    /**
     * 带初始值的构造函数
     *
     * @param initialValue 初始值
     */
    public PaddedAtomicLong(long initialValue) {
        super(initialValue);
    }

    /**
     * 防止JVM优化掉填充字段的方法
     *
     * @return 所有填充字段的和
     *
     * 【作用】
     * JVM可能会优化掉未使用的字段，这个方法通过"读取"填充字段
     * 来防止JVM将它们优化掉，确保填充效果。
     * 在实际使用中，这个方法通常不会被调用。
     */
    public long sumPaddingToPreventOptimization() {
        return p1 + p2 + p3 + p4 + p5 + p6;
    }

}
