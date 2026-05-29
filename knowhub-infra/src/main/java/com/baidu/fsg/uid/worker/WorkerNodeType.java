package com.baidu.fsg.uid.worker;

import com.baidu.fsg.uid.utils.ValuedEnum;

/**
 * 工作节点类型枚举 - 标识节点的运行环境类型
 *
 * 【枚举的作用】
 * 这个枚举定义了工作节点的类型，用于标识节点运行在什么环境中。
 * 在UID生成器中，节点类型可能用于：
 * 1. 记录节点的运行环境信息
 * 2. 根据不同类型采取不同的Worker ID分配策略
 * 3. 监控和统计不同环境的节点数量
 *
 * 【枚举值说明】
 * - CONTAINER(1)：容器类型，运行在Docker等容器环境中
 * - ACTUAL(2)：实际类型，运行在物理机或虚拟机上
 *
 * 【设计模式】
 * 使用了"枚举单例"模式：
 * - 每个枚举值都是唯一的实例
 * - 实现了ValuedEnum<Integer>接口，支持值到枚举的反向查找
 *
 * 【使用示例】
 * // 根据值获取枚举
 * WorkerNodeType type = AbstractEnumUtils.parse(WorkerNodeType.class, 1);
 * // 返回 WorkerNodeType.CONTAINER
 *
 * // 获取枚举的值
 * int value = WorkerNodeType.CONTAINER.value();
 * // 返回 1
 */
public enum WorkerNodeType implements ValuedEnum<Integer> {

    /**
     * 容器类型
     * 运行在Docker、Kubernetes等容器环境中
     */
    CONTAINER(1),

    /**
     * 实际类型
     * 运行在物理机或虚拟机上
     */
    ACTUAL(2);

    /**
     * 节点类型的数值标识
     */
    private final Integer type;

    /**
     * 构造函数
     *
     * @param type 节点类型的数值标识
     */
    private WorkerNodeType(Integer type) {
        this.type = type;
    }

    /**
     * 获取节点类型的数值标识
     *
     * @return 数值标识（1或2）
     */
    @Override
    public Integer value() {
        return type;
    }

}
