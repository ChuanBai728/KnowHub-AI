package ai.knowhub.chat.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG 管道专用线程池配置类。
 *
 * 在整个 RAG（检索增强生成）流水线中，有些操作比较耗时（例如检索文档、压缩历史摘要、后处理等），
 * 如果在 Servlet 请求线程里执行会阻塞 HTTP 连接。因此本类集中注册了三组线程池，让这些耗时任务
 * 在独立线程中异步执行，不占用 Web 请求线程。
 *
 * 三组线程池的用途
 * 
 *   <b>chatRagExecutorService</b>：核心 RAG 检索池，负责向量检索、关键词检索、RRF 融合等重 IO 操作
 *   <b>chatMemorySummaryExecutorService</b>：会话历史摘要池，负责将长对话历史压缩成摘要
 *   <b>chatPostProcessExecutorService</b>：后处理池，负责答案格式化、引用标注等收尾工作
 * 线程池参数说明
 * 每个池都是固定大小线程池（核心线程数 = 最大线程数），使用 LinkedBlockingQueue 做任务缓冲。
 * 当队列满时采用 ThreadPoolExecutor.CallerRunsPolicy（由调用线程自己执行任务），
 * 这是一种温和的限流策略——既不会丢弃任务，也不会无限堆积。
 *
 * 所有线程都设置为守护线程（daemon=true），这样 JVM 退出时不会因为等待这些线程而卡住。
 *
 * @see ChatRagProperties RAG 相关配置属性
 */
@Configuration
public class ChatRagExecutorConfiguration {

    /**
     * RAG 检索专用线程池。
     *
     * 核心线程数 8，队列容量 256。用于执行向量检索、关键词检索、证据融合等重 IO 操作。
     * 这些操作可能涉及 Elasticsearch、PostgreSQL 向量查询等外部调用，需要足够的并发度。
     *
     * @return 配置好的线程池执行器，Spring 容器关闭时自动 shutdown
     */
    @Bean(name = "chatRagExecutorService", destroyMethod = "shutdown")
    public ExecutorService chatRagExecutorService() {

        return newFixedThreadPool("chat-rag-executor-", 8, 256);
    }

    /**
     * 会话历史摘要压缩专用线程池。
     *
     * 核心线程数 2，队列容量 32。用于将长对话历史压缩成摘要，释放 Prompt 上下文空间。
     * 摘要操作需要调用大模型，频率较低，所以线程数和队列都比较小。
     *
     * @return 配置好的线程池执行器，Spring 容器关闭时自动 shutdown
     */
    @Bean(name = "chatMemorySummaryExecutorService", destroyMethod = "shutdown")
    public ExecutorService chatMemorySummaryExecutorService() {

        return newFixedThreadPool("chat-memory-summary-", 2, 32);
    }

    /**
     * 答案后处理专用线程池。
     *
     * 核心线程数 2，队列容量 64。用于答案格式化、引用标注、Markdown 渲染等收尾工作。
     * 后处理在模型流式输出完成后执行，频率适中。
     *
     * @return 配置好的线程池执行器，Spring 容器关闭时自动 shutdown
     */
    @Bean(name = "chatPostProcessExecutorService", destroyMethod = "shutdown")
    public ExecutorService chatPostProcessExecutorService() {
        return newFixedThreadPool("chat-post-process-", 2, 64);
    }

    /**
     * 创建固定大小线程池的工厂方法。
     *
     * 所有通过此方法创建的线程池具有以下共同特征：
     * 
     *   核心线程数 = 最大线程数（固定大小，不会动态伸缩）
     *   空闲线程存活 60 秒后回收
     *   使用有界队列缓冲任务，队列满时由调用线程执行（CallerRunsPolicy）
     *   守护线程，不阻止 JVM 退出
     *   线程名带前缀和自增编号，方便日志排查
     * @param threadNamePrefix 线程名前缀，例如 "chat-rag-executor-"，最终线程名形如 "chat-rag-executor-1"
     * @param poolSize         核心线程数（同时也是最大线程数）
     * @param queueCapacity    任务队列容量，超出后由调用线程执行
     * @return 配置好的线程池执行器
     */
    private ExecutorService newFixedThreadPool(String threadNamePrefix, int poolSize, int queueCapacity) {
        // 自增计数器，用于给每个线程编号（1, 2, 3, ...）
        AtomicInteger threadCounter = new AtomicInteger(1);

        return new ThreadPoolExecutor(
            poolSize,       // 核心线程数
            poolSize,       // 最大线程数（与核心线程数相同，固定大小）
            60L,            // 空闲线程存活时间
            TimeUnit.SECONDS, // 存活时间单位：秒
            new LinkedBlockingQueue<>(queueCapacity), // 有界任务队列
            runnable -> {
                // 自定义线程工厂：设置线程名和守护标志
                Thread thread = new Thread(runnable);
                thread.setName(threadNamePrefix + threadCounter.getAndIncrement());
                thread.setDaemon(true); // 守护线程，JVM 退出时自动结束
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
            // 拒绝策略：队列满时不丢弃任务，而是让提交任务的调用线程自己执行
            // 这相当于一种"背压"机制——当线程池忙不过来时，调用方会变慢，自然降低提交速度
        );
    }
}
