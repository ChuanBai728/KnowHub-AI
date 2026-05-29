package ai.knowhub.core;

/**
 * 全局常量类
 *
 * 【作用】：定义整个 Redisson 框架中通用的常量。
 *          目前主要用于锁名称拼接时的分隔符。
 */
public class Constants {

    /**
     * 锁名称各部分之间的分隔符
     * 【示例】：lockPrefix:lockName:key1:key2
     * 【说明】：使用冒号 ":" 作为分隔符是 Redis 社区的惯例（类似于 Redis 的 key 命名规范）。
     */
    public static final String SEPARATOR = ":";
}
