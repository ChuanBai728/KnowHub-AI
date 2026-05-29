package com.baidu.fsg.uid.buffer;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.baidu.fsg.uid.utils.PaddedAtomicLong;

/**
 * 环形缓冲区 - 高性能UID缓存的核心数据结构
 *
 * 【类的作用】
 * 这是整个缓存UID生成器的核心，实现了一个高性能的环形缓冲区（Ring Buffer）。
 * 它预先批量生成UID并缓存起来，当业务需要UID时直接从缓冲区取出，避免每次都实时生成。
 *
 * 【为什么需要环形缓冲区？】
 * 1. 性能优化：直接生成UID需要位运算和时间戳计算，从缓冲区取是O(1)操作
 * 2. 削峰填谷：应对突发的高并发请求，缓冲区起到"蓄水池"的作用
 * 3. 批量生成：一次性生成大量UID，减少锁竞争和系统调用次数
 *
 * 【环形缓冲区原理】
 * 想象一个圆形的数组，有两个指针：
 * - tail（尾指针）：指向最后放入UID的位置，由生产者（填充线程）移动
 * - cursor（游标）：指向下一个可以取出的位置，由消费者（业务线程）移动
 *
 * ┌─────────────────────────────────┐
 * │  环形缓冲区示意图                │
 * │                                 │
 * │    [0] [1] [2] [3] [4] [5]     │
 * │     ↑               ↑          │
 * │   cursor           tail        │
 * │   (取出)           (放入)       │
 * │                                 │
 * │  数据从cursor流向tail方向流动    │
 * └─────────────────────────────────┘
 *
 * 【设计模式】
 * 使用了"生产者-消费者"模式和"模板方法"模式：
 * - 生产者：BufferPaddingExecutor负责填充UID
 * - 消费者：业务线程通过take()取出UID
 * - 拒绝策略：可自定义的放入/取出被拒绝时的处理方式
 *
 * 【线程安全】
 * - 使用AtomicLong保证tail和cursor的原子性操作
 * - 使用PaddedAtomicLong避免伪共享（False Sharing）问题
 * - put()方法使用synchronized保证同一时刻只有一个生产者
 * - take()方法使用CAS操作实现无锁消费
 */
public class RingBuffer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RingBuffer.class);

    /**
     * 起始点：-1
     * 表示缓冲区刚初始化，还没有放入任何UID
     * tail和cursor都从-1开始，第一次put后tail变为0
     */
    private static final int START_POINT = -1;

    /**
     * 可放入标志：0
     * 表示该槽位可以放入新的UID
     */
    private static final long CAN_PUT_FLAG = 0L;

    /**
     * 可取出标志：1
     * 表示该槽位已经有UID，可以取出了
     */
    private static final long CAN_TAKE_FLAG = 1L;

    /**
     * 默认的填充阈值百分比：50%
     * 当缓冲区剩余UID低于50%时，触发异步填充
     */
    public static final int DEFAULT_PADDING_PERCENT = 50;

    /**
     * 缓冲区大小（必须是2的幂次方）
     * 例如：1024、2048、4096等
     * 使用2的幂次方可以用位运算代替取模运算，提高性能
     */
    private final int bufferSize;

    /**
     * 索引掩码：bufferSize - 1
     * 用于将任意long值映射到数组索引范围内
     * 例如bufferSize=1024时，indexMask=1023（二进制：1111111111）
     * sequence & indexMask 等价于 sequence % bufferSize，但位运算更快
     */
    private final long indexMask;

    /**
     * 存储UID的数组（环形缓冲区的核心存储）
     * 每个槽位存储一个UID
     */
    private final long[] slots;

    /**
     * 标志数组：记录每个槽位的状态
     * - CAN_PUT_FLAG(0)：该槽位空闲，可以放入UID
     * - CAN_TAKE_FLAG(1)：该槽位有UID，可以取出
     * 使用PaddedAtomicLong避免伪共享问题
     */
    private final PaddedAtomicLong[] flags;

    /**
     * 尾指针：指向最后放入UID的位置
     * 由生产者（BufferPaddingExecutor）移动
     * 使用PaddedAtomicLong避免伪共享
     */
    private final AtomicLong tail = new PaddedAtomicLong(START_POINT);

    /**
     * 游标：指向下一个可以取出UID的位置
     * 由消费者（业务线程）移动
     * 使用PaddedAtomicLong避免伪共享
     */
    private final AtomicLong cursor = new PaddedAtomicLong(START_POINT);

    /**
     * 填充阈值：当剩余UID数量低于此值时触发异步填充
     * 计算方式：bufferSize * paddingFactor / 100
     * 例如bufferSize=8192, paddingFactor=50时，paddingThreshold=4096
     */
    private final int paddingThreshold;

    /**
     * 拒绝放入处理器：当缓冲区满时调用
     * 默认实现：丢弃UID并记录警告日志
     */
    private RejectedPutBufferHandler rejectedPutHandler = this::discardPutBuffer;

    /**
     * 拒绝取出处理器：当缓冲区空时调用
     * 默认实现：抛出RuntimeException
     */
    private RejectedTakeBufferHandler rejectedTakeHandler = this::exceptionRejectedTakeBuffer;

    /**
     * 缓冲区填充执行器
     * 当触发填充阈值时，通过此执行器异步填充UID
     */
    private BufferPaddingExecutor bufferPaddingExecutor;

    /**
     * 构造函数（使用默认填充阈值50%）
     *
     * @param bufferSize 缓冲区大小，必须是2的幂次方
     */
    public RingBuffer(int bufferSize) {
        this(bufferSize, DEFAULT_PADDING_PERCENT);
    }

    /**
     * 构造函数
     *
     * @param bufferSize    缓冲区大小，必须是2的幂次方（如1024、2048、4096）
     * @param paddingFactor 填充阈值百分比（1-99），当剩余UID低于此百分比时触发填充
     *
     * 【为什么必须是2的幂次方？】
     * 因为使用位运算 sequence & (bufferSize - 1) 来计算数组索引，
     * 这要求bufferSize必须是2的幂次方，否则会导致索引不均匀分布。
     */
    public RingBuffer(int bufferSize, int paddingFactor) {

        // 校验参数合法性
        Assert.isTrue(bufferSize > 0L, "RingBuffer size must be positive");
        // Integer.bitCount计算二进制中1的个数，2的幂次方只有1个1
        Assert.isTrue(Integer.bitCount(bufferSize) == 1, "RingBuffer size must be a power of 2");
        Assert.isTrue(paddingFactor > 0 && paddingFactor < 100, "RingBuffer size must be positive");

        this.bufferSize = bufferSize;
        // 计算索引掩码：例如bufferSize=1024，indexMask=1023（二进制11个1）
        this.indexMask = bufferSize - 1;
        // 初始化UID存储数组
        this.slots = new long[bufferSize];
        // 初始化标志数组，所有槽位初始状态为CAN_PUT_FLAG（可放入）
        this.flags = initFlags(bufferSize);

        // 计算填充阈值
        this.paddingThreshold = bufferSize * paddingFactor / 100;
    }

    /**
     * 放入一个UID到缓冲区（生产者方法）
     *
     * @param uid 要放入的UID
     * @return true表示放入成功，false表示缓冲区已满
     *
     * 【执行流程】
     * 1. 检查缓冲区是否已满（tail - cursor == bufferSize - 1）
     * 2. 计算下一个放入位置的索引
     * 3. 检查该位置是否可以放入（flags[nextTailIndex] == CAN_PUT_FLAG）
     * 4. 将UID存入slots数组
     * 5. 将该位置的标志设为CAN_TAKE_FLAG（可取出）
     * 6. 移动tail指针
     *
     * 【线程安全】
     * 使用synchronized关键字，确保同一时刻只有一个线程执行put操作
     */
    public synchronized boolean put(long uid) {
        long currentTail = tail.get();
        long currentCursor = cursor.get();

        // 计算tail和cursor之间的距离
        // 如果cursor是起始点(-1)，则当作0处理
        long distance = currentTail - (currentCursor == START_POINT ? 0 : currentCursor);
        // 如果距离等于bufferSize-1，说明缓冲区已满
        if (distance == bufferSize - 1) {
            rejectedPutHandler.rejectPutBuffer(this, uid);
            return false;
        }

        // 计算下一个放入位置的索引
        int nextTailIndex = calSlotIndex(currentTail + 1);
        // 检查该位置是否可以放入
        if (flags[nextTailIndex].get() != CAN_PUT_FLAG) {
            rejectedPutHandler.rejectPutBuffer(this, uid);
            return false;
        }

        // 将UID存入对应位置
        slots[nextTailIndex] = uid;
        // 标记该位置为可取出状态
        flags[nextTailIndex].set(CAN_TAKE_FLAG);
        // 移动tail指针
        tail.incrementAndGet();

        return true;
    }

    /**
     * 从缓冲区取出一个UID（消费者方法）
     *
     * @return 取出的UID
     *
     * 【执行流程】
     * 1. 获取当前cursor位置
     * 2. 使用CAS操作将cursor向前移动一位
     * 3. 检查是否需要触发异步填充（剩余UID低于阈值）
     * 4. 如果cursor没有移动（缓冲区为空），调用拒绝处理器
     * 5. 从对应位置取出UID
     * 6. 将该位置的标志重置为CAN_PUT_FLAG（可放入）
     *
     * 【线程安全】
     * 使用AtomicLong的updateAndGet实现CAS操作，无锁设计，高并发性能好
     */
    public long take() {

        // 获取当前cursor位置
        long currentCursor = cursor.get();
        // 使用CAS操作将cursor向前移动一位
        // 如果cursor等于tail（没有新数据），cursor不会移动
        long nextCursor = cursor.updateAndGet(old -> old == tail.get() ? old : old + 1);

        // 校验：cursor不能后退
        Assert.isTrue(nextCursor >= currentCursor, "Curosr can't move back");

        // 检查是否需要触发异步填充
        long currentTail = tail.get();
        if (currentTail - nextCursor < paddingThreshold) {
            LOGGER.info("Reach the padding threshold:{}. tail:{}, cursor:{}, rest:{}", paddingThreshold, currentTail,
                    nextCursor, currentTail - nextCursor);
            // 触发异步填充
            bufferPaddingExecutor.asyncPadding();
        }

        // 如果cursor没有移动，说明缓冲区为空
        if (nextCursor == currentCursor) {
            rejectedTakeHandler.rejectTakeBuffer(this);
        }

        // 计算取出位置的索引
        int nextCursorIndex = calSlotIndex(nextCursor);
        // 校验：该位置必须是可取出状态
        Assert.isTrue(flags[nextCursorIndex].get() == CAN_TAKE_FLAG, "Curosr not in can take status");

        // 取出UID
        long uid = slots[nextCursorIndex];
        // 将该位置重置为可放入状态
        flags[nextCursorIndex].set(CAN_PUT_FLAG);

        return uid;
    }

    /**
     * 计算数组索引
     *
     * @param sequence 序列号（可以是很大的数字）
     * @return 映射到[0, bufferSize-1]范围内的索引
     *
     * 【位运算原理】
     * sequence & indexMask 等价于 sequence % bufferSize
     * 但位运算比取模运算快得多
     * 例如：sequence=1025, indexMask=1023(二进制11个1)
     *       1025 & 1023 = 1（取低11位）
     */
    protected int calSlotIndex(long sequence) {
        return (int) (sequence & indexMask);
    }

    /**
     * 默认的拒绝放入处理：丢弃UID并记录警告日志
     *
     * @param ringBuffer 环形缓冲区
     * @param uid        被拒绝的UID
     */
    protected void discardPutBuffer(RingBuffer ringBuffer, long uid) {
        LOGGER.warn("Rejected putting buffer for uid:{}. {}", uid, ringBuffer);
    }

    /**
     * 默认的拒绝取出处理：抛出运行时异常
     *
     * @param ringBuffer 环形缓冲区
     */
    protected void exceptionRejectedTakeBuffer(RingBuffer ringBuffer) {
        LOGGER.warn("Rejected take buffer. {}", ringBuffer);
        throw new RuntimeException("Rejected take buffer. " + ringBuffer);
    }

    /**
     * 初始化标志数组
     *
     * @param bufferSize 缓冲区大小
     * @return 初始化后的标志数组，所有标志都设为CAN_PUT_FLAG
     */
    private PaddedAtomicLong[] initFlags(int bufferSize) {
        PaddedAtomicLong[] flags = new PaddedAtomicLong[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            flags[i] = new PaddedAtomicLong(CAN_PUT_FLAG);
        }

        return flags;
    }

    /**
     * 获取尾指针位置
     */
    public long getTail() {
        return tail.get();
    }

    /**
     * 获取游标位置
     */
    public long getCursor() {
        return cursor.get();
    }

    /**
     * 获取缓冲区大小
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * 设置缓冲区填充执行器
     *
     * @param bufferPaddingExecutor 填充执行器
     */
    public void setBufferPaddingExecutor(BufferPaddingExecutor bufferPaddingExecutor) {
        this.bufferPaddingExecutor = bufferPaddingExecutor;
    }

    /**
     * 设置拒绝放入处理器
     *
     * @param rejectedPutHandler 自定义的拒绝放入处理器
     */
    public void setRejectedPutHandler(RejectedPutBufferHandler rejectedPutHandler) {
        this.rejectedPutHandler = rejectedPutHandler;
    }

    /**
     * 设置拒绝取出处理器
     *
     * @param rejectedTakeHandler 自定义的拒绝取出处理器
     */
    public void setRejectedTakeHandler(RejectedTakeBufferHandler rejectedTakeHandler) {
        this.rejectedTakeHandler = rejectedTakeHandler;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RingBuffer [bufferSize=").append(bufferSize)
               .append(", tail=").append(tail)
               .append(", cursor=").append(cursor)
               .append(", paddingThreshold=").append(paddingThreshold).append("]");

        return builder.toString();
    }

}
