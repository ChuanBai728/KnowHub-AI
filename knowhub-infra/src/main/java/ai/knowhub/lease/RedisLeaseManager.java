package ai.knowhub.lease;

import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;

/**
 * Redis 租约管理器
 *
 * 【作用】：基于 Redis 实现分布式租约（Lease）机制，用于协调多个服务实例对共享资源的独占访问。
 *
 * 【什么是租约？】：
 * 租约是一种"有时限的授权"。服务 A 获取租约后，在 TTL 时间内独占某个资源；
 * 如果服务 A 想继续持有，必须在 TTL 到期前续约；否则租约自动过期，其他服务可以获取。
 *
 * 【典型使用场景】：
 * - 分布式定时任务：同一时刻只有一个实例执行定时任务
 * - Leader 选举：多个实例竞争成为 Leader
 * - 资源独占：确保某个操作不会被多个实例同时执行
 *
 * 【核心操作】：
 * 1. acquire（获取）：尝试获取租约，成功返回 true
 * 2. renew（续约）：延长租约的 TTL，防止过期
 * 3. release（释放）：主动释放租约
 *
 * 【关键概念 - Lua 脚本】：
 * 所有操作都通过 Redis Lua 脚本实现原子性。Lua 脚本在 Redis 服务器端执行，
 * 保证"判断 + 写入"是一个不可分割的原子操作，避免并发问题。
 *
 * 【ownerToken 机制】：
 * 每个服务实例生成唯一的 ownerToken（如 UUID），在 acquire/renew/release 时都要传入。
 * 这样可以防止 A 服务误操作 B 服务的租约（只有持有者才能续租和释放）。
 *
 * 【设计模式】：基于 Lua 脚本的原子操作模式
 */
public class RedisLeaseManager {

    /**
     * 获取租约的 Lua 脚本
     *
     * 【逻辑】：
     * 1. 检查 key 是否存在（redis.call('exists', KEYS[1])）
     * 2. 如果不存在（== 0），则写入 ownerToken 并设置过期时间（psetex），返回 1（成功）
     * 3. 如果已存在，返回 0（失败，说明已被其他实例持有）
     *
     * 【原子性保证】：exists 判断和 psetex 写入在同一个 Lua 脚本中执行，不会被其他命令插入。
     *
     * KEYS[1]：租约的 Redis key
     * ARGV[1]：ownerToken（持有者标识）
     * ARGV[2]：TTL（过期时间，毫秒）
     */
    private static final String ACQUIRE_SCRIPT =
        "if redis.call('exists', KEYS[1]) == 0 then "
            + "redis.call('psetex', KEYS[1], ARGV[2], ARGV[1]); "
            + "return 1; "
            + "end; "
            + "return 0;";

    /**
     * 续约的 Lua 脚本
     *
     * 【逻辑】：
     * 1. 获取 key 当前的值（redis.call('get', KEYS[1])）
     * 2. 如果等于 ARGV[1]（ownerToken），则更新过期时间（pexpire），返回 1（成功）
     * 3. 如果不等于，返回 0（失败，说明不是持有者）
     *
     * 【安全机制】：只有持有者才能续约，防止 A 服务续了 B 服务的租约。
     *
     * KEYS[1]：租约的 Redis key
     * ARGV[1]：ownerToken
     * ARGV[2]：新的 TTL（毫秒）
     */
    private static final String RENEW_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "redis.call('pexpire', KEYS[1], ARGV[2]); "
            + "return 1; "
            + "end; "
            + "return 0;";

    /**
     * 释放租约的 Lua 脚本
     *
     * 【逻辑】：
     * 1. 获取 key 当前的值
     * 2. 如果等于 ownerToken，则删除 key（del），返回 1（成功）
     * 3. 如果不等于，返回 0（失败）
     *
     * 【安全机制】：只有持有者才能释放，防止误删别人的租约。
     *
     * KEYS[1]：租约的 Redis key
     * ARGV[1]：ownerToken
     */
    private static final String RELEASE_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]); "
            + "end; "
            + "return 0;";

    /**
     * Redisson 客户端，用于执行 Lua 脚本
     */
    private final RedissonClient redissonClient;

    public RedisLeaseManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取租约
     *
     * @param key         租约的 Redis key（通常是资源标识符）
     * @param ownerToken  持有者标识（通常是 UUID）
     * @param ttl         租约的存活时间
     * @return true = 获取成功，false = 已被其他实例持有
     */
    public boolean acquire(String key, String ownerToken, Duration ttl) {
        // 获取租约成功返回 true；失败表示已经有其他执行者持有这个 key
        return executeInteger(ACQUIRE_SCRIPT, key, ownerToken, ttl) == 1L;
    }

    /**
     * 续约（延长租约的 TTL）
     *
     * 【使用场景】：长任务执行过程中，定期调用此方法续约，防止租约过期被其他实例抢占。
     *
     * @param key         租约的 Redis key
     * @param ownerToken  持有者标识
     * @param ttl         新的存活时间
     * @return true = 续约成功，false = 不是持有者或租约已过期
     */
    public boolean renew(String key, String ownerToken, Duration ttl) {
        // 长任务要定期续约，否则 Redis key 到期后会被自动删除
        return executeInteger(RENEW_SCRIPT, key, ownerToken, ttl) == 1L;
    }

    /**
     * 释放租约
     *
     * 【使用场景】：任务正常结束或异常退出时，主动释放租约，让其他实例可以尽快获取。
     *
     * @param key         租约的 Redis key
     * @param ownerToken  持有者标识
     * @return true = 释放成功，false = 不是持有者或已过期
     */
    public boolean release(String key, String ownerToken) {
        // 主动释放通常发生在任务正常结束或失败收尾时
        Assert.hasText(key, "key 不能为空");
        Assert.hasText(ownerToken, "ownerToken 不能为空");
        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE,       // 读写模式（因为要执行 del 操作）
            RELEASE_SCRIPT,                // Lua 脚本
            RScript.ReturnType.INTEGER,    // 返回类型：整数
            List.of(key),                  // KEYS 列表
            ownerToken                     // ARGV 列表
        );
        return result != null && result == 1L;
    }

    /**
     * 执行 Lua 脚本并返回整数结果（内部通用方法）
     *
     * @param script       Lua 脚本内容
     * @param key          Redis key
     * @param ownerToken   持有者标识
     * @param ttl          过期时间
     * @return 脚本返回的整数值（0 或 1）
     */
    private long executeInteger(String script, String key, String ownerToken, Duration ttl) {
        // 参数校验
        Assert.hasText(key, "key 不能为空");
        Assert.hasText(ownerToken, "ownerToken 不能为空");
        Assert.notNull(ttl, "ttl 不能为空");
        Assert.isTrue(!ttl.isNegative() && !ttl.isZero(), "ttl 必须大于 0");

        // 通过 Redisson 的 RScript API 执行 Lua 脚本
        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE,       // 读写模式
            script,                        // Lua 脚本
            RScript.ReturnType.INTEGER,    // 返回类型
            List.of(key),                  // KEYS 参数列表
            ownerToken,                    // ARGV[1] = ownerToken
            String.valueOf(ttl.toMillis()) // ARGV[2] = TTL（毫秒）
        );
        return result != null ? result : 0L;
    }
}
