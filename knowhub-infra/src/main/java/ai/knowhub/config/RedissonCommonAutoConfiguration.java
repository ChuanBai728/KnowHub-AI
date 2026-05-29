package ai.knowhub.config;

import ai.knowhub.handle.RedissonDataHandle;
import ai.knowhub.locallock.LocalLockCache;
import ai.knowhub.lockinfo.factory.LockInfoHandleFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redisson 公共自动配置类
 *
 * 【作用】：Spring Boot 自动配置类，负责创建并注册整个 Redisson 框架所需的核心 Bean：
 *   1. RedissonClient —— Redisson 客户端，连接 Redis 服务器
 *   2. RedissonDataHandle —— Redis 数据操作工具（基于 Redisson Bucket）
 *   3. LocalLockCache —— 本地锁缓存（基于 Caffeine）
 *   4. LockInfoHandleFactory —— 锁信息处理工厂
 *
 * 【设计思路】：
 * - 使用 @AutoConfigureBefore 确保本配置在 Redisson 官方自动配置之前加载，
 *   这样可以覆盖官方默认的 RedissonClient 创建逻辑。
 * - 从 Spring Boot 的 RedisProperties 中读取 Redis 连接信息（host、port、password 等），
 *   再结合自定义的 RedissonBaseProperties 来构建 Redisson Config。
 *
 * 【关键注解】：
 * - @AutoConfigureBefore：指定自动配置的加载顺序，本类在 RedissonAutoConfigurationV2 和
 *   RedissonAutoConfiguration 之前加载
 * - @EnableConfigurationProperties：启用 RedissonBaseProperties 的配置绑定功能
 * - @Bean：将方法返回值注册为 Spring 容器中的 Bean
 */
@AutoConfigureBefore(value = {RedissonAutoConfigurationV2.class, RedissonAutoConfiguration.class})
@EnableConfigurationProperties(RedissonBaseProperties.class)
public class RedissonCommonAutoConfiguration {

    /**
     * 自定义线程池的线程编号计数器。
     * 使用 AtomicInteger 保证线程安全，用于给自定义线程池中的线程命名。
     */
    private final AtomicInteger executeTaskThreadCount = new AtomicInteger(1);

    /**
     * 创建 Redisson 客户端 Bean
     *
     * 【流程】：
     * 1. 创建 Redisson Config 对象
     * 2. 通过反射判断 RedisProperties 是否启用了 SSL（兼容不同 Spring Boot 版本）
     * 3. 配置单机模式的 Redis 连接地址、超时、数据库编号、密码
     * 4. 设置 Redisson 的线程数和 Netty 线程数
     * 5. 如果配置了自定义线程池参数，则创建 ThreadPoolExecutor 并注入
     * 6. 通过 Redisson.create() 创建客户端实例
     *
     * @param redisProperties        Spring Boot 内置的 Redis 连接属性（host、port、password 等）
     * @param redissonBaseProperties 自定义的 Redisson 线程池属性
     * @return RedissonClient 实例
     */
    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties, RedissonBaseProperties redissonBaseProperties){
        Config config = new Config();
        // 默认使用 redis:// 协议前缀
        String prefix = "redis://";
        // 通过反射获取 isSsl() 方法，兼容没有 SSL 配置的旧版本 Spring Boot
        Method method = ReflectionUtils.findMethod(RedisProperties.class, "isSsl");
        if (method != null && (Boolean)ReflectionUtils.invokeMethod(method, redisProperties)) {
            // 如果启用了 SSL，使用 redss:// 协议前缀
            prefix = "rediss://";
        }
        // 配置单机模式（Single Server）连接
        config.useSingleServer()
                .setAddress(prefix + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setConnectTimeout(1000)  // 连接超时 1 秒
                .setDatabase(redisProperties.getDatabase())
                .setPassword(redisProperties.getPassword());
        // 设置 Redisson 的工作线程数和 Netty 线程数
        config.setThreads(redissonBaseProperties.getThreads());
        config.setNettyThreads(redissonBaseProperties.getNettyThreads());
        // 如果配置了自定义线程池参数，则创建自定义线程池
        if (Objects.nonNull(redissonBaseProperties.getCorePoolSize()) &&
                Objects.nonNull(redissonBaseProperties.getMaximumPoolSize())) {
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                    redissonBaseProperties.getCorePoolSize(),     // 核心线程数
                    redissonBaseProperties.getMaximumPoolSize(),  // 最大线程数
                    redissonBaseProperties.getKeepAliveTime(),    // 空闲线程存活时间
                    redissonBaseProperties.getUnit(),             // 时间单位
                    new LinkedBlockingQueue<>(redissonBaseProperties.getWorkQueueSize()), // 工作队列
                    // 自定义线程工厂，给线程起一个有意义的名字，方便排查问题
                    r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                            "redisson-thread-" + executeTaskThreadCount.getAndIncrement()));
            config.setExecutor(threadPoolExecutor);
        }
        return Redisson.create(config);
    }

    /**
     * 创建 Redis 数据操作工具 Bean
     *
     * @param redissonClient Redisson 客户端
     * @return RedissonDataHandle 实例
     */
    @Bean
    public RedissonDataHandle redissonDataHandle(RedissonClient redissonClient){
        return new RedissonDataHandle(redissonClient);
    }

    /**
     * 创建本地锁缓存 Bean
     * 【作用】：缓存 JVM 内部的 ReentrantLock 实例，避免每次加锁都创建新对象。
     *
     * @return LocalLockCache 实例
     */
    @Bean
    public LocalLockCache localLockCache(){
        return new LocalLockCache();
    }

    /**
     * 创建锁信息处理工厂 Bean
     * 【作用】：根据锁信息类型（如 "repeat_execute_limit"、"service_lock"）获取对应的 LockInfoHandle 实现。
     *
     * @return LockInfoHandleFactory 实例
     */
    @Bean
    public LockInfoHandleFactory lockInfoHandleFactory(){
        return new LockInfoHandleFactory();
    }
}
