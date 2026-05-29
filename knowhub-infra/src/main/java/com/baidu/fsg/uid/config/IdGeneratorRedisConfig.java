package com.baidu.fsg.uid.config;

import com.baidu.fsg.uid.worker.WorkerIdAssigner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * ID生成器的Redis配置类
 *
 * 【类的作用】
 * 这个配置类负责创建ID生成器所需的Redis相关Bean。
 * 只有在配置了spring.data.redis.host属性时才会生效。
 *
 * 【配置的Bean】
 * 1. idGeneratorRedisTemplate - 专门用于ID生成器的RedisTemplate
 * 2. disposableWorkerIdAssigner - 基于Redis的Worker ID分配器
 *
 * 【设计模式】
 * 使用了Spring的@Configuration和@Bean注解，实现"依赖注入"和"控制反转"。
 * 使用@ConditionalOnProperty实现条件装配，只有配置了Redis地址才创建这些Bean。
 *
 * 【为什么需要单独的RedisTemplate？】
 * ID生成器使用独立的RedisTemplate，可以：
 * 1. 使用不同的序列化方式（这里用StringRedisSerializer）
 * 2. 使用不同的Redis数据库或key前缀
 * 3. 避免与其他业务的Redis操作相互影响
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.data.redis.host")
public class IdGeneratorRedisConfig {

    /**
     * 创建ID生成器专用的RedisTemplate
     *
     * @param redisConnectionFactory Redis连接工厂（Spring Boot自动配置）
     * @return 配置好的RedisTemplate实例
     *
     * 【序列化说明】
     * 使用StringRedisSerializer作为默认序列化器：
     * - Redis中存储的是可读的字符串，便于调试
     * - 与Redis命令行工具查看的数据格式一致
     */
    @Bean("idGeneratorRedisTemplate")
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate redisTemplate = new RedisTemplate();
        redisTemplate.setDefaultSerializer(new StringRedisSerializer());
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }

    /**
     * 创建基于Redis的Worker ID分配器
     *
     * @param redisTemplate ID生成器专用的RedisTemplate
     * @return WorkerIdAssigner实现类实例
     *
     * 【Worker ID的作用】
     * 在分布式系统中，每台服务器需要一个唯一的Worker ID，
     * 这样生成的ID才不会和其他服务器重复。
     * 这里使用Redis的原子自增操作来分配Worker ID。
     */
    @Bean("disposableWorkerIdAssigner")
    public WorkerIdAssigner redisDisposableWorkerIdAssigner(@Qualifier("idGeneratorRedisTemplate") RedisTemplate redisTemplate){
        RedisDisposableWorkerIdAssigner redisDisposableWorkerIdAssigner = new RedisDisposableWorkerIdAssigner(redisTemplate);
        return redisDisposableWorkerIdAssigner;
    }
}
