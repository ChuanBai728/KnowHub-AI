package ai.knowhub.config;

import ai.knowhub.lease.RedisLeaseManager;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

/**
 * 服务租约自动配置类
 *
 * 【作用】：注册 RedisLeaseManager Bean，用于分布式环境下的租约管理。
 *
 * 【使用场景】：
 * - 多实例部署时，确保某个定时任务或服务在同一时刻只在一个实例上运行
 * - 通过"获取租约 -> 定期续约 -> 释放租约"的机制实现分布式 Leader 选举
 */
public class ServiceLeaseAutoConfiguration {

    /**
     * 创建 Redis 租约管理器 Bean
     *
     * @param redissonClient Redisson 客户端
     * @return RedisLeaseManager 实例
     */
    @Bean
    public RedisLeaseManager redisLeaseManager(RedissonClient redissonClient) {
        return new RedisLeaseManager(redissonClient);
    }
}
