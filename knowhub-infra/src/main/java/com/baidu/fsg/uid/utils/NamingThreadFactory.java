package com.baidu.fsg.uid.utils;

import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 命名线程工厂 - 为线程池中的线程提供有意义的名称
 *
 * 【类的作用】
 * 这是一个自定义的ThreadFactory实现，为线程池中的每个线程设置有意义的名称。
 * 在UID生成器中用于：
 * 1. RingBuffer-Padding-Worker：填充UID的工作线程
 * 2. RingBuffer-Padding-Schedule：定时调度线程
 *
 * 【为什么需要命名线程？】
 * 默认情况下，Java线程池中的线程名称是"pool-X-thread-Y"格式，不直观。
 * 使用有意义的名称可以：
 * 1. 在日志中更容易识别线程来源
 * 2. 在线程转储（thread dump）中快速定位问题
 * 3. 在监控工具中更清晰地展示线程信息
 *
 * 【设计模式】
 * 使用了"工厂方法"模式：
 * - 实现ThreadFactory接口
 * - 提供newThread()方法创建线程
 * - 支持自定义线程名称、是否守护线程、异常处理器
 *
 * 【线程命名规则】
 * 格式：{名称前缀}-{序列号}
 * 例如：RingBuffer-Padding-Worker-1, RingBuffer-Padding-Worker-2
 */
public class NamingThreadFactory implements ThreadFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(NamingThreadFactory.class);

    /** 线程名称前缀 */
    private String name;

    /** 是否是守护线程 */
    private boolean daemon;

    /** 未捕获异常处理器 */
    private UncaughtExceptionHandler uncaughtExceptionHandler;

    /**
     * 序列号映射表
     * Key：线程名称前缀
     * Value：该前缀的当前序列号
     * 使用ConcurrentHashMap保证线程安全
     */
    private final ConcurrentHashMap<String, AtomicLong> sequences;

    /**
     * 无参构造函数
     * 使用默认配置：名称为null（自动获取调用者类名），非守护线程，无自定义异常处理器
     */
    public NamingThreadFactory() {
        this(null, false, null);
    }

    /**
     * 带名称的构造函数
     *
     * @param name 线程名称前缀
     */
    public NamingThreadFactory(String name) {
        this(name, false, null);
    }

    /**
     * 带名称和守护标志的构造函数
     *
     * @param name   线程名称前缀
     * @param daemon 是否是守护线程
     */
    public NamingThreadFactory(String name, boolean daemon) {
        this(name, daemon, null);
    }

    /**
     * 完整构造函数
     *
     * @param name    线程名称前缀
     * @param daemon  是否是守护线程
     * @param handler 未捕获异常处理器
     */
    public NamingThreadFactory(String name, boolean daemon, UncaughtExceptionHandler handler) {
        this.name = name;
        this.daemon = daemon;
        this.uncaughtExceptionHandler = handler;
        this.sequences = new ConcurrentHashMap<String, AtomicLong>();
    }

    /**
     * 创建新线程
     *
     * @param r 线程要执行的任务
     * @return 创建好的线程对象
     *
     * 【执行流程】
     * 1. 创建Thread对象
     * 2. 设置是否为守护线程
     * 3. 生成线程名称（前缀 + 序列号）
     * 4. 设置异常处理器
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        // 设置是否为守护线程
        // 守护线程会在所有非守护线程结束后自动终止
        thread.setDaemon(this.daemon);

        // 生成线程名称
        String prefix = this.name;
        // 如果没有设置名称，自动获取调用者的类名作为前缀
        if (StringUtils.isBlank(prefix)) {
            prefix = getInvoker(2);
        }
        thread.setName(prefix + "-" + getSequence(prefix));

        // 设置异常处理器
        if (this.uncaughtExceptionHandler != null) {
            thread.setUncaughtExceptionHandler(this.uncaughtExceptionHandler);
        } else {
            // 默认异常处理器：记录错误日志
            thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    LOGGER.error("unhandled exception in thread: " + t.getId() + ":" + t.getName(), e);
                }
            });
        }

        return thread;
    }

    /**
     * 获取调用者的类名
     *
     * @param depth 调用栈深度
     * @return 调用者的类名（短名称，不含包名）
     */
    private String getInvoker(int depth) {
        Exception e = new Exception();
        StackTraceElement[] stes = e.getStackTrace();
        if (stes.length > depth) {
            return ClassUtils.getShortClassName(stes[depth].getClassName());
        }
        return getClass().getSimpleName();
    }

    /**
     * 获取指定前缀的下一个序列号
     *
     * @param invoker 线程名称前缀
     * @return 下一个序列号
     *
     * 【实现逻辑】
     * 1. 从sequences映射表中获取该前缀对应的AtomicLong
     * 2. 如果不存在，创建一个新的并放入映射表
     * 3. 原子递增并返回
     */
    private long getSequence(String invoker) {
        AtomicLong r = this.sequences.get(invoker);
        if (r == null) {
            r = new AtomicLong(0);
            // putIfAbsent：如果key不存在则放入，返回旧值
            AtomicLong previous = this.sequences.putIfAbsent(invoker, r);
            if (previous != null) {
                r = previous;
            }
        }

        return r.incrementAndGet();
    }

    /**
     * 获取线程名称前缀
     */
    public String getName() {
        return name;
    }

    /**
     * 设置线程名称前缀
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 判断是否是守护线程
     */
    public boolean isDaemon() {
        return daemon;
    }

    /**
     * 设置是否是守护线程
     */
    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    /**
     * 获取异常处理器
     */
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler;
    }

    /**
     * 设置异常处理器
     */
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler handler) {
        this.uncaughtExceptionHandler = handler;
    }

}
