package ai.knowhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 基础配置属性类
 *
 * 【作用】：从 application.yml / application.properties 中读取 spring.redis.redisson.* 前缀的配置项，
 *          用于自定义 Redisson 客户端的线程池和网络参数。
 *
 * 【配置示例】（在 application.yml 中）：
 * spring:
 *   redis:
 *     redisson:
 *       threads: 16
 *       netty-threads: 32
 *       core-pool-size: 8
 *       maximum-pool-size: 16
 *       keep-alive-time: 30
 *       unit: SECONDS
 *       work-queue-size: 256
 *
 * 【相关注解说明】：
 * - @Data：Lombok 注解，自动生成 getter/setter/toString/equals/hashCode 方法
 * - @ConfigurationProperties：Spring Boot 注解，将配置文件中指定前缀的属性绑定到此 JavaBean
 */
@Data
@ConfigurationProperties(prefix = "spring.redis.redisson")
public class RedissonBaseProperties {

    /**
     * Redisson 用于处理 Redis 命令的线程数。
     * 默认值：16
     * 【说明】：每个线程都会与 Redis 建立一个连接，线程数越多并发能力越强，但也会占用更多系统资源。
     */
    private Integer threads = 16;

    /**
     * Netty 传输层的线程数。
     * 默认值：32
     * 【说明】：Netty 是 Redisson 底层使用的网络通信框架，此参数控制 Netty 的 I/O 线程数量。
     */
    private Integer nettyThreads = 32;

    /**
     * 自定义线程池的核心线程数。
     * 默认值：null（不自定义线程池，使用 Redisson 内置默认线程池）
     * 【说明】：当 corePoolSize 和 maximumPoolSize 都不为 null 时，会创建自定义的 ThreadPoolExecutor。
     */
    private Integer corePoolSize = null;

    /**
     * 自定义线程池的最大线程数。
     * 默认值：null
     */
    private Integer maximumPoolSize = null;

    /**
     * 线程池中空闲线程的存活时间。
     * 默认值：30
     * 【说明】：超过核心线程数的空闲线程，在此时间后会被回收。
     */
    private long keepAliveTime = 30;

    /**
     * keepAliveTime 的时间单位。
     * 默认值：TimeUnit.SECONDS（秒）
     */
    private TimeUnit unit = TimeUnit.SECONDS;

    /**
     * 线程池的工作队列容量。
     * 默认值：256
     * 【说明】：当所有核心线程都在忙时，新提交的任务会先进入此队列等待。
     *          队列满后才会创建新线程（直到最大线程数）。
     */
    private Integer workQueueSize = 256;
}
