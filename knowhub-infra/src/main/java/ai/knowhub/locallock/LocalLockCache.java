package ai.knowhub.locallock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地锁缓存类
 *
 * 【作用】：使用 Caffeine 本地缓存来管理 JVM 内部的 ReentrantLock 实例。
 *          同一个锁名称会复用同一个 ReentrantLock 对象，避免重复创建。
 *
 * 【为什么需要本地锁？】：
 * 分布式锁（Redis）虽然能跨 JVM 协调，但网络开销较大。
 * 在同一 JVM 内，先用本地锁快速拦截并发请求，再用分布式锁处理跨实例并发，
 * 这样可以大幅减少对 Redis 的访问压力。
 *
 * 【关键概念】：
 * - Caffeine：高性能的 Java 本地缓存库，类似 Guava Cache 但性能更好
 * - ReentrantLock：Java 并发包中的可重入锁，支持公平锁和非公平锁
 * - expireAfterWrite：写入后指定时间过期，防止缓存无限增长
 *
 * 【设计模式】：缓存模式 —— 使用 Caffeine 缓存锁对象，自动过期回收。
 */
public class LocalLockCache {

    /**
     * Caffeine 缓存实例，key 为锁名称，value 为 ReentrantLock 对象
     */
    private Cache<String, ReentrantLock> localLockCache;

    /**
     * 缓存过期时间（单位：小时）
     * 默认值：48 小时
     * 可通过配置项 durationTime 覆盖
     */
    @Value("${durationTime:48}")
    private Integer durationTime;

    /**
     * 初始化方法，在 Bean 创建后自动调用
     * 【说明】：@PostConstruct 标注的方法会在依赖注入完成后执行，
     *          此处用于初始化 Caffeine 缓存实例。
     */
    @PostConstruct
    public void localLockCacheInit(){
        localLockCache = Caffeine.newBuilder()
                .expireAfterWrite(durationTime, TimeUnit.HOURS)  // 写入后 48 小时过期
                .build();
    }

    /**
     * 获取或创建指定名称的本地锁
     *
     * 【说明】：如果缓存中已存在该锁名称的 ReentrantLock，则直接返回；
     *          否则创建一个新的 ReentrantLock 并放入缓存。
     *
     * @param lockKey 锁名称（作为缓存的 key）
     * @param fair    是否使用公平锁（true = 公平锁，按请求顺序获取锁；false = 非公平锁，允许插队）
     * @return ReentrantLock 实例
     */
    public ReentrantLock getLock(String lockKey,boolean fair){
        // Caffeine 的 get 方法：如果 key 不存在，则通过 lambda 创建新值并缓存
        return localLockCache.get(lockKey, key -> new ReentrantLock(fair));
    }
}
