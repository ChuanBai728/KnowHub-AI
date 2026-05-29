package com.baidu.fsg.uid.buffer;

import com.baidu.fsg.uid.utils.NamingThreadFactory;
import com.baidu.fsg.uid.utils.PaddedAtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 缓冲区填充执行器 - 负责向环形缓冲区批量填充UID
 *
 * 【类的作用】
 * 这个类是环形缓冲区的"后勤保障"，负责在UID快用完时自动补充新的UID。
 * 就像一个自动补货系统：当货架上的商品快卖完时，自动从仓库调货补充。
 *
 * 【工作流程】
 * 1. 初始化时创建线程池和定时调度器
 * 2. 当RingBuffer中剩余UID低于阈值时，触发异步填充
 * 3. 调用BufferedUidProvider批量生成UID
 * 4. 将生成的UID逐个放入RingBuffer中
 * 5. 可选：通过定时调度器定期检查并填充
 *
 * 【设计模式】
 * 使用了"生产者-消费者"模式：
 * - BufferPaddingExecutor是生产者，负责生产UID并放入缓冲区
 * - 业务线程是消费者，从缓冲区取出UID使用
 * - RingBuffer是缓冲区，起到削峰填谷的作用
 *
 * 【线程安全】
 * - 使用AtomicBoolean确保同一时刻只有一个填充任务在执行
 * - 使用PaddedAtomicLong避免伪共享（False Sharing）问题
 */
public class BufferPaddingExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RingBuffer.class);

    /** 工作线程名称前缀 */
    private static final String WORKER_NAME = "RingBuffer-Padding-Worker";

    /** 定时调度线程名称前缀 */
    private static final String SCHEDULE_NAME = "RingBuffer-Padding-Schedule";

    /** 默认的定时调度间隔：5分钟（300秒） */
    private static final long DEFAULT_SCHEDULE_INTERVAL = 5 * 60L;

    /**
     * 运行状态标志：确保同一时刻只有一个填充任务在执行
     * 使用CAS（Compare And Swap）操作保证原子性
     */
    private final AtomicBoolean running;

    /**
     * 上一次填充到的时间秒数
     * 记录已经为哪些秒生成过UID，避免重复生成
     * 使用PaddedAtomicLong避免伪共享，提高并发性能
     */
    private final PaddedAtomicLong lastSecond;

    /** 环形缓冲区引用，用于放入生成的UID */
    private final RingBuffer ringBuffer;

    /** UID提供者，负责批量生成UID */
    private final BufferedUidProvider uidProvider;

    /**
     * 填充任务的线程池
     * 当缓冲区UID不足时，通过此线程池异步执行填充任务
     */
    private final ExecutorService bufferPadExecutors;

    /**
     * 定时调度器
     * 可选功能，定期检查并填充缓冲区
     */
    private final ScheduledExecutorService bufferPadSchedule;

    /** 定时调度间隔（秒），默认5分钟 */
    private long scheduleInterval = DEFAULT_SCHEDULE_INTERVAL;

    /**
     * 构造函数（默认启用定时调度）
     *
     * @param ringBuffer   环形缓冲区
     * @param uidProvider  UID提供者
     */
    public BufferPaddingExecutor(RingBuffer ringBuffer, BufferedUidProvider uidProvider) {
        this(ringBuffer, uidProvider, true);
    }

    /**
     * 构造函数
     *
     * @param ringBuffer    环形缓冲区
     * @param uidProvider   UID提供者
     * @param usingSchedule 是否启用定时调度
     *
     * 【线程池配置】
     * - 核心线程数 = CPU核心数 * 2
     * - 最大线程数 = CPU核心数 * 2
     * - 使用LinkedBlockingQueue作为任务队列
     * - 使用NamingThreadFactory为线程命名，便于调试
     */
    public BufferPaddingExecutor(RingBuffer ringBuffer, BufferedUidProvider uidProvider, boolean usingSchedule) {
        // 初始化运行状态为false（未运行）
        this.running = new AtomicBoolean(false);
        // 记录当前时间的秒数作为起始点
        this.lastSecond = new PaddedAtomicLong(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        this.ringBuffer = ringBuffer;
        this.uidProvider = uidProvider;

        // 获取CPU核心数，用于配置线程池大小
        int cores = Runtime.getRuntime().availableProcessors();

        // 创建填充任务的线程池
        bufferPadExecutors = new ThreadPoolExecutor(cores * 2,cores * 2,0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),new NamingThreadFactory(WORKER_NAME));

        // 根据配置决定是否创建定时调度器
        if (usingSchedule) {
            bufferPadSchedule = new ScheduledThreadPoolExecutor(1, new NamingThreadFactory(SCHEDULE_NAME));
        } else {
            bufferPadSchedule = null;
        }
    }

    /**
     * 启动定时调度
     *
     * 【调度策略】
     * 使用fixedDelay模式：上一次执行完成后，等待指定间隔再执行下一次
     * 这样可以避免任务堆积
     */
    public void start() {
        if (bufferPadSchedule != null) {
            bufferPadSchedule.scheduleWithFixedDelay(() -> paddingBuffer(), scheduleInterval, scheduleInterval, TimeUnit.SECONDS);
        }
    }

    /**
     * 关闭所有线程池，释放资源
     *
     * 【调用时机】
     * 在CachedUidGenerator销毁时调用（实现DisposableBean接口）
     */
    public void shutdown() {
        if (!bufferPadExecutors.isShutdown()) {
            bufferPadExecutors.shutdownNow();
        }

        if (bufferPadSchedule != null && !bufferPadSchedule.isShutdown()) {
            bufferPadSchedule.shutdownNow();
        }
    }

    /**
     * 判断当前是否正在执行填充任务
     *
     * @return true表示正在填充，false表示空闲
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 异步触发缓冲区填充
     *
     * 【调用时机】
     * 当RingBuffer.take()检测到剩余UID低于阈值时调用
     * 提交任务到线程池后立即返回，不阻塞调用线程
     */
    public void asyncPadding() {
        bufferPadExecutors.submit(this::paddingBuffer);
    }

    /**
     * 执行缓冲区填充（核心方法）
     *
     * 【执行流程】
     * 1. 使用CAS操作将running从false设为true，确保只有一个线程在填充
     * 2. 如果CAS失败，说明已有线程在填充，直接返回
     * 3. 循环生成UID并放入RingBuffer，直到RingBuffer满了
     * 4. 填充完成后，将running重置为false
     *
     * 【防并发机制】
     * 使用AtomicBoolean的compareAndSet实现无锁的互斥：
     * - compareAndSet(false, true)：只有当前值为false时才能设为true
     * - 如果多个线程同时调用，只有一个能成功，其他直接返回
     */
    public void paddingBuffer() {
        LOGGER.info("Ready to padding buffer lastSecond:{}. {}", lastSecond.get(), ringBuffer);

        // CAS操作：尝试将running从false改为true
        // 如果失败，说明已有其他线程在执行填充，直接返回
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("Padding buffer is still running. {}", ringBuffer);
            return;
        }

        boolean isFullRingBuffer = false;
        // 循环填充，直到RingBuffer满了
        while (!isFullRingBuffer) {
            // 递增时间秒数，获取该秒的所有UID
            List<Long> uidList = uidProvider.provide(lastSecond.incrementAndGet());
            // 将每个UID放入RingBuffer
            for (Long uid : uidList) {
                isFullRingBuffer = !ringBuffer.put(uid);
                // 如果RingBuffer满了，停止填充
                if (isFullRingBuffer) {
                    break;
                }
            }
        }

        // 填充完成，将running重置为false
        running.compareAndSet(true, false);
        LOGGER.info("End to padding buffer lastSecond:{}. {}", lastSecond.get(), ringBuffer);
    }

    /**
     * 设置定时调度间隔
     *
     * @param scheduleInterval 调度间隔（秒），必须大于0
     */
    public void setScheduleInterval(long scheduleInterval) {
        Assert.isTrue(scheduleInterval > 0, "Schedule interval must positive!");
        this.scheduleInterval = scheduleInterval;
    }

}
