package com.baidu.fsg.uid.utils;

import org.springframework.util.Assert;

/**
 * 枚举工具类 - 提供枚举值的查找和转换功能
 *
 * 【类的作用】
 * 提供通用的枚举操作方法，支持：
 * 1. 根据值查找枚举常量
 * 2. 根据名称查找枚举常量
 *
 * 【设计模式】
 * 使用了"静态工具类"模式：
 * - 所有方法都是静态的
 * - 不需要实例化
 * - 提供通用的枚举操作
 *
 * 【泛型说明】
 * <T extends ValuedEnum<V>, V>：
 * - T：枚举类型，必须实现ValuedEnum接口
 * - V：值的类型（如Integer、String等）
 *
 * 【使用示例】
 * WorkerNodeType nodeType = AbstractEnumUtils.parse(WorkerNodeType.class, 1);
 * // 返回 WorkerNodeType.CONTAINER
 */
public abstract class AbstractEnumUtils {

    /**
     * 根据值查找枚举常量
     *
     * @param clz   枚举类的Class对象
     * @param value 要查找的值
     * @param <T>   枚举类型（必须实现ValuedEnum接口）
     * @param <V>   值的类型
     * @return 匹配的枚举常量，如果没有匹配则返回null
     *
     * 【使用示例】
     * // WorkerNodeType实现ValuedEnum<Integer>
     * // CONTAINER(1), ACTUAL(2)
     * WorkerNodeType type = AbstractEnumUtils.parse(WorkerNodeType.class, 1);
     * // 返回 WorkerNodeType.CONTAINER
     */
    public static <T extends ValuedEnum<V>, V> T parse(Class<T> clz, V value) {
        Assert.notNull(clz, "clz can not be null");
        if (value == null) {
            return null;
        }

        // 遍历所有枚举常量，找到值匹配的
        for (T t : clz.getEnumConstants()) {
            if (value.equals(t.value())) {
                return t;
            }
        }
        return null;
    }

    /**
     * 根据名称查找枚举常量
     *
     * @param enumType 枚举类的Class对象
     * @param name     枚举常量的名称（如"CONTAINER"）
     * @param <T>      枚举类型
     * @return 匹配的枚举常量，如果name为null则返回null
     *
     * 【使用示例】
     * WorkerNodeType type = AbstractEnumUtils.valueOf(WorkerNodeType.class, "CONTAINER");
     * // 返回 WorkerNodeType.CONTAINER
     */
    public static <T extends Enum<T>> T valueOf(Class<T> enumType, String name) {
        if (name == null) {
            return null;
        }

        return Enum.valueOf(enumType, name);
    }

}
