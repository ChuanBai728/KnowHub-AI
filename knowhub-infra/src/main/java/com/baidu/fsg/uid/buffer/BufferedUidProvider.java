package com.baidu.fsg.uid.buffer;

import java.util.List;

/**
 * UID提供者接口 - 为环形缓冲区批量提供UID
 *
 * 【接口的作用】
 * 这是一个函数式接口（@FunctionalInterface），用于向环形缓冲区批量填充UID。
 * 它定义了"如何批量生成UID"的策略，由CachedUidGenerator来实现具体的生成逻辑。
 *
 * 【设计模式】
 * 使用了"策略模式"（Strategy Pattern）和"函数式接口"：
 * - @FunctionalInterface 表示这个接口只有一个抽象方法
 * - 可以用Lambda表达式来实现，例如：(moment) -> generateIds(moment)
 * - 这样可以将"生成UID的逻辑"与"缓冲区填充逻辑"解耦
 *
 * 【使用场景】
 * 当环形缓冲区中的UID快要用完时，BufferPaddingExecutor会调用此接口的方法来批量生成新的UID。
 * 传入当前时间秒数，返回该秒内所有可用的UID列表。
 */
@FunctionalInterface
public interface BufferedUidProvider {

    /**
     * 批量提供UID
     *
     * @param momentInSecond 当前时间的秒数（Unix时间戳，精确到秒）
     * @return 该秒内生成的所有UID列表
     *
     * 【参数说明】
     * momentInSecond：传入一个时间点（秒），生成该时间点对应的所有UID。
     * 例如：如果序列号占13位，则每个时间点最多生成 2^13 = 8192 个UID。
     *
     * 【返回值说明】
     * 返回一个UID列表，这些UID都属于同一个时间秒。
     * 列表中的每个UID都是唯一的，通过序列号区分。
     */
    List<Long> provide(long momentInSecond);
}
