package com.baidu.fsg.uid.utils;

/**
 * 带值的枚举接口 - 为枚举提供统一的值访问方式
 *
 * 【接口的作用】
 * 这是一个泛型接口，用于为枚举类型提供统一的"获取值"方法。
 * 实现此接口的枚举可以通过value()方法获取其对应的值。
 *
 * 【使用场景】
 * 在UID生成器中，WorkerNodeType枚举实现了此接口：
 * - CONTAINER(1)：容器类型，value()返回1
 * - ACTUAL(2)：实际类型，value()返回2
 *
 * 【设计模式】
 * 使用了"接口抽象"模式：
 * - 定义统一的值访问接口
 * - 不同的枚举可以有不同的值类型（Integer、String等）
 * - 配合AbstractEnumUtils.parse()方法，实现值到枚举的反向查找
 *
 * 【泛型说明】
 * <T>：值的类型，可以是Integer、String等
 *
 * 【使用示例】
 * public enum WorkerNodeType implements ValuedEnum<Integer> {
 *     CONTAINER(1), ACTUAL(2);
 *
 *     private final Integer type;
 *
 *     private WorkerNodeType(Integer type) {
 *         this.type = type;
 *     }
 *
 *     @Override
 *     public Integer value() {
 *         return type;
 *     }
 * }
 */
public interface ValuedEnum<T> {

    /**
     * 获取枚举的值
     *
     * @return 枚举对应的值
     */
    T value();
}
