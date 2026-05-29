package com.baidu.fsg.uid.worker;

/**
 * Worker ID分配器接口 - 为分布式节点分配唯一标识
 *
 * 【接口的作用】
 * 这个接口定义了"如何为当前实例分配Worker ID"的策略。
 * Worker ID是百度 UID 算法中标识"哪台服务器生成的ID"的关键部分。
 *
 * 【Worker ID是什么？】
 * 在64位的唯一ID中，有一部分位用于存储Worker ID：
 * ┌──────┬──────────┬──────────────┬──────────┐
 * │ 符号 │ 时间戳   │  Worker ID   │ 序列号   │
 * │ 1位  │ N位      │   M位        │ K位      │
 * └──────┴──────────┴──────────────┴──────────┘
 * 不同的服务器必须有不同的Worker ID，否则在同一时间戳和序列号下会生成重复的ID。
 *
 * 【实现类】
 * RedisDisposableWorkerIdAssigner：
 * - 使用Redis的原子自增操作分配Worker ID
 * - 每次应用启动都会获得新的ID
 * - 简单可靠，适合大多数场景
 *
 * 【设计模式】
 * 使用了"策略模式"（Strategy Pattern）：
 * - 定义了分配Worker ID的策略接口
 * - 可以有不同的实现方式（Redis、数据库、配置文件等）
 * - 调用方不需要关心具体的分配方式
 *
 * 【其他可能的实现方式】
 * 1. 基于数据库：使用数据库自增ID
 * 2. 基于配置文件：手动配置每个实例的ID
 * 3. 基于IP/MAC地址：根据网络地址计算ID
 * 4. 基于ZooKeeper：使用临时顺序节点
 */
public interface WorkerIdAssigner {

    /**
     * 分配Worker ID
     *
     * @return 唯一的Worker ID（通常从1开始递增）
     *
     * 【注意事项】
     * - 返回值必须在Worker ID位数允许的范围内
     * - 例如Worker ID占22位，则最大值为 2^22 - 1 = 4194303
     * - 不同的实例必须返回不同的值
     */
    long assignWorkerId();

}
