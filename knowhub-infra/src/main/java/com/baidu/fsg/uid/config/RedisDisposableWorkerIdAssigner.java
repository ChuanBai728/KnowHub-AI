package com.baidu.fsg.uid.config;

import com.baidu.fsg.uid.worker.WorkerIdAssigner;
import ai.knowhub.enums.BaseCode;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

/**
 * 基于Redis的一次性Worker ID分配器
 *
 * 【类的作用】
 * 这个类通过Redis的原子自增操作为每个实例分配唯一的Worker ID。
 * 每次调用assignWorkerId()都会获得一个新的、递增的Worker ID。
 *
 * 【Worker ID是什么？】
 * 在百度 UID 算法中，64位ID被分成几部分，其中一部分是Worker ID。
 * Worker ID用于标识"哪台服务器生成的这个ID"。
 * 不同服务器必须有不同的Worker ID，否则会生成重复的ID。
 *
 * 【为什么用Redis分配？】
 * 1. 简单可靠：Redis的INCR操作是原子性的，天然保证唯一性
 * 2. 分布式友好：所有实例共享同一个Redis，ID不会冲突
 * 3. 无需人工配置：不需要手动为每台服务器分配ID
 *
 * 【注意事项】
 * 这里分配的是"一次性"Worker ID，意味着：
 * - 每次应用启动都会获得新的Worker ID
 * - 之前的Worker ID不会被回收
 * - 适用于Worker ID位数足够多的场景（如22位可支持约419万个ID）
 *
 * 【设计模式】
 * 使用了"策略模式"：实现了WorkerIdAssigner接口，可以与其他分配策略互换。
 */
public class RedisDisposableWorkerIdAssigner implements WorkerIdAssigner {

    /** Redis操作模板 */
    private RedisTemplate redisTemplate;

    /**
     * 构造函数
     *
     * @param redisTemplate Redis操作模板
     */
    public RedisDisposableWorkerIdAssigner (RedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    /**
     * 分配Worker ID
     *
     * @return 唯一的Worker ID（从1开始递增）
     * @throws KnowHubFrameException 当Redis操作失败时抛出异常
     *
     * 【实现原理】
     * 使用Redis的INCR命令对key "uid_work_id" 进行原子自增：
     * - 第一次调用返回1
     * - 第二次调用返回2
     * - 以此类推...
     *
     * 【为什么是原子操作？】
     * Redis的INCR命令是单线程执行的，即使多个实例同时调用，
     * Redis也会串行处理，保证每个实例获得不同的值。
     */
    @Override
    public long assignWorkerId() {
        String key = "uid_work_id";
        // 使用Redis的原子自增操作
        Long increment = redisTemplate.opsForValue().increment(key);
        // 如果返回null（Redis异常），抛出业务异常
        return Optional.ofNullable(increment).orElseThrow(() -> new KnowHubFrameException(BaseCode.UID_WORK_ID_ERROR));
    }
}
