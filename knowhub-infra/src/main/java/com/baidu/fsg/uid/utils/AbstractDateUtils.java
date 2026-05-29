package com.baidu.fsg.uid.utils;

import org.apache.commons.lang.time.DateFormatUtils;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期工具类 - 日期时间的格式化和解析
 *
 * 【类的作用】
 * 提供日期时间的格式化和解析功能，是Apache Commons Lang DateUtils的扩展。
 * 在UID生成器中主要用于：
 * 1. 将基准时间字符串解析为Date对象
 * 2. 将ID中提取的时间戳格式化为可读的日期时间字符串
 *
 * 【设计模式】
 * 使用了"模板方法"模式：
 * - 继承Apache Commons Lang的DateUtils
 * - 添加项目特有的日期格式化方法
 *
 * 【日期格式说明】
 * - DAY_PATTERN: "yyyy-MM-dd" - 只有日期，没有时间
 * - DATETIME_PATTERN: "yyyy-MM-dd HH:mm:ss" - 日期和时间，精确到秒
 * - DATETIME_MS_PATTERN: "yyyy-MM-dd HH:mm:ss.SSS" - 日期和时间，精确到毫秒
 */
public abstract class AbstractDateUtils extends org.apache.commons.lang.time.DateUtils {

    /** 日期格式：年-月-日，例如：2024-05-20 */
    public static final String DAY_PATTERN = "yyyy-MM-dd";

    /** 日期时间格式：年-月-日 时:分:秒，例如：2024-05-20 10:30:45 */
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 日期时间格式（含毫秒）：年-月-日 时:分:秒.毫秒，例如：2024-05-20 10:30:45.123 */
    public static final String DATETIME_MS_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    /** 默认日期：1970-01-01（Unix纪元） */
    public static final Date DEFAULT_DATE = AbstractDateUtils.parseByDayPattern("1970-01-01");

    /**
     * 按日期格式解析字符串
     *
     * @param str 日期字符串，例如："2024-05-20"
     * @return 解析后的Date对象
     * @throws RuntimeException 当解析失败时抛出
     */
    public static Date parseByDayPattern(String str) {
        return parseDate(str, DAY_PATTERN);
    }

    /**
     * 按日期时间格式解析字符串
     *
     * @param str 日期时间字符串，例如："2024-05-20 10:30:45"
     * @return 解析后的Date对象
     * @throws RuntimeException 当解析失败时抛出
     */
    public static Date parseByDateTimePattern(String str) {
        return parseDate(str, DATETIME_PATTERN);
    }

    /**
     * 按指定格式解析日期字符串
     *
     * @param str     日期字符串
     * @param pattern 日期格式，例如："yyyy-MM-dd"
     * @return 解析后的Date对象
     * @throws RuntimeException 当解析失败时抛出
     */
    public static Date parseDate(String str, String pattern) {
        try {
            return parseDate(str, new String[]{pattern});
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 按指定格式格式化日期
     *
     * @param date    要格式化的日期对象
     * @param pattern 日期格式
     * @return 格式化后的字符串
     */
    public static String formatDate(Date date, String pattern) {
        return DateFormatUtils.format(date, pattern);
    }

    /**
     * 按日期格式格式化
     *
     * @param date 要格式化的日期对象
     * @return 格式化后的字符串，例如："2024-05-20"，如果date为null则返回null
     */
    public static String formatByDayPattern(Date date) {
        if (date != null) {
            return DateFormatUtils.format(date, DAY_PATTERN);
        } else {
            return null;
        }
    }

    /**
     * 按日期时间格式格式化
     *
     * @param date 要格式化的日期对象
     * @return 格式化后的字符串，例如："2024-05-20 10:30:45"
     */
    public static String formatByDateTimePattern(Date date) {
        return DateFormatUtils.format(date, DATETIME_PATTERN);
    }

    /**
     * 获取当前日期的字符串表示
     *
     * @return 当前日期的字符串，格式：yyyy-MM-dd
     */
    public static String getCurrentDayByDayPattern() {
        Calendar cal = Calendar.getInstance();
        return formatByDayPattern(cal.getTime());
    }

}
