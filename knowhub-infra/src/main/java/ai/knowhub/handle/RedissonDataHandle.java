package ai.knowhub.handle;

import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 数据操作工具类
 *
 * 【作用】：封装 Redisson 客户端的常用数据操作（get/set），提供简洁的 API。
 *          底层使用 Redisson 的 RBucket（Redis String 类型）来存储键值对。
 *
 * 【使用场景】：
 * - 重复执行限制：在 Redis 中标记某个操作是否已成功执行过
 * - 通用的键值存取场景
 *
 * 【关键概念】：
 * - RBucket：Redisson 对 Redis String 类型的封装，支持 get/set 操作
 * - TTL（Time To Live）：键的过期时间，到期后 Redis 会自动删除该键
 *
 * 【设计模式】：工具类模式 —— 对底层 Redisson API 进行简化封装。
 */
@AllArgsConstructor
public class RedissonDataHandle {

    /**
     * Redisson 客户端实例，用于与 Redis 服务器通信
     */
    private final RedissonClient redissonClient;

    /**
     * 根据 key 从 Redis 获取值
     *
     * @param key Redis 键名
     * @return 键对应的值（String 类型），如果键不存在返回 null
     */
    public String get(String key){
        return (String)redissonClient.getBucket(key).get();
    }

    /**
     * 向 Redis 设置键值对（永不过期）
     *
     * @param key   Redis 键名
     * @param value 要存储的值
     */
    public void set(String key,String value){
        redissonClient.getBucket(key).set(value);
    }

    /**
     * 向 Redis 设置键值对，并指定过期时间
     *
     * @param key         Redis 键名
     * @param value       要存储的值
     * @param timeToLive  过期时间数值
     * @param timeUnit    过期时间单位
     */
    public void set(String key,String value,long timeToLive, TimeUnit timeUnit){
        redissonClient.getBucket(key).set(value,getDuration(timeToLive,timeUnit));
    }

    /**
     * 将数值 + 时间单位转换为 Java 的 Duration 对象
     *
     * 【说明】：Redisson 的 set 方法接受 Duration 类型的过期时间，
     *          此方法负责将常见的 TimeUnit 转换为 Duration。
     *
     * @param timeToLive 时间数值
     * @param timeUnit   时间单位（支持 MINUTES、HOURS、DAYS，默认为 SECONDS）
     * @return Duration 对象
     */
    public Duration getDuration(long timeToLive, TimeUnit timeUnit){
        switch (timeUnit) {

            case MINUTES -> {
                return Duration.ofMinutes(timeToLive);
            }

            case HOURS -> {
                return Duration.ofHours(timeToLive);
            }

            case DAYS -> {
                return Duration.ofDays(timeToLive);
            }

            default -> {
                return Duration.ofSeconds(timeToLive);
            }
        }
    }
}
