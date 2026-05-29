package ai.knowhub.util;

import lombok.Data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/**
 * 【日期工具类】
 *
 * 作用：提供全面的日期/时间操作工具方法，包括格式化、解析、计算、区间遍历等。
 *       是项目中处理日期相关逻辑的统一工具类。
 *
 * 设计思路：
 *   - 所有方法都是静态方法，无需实例化即可使用。
 *   - 常量定义了常用的时间单位换算和格式化模式，避免硬编码。
 *   - 使用 SimpleDateFormat 进行格式化（注意：SimpleDateFormat 非线程安全，
 *     在多线程场景下应使用 DateTimeFormatter 或 ThreadLocal）。
 *
 * 注意事项：
 *   - SimpleDateFormat 不是线程安全的，高并发场景下建议使用 Java 8 的 DateTimeFormatter。
 *   - 时区处理：now() 方法默认使用 UTC+8（东八区，北京时间）。
 *
 * 使用示例：
 *   String dateStr = DateUtils.formatDate(new Date());        // "2026-05-27"
 *   Date tomorrow = DateUtils.addDay(new Date(), 1);          // 明天
 *   List<Date> week = DateUtils.getWeekDateList(new Date());  // 本周所有日期
 */
public class DateUtils {

    // ==================== 时间单位换算常量 ====================

    /** 一周的天数 */
    public static final int WEEK_DAYS = 7;

    /** 一年的月数 */
    public static final int YEAR_MONTHS = 12;

    /** 一天的小时数 */
    public static final int DAY_HOURS = 24;

    /** 一小时的分钟数 */
    public static final int HOUR_MINUTES = 60;

    /** 一天的分钟数 = 24 * 60 = 1440 */
    public static final int DAY_MINUTES = 1440;

    /** 一分钟的秒数 */
    public static final int MINUTE_SECONDS = 60;

    /** 一小时的秒数 = 60 * 60 = 3600 */
    public static final int HOUR_SECONDS = 3600;

    /** 一天的秒数 = 24 * 60 * 60 = 86400 */
    public static final int DAY_SECONDS = 86400;

    /** 一秒的毫秒数 */
    public static final long SECOND_MILLISECONDS = 1000L;

    /** 一分钟的毫秒数 = 60 * 1000 = 60000 */
    public static final long MINUTE_MILLISECONDS = 60000L;

    /** 一小时的毫秒数 = 60 * 60 * 1000 = 3600000 */
    public static final long HOUR_MILLISECONDS = 3600000L;

    /** 一天的毫秒数 = 24 * 60 * 60 * 1000 = 86400000 */
    public static final long DAY_MILLISECONDS = 86400000L;

    // ==================== 星期常量（ISO 标准，周一=1，周日=7） ====================

    /** 周一 */
    public static final int WEEK_1_MONDAY = 1;
    /** 周二 */
    public static final int WEEK_2_TUESDAY = 2;
    /** 周三 */
    public static final int WEEK_3_WEDNESDAY = 3;
    /** 周四 */
    public static final int WEEK_4_THURSDAY = 4;
    /** 周五 */
    public static final int WEEK_5_FRIDAY = 5;
    /** 周六 */
    public static final int WEEK_6_SATURDAY = 6;
    /** 周日 */
    public static final int WEEK_7_SUNDAY = 7;

    // ==================== 月份常量 ====================

    /** 一月 */
    public static final int MONTH_1_JANUARY = 1;
    /** 二月 */
    public static final int MONTH_2_FEBRUARY = 2;
    /** 三月 */
    public static final int MONTH_3_MARCH = 3;
    /** 四月 */
    public static final int MONTH_4_APRIL= 4;
    /** 五月 */
    public static final int MONTH_5_MAY = 5;
    /** 六月 */
    public static final int MONTH_6_JUNE = 6;
    /** 七月 */
    public static final int MONTH_7_JULY = 7;
    /** 八月 */
    public static final int MONTH_8_AUGUST = 8;
    /** 九月 */
    public static final int MONTH_9_SEPTEMBER = 9;
    /** 十月 */
    public static final int MONTH_10_OCTOBER = 10;
    /** 十一月 */
    public static final int MONTH_11_NOVEMBER = 11;
    /** 十二月 */
    public static final int MONTH_12_DECEMBER= 12;

    // ==================== 日期格式化模式常量 ====================

    /** 带分隔符的日期格式：yyyy-MM-dd */
    public static final String FORMAT_DATE = "yyyy-MM-dd";

    /** 带分隔符的日期+小时格式：yyyy-MM-dd HH */
    public static final String FORMAT_HOUR = "yyyy-MM-dd HH";

    /** 带分隔符的日期+时分格式：yyyy-MM-dd HH:mm */
    public static final String FORMAT_MINUTE = "yyyy-MM-dd HH:mm";

    /** 带分隔符的日期+时分秒格式：yyyy-MM-dd HH:mm:ss */
    public static final String FORMAT_SECOND = "yyyy-MM-dd HH:mm:ss";

    /** 带分隔符的完整日期时间格式（含毫秒）：yyyy-MM-dd HH:mm:ss:SSS */
    public static final String FORMAT_MILLISECOND = "yyyy-MM-dd HH:mm:ss:SSS";

    /** 无分隔符的日期格式：yyyyMMdd */
    public static final String FORMAT_NO_DATE = "yyyyMMdd";

    /** 无分隔符的日期+小时格式：yyyyMMddHH */
    public static final String FORMAT_NO_HOUR = "yyyyMMddHH";

    /** 无分隔符的日期+时分格式：yyyyMMddHHmm */
    public static final String FORMAT_NO_MINUTE = "yyyyMMddHHmm";

    /** 无分隔符的日期+时分秒格式：yyyyMMddHHmmss */
    public static final String FORMAT_NO_SECOND = "yyyyMMddHHmmss";

    /** 无分隔符的完整日期时间格式（含毫秒）：yyyyMMddHHmmssSSS */
    public static final String FORMAT_NO_MILLISECOND = "yyyyMMddHHmmssSSS";

    /** UTC 时间格式：yyyy-MM-dd'T'HH:mm:ss.SSS'Z' */
    public static final String FORMAT_UTC = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    // ==================== 获取当前时间 ====================

    /**
     * 获取当前时间（东八区北京时间），格式为 yyyy-MM-dd HH:mm:ss
     *
     * @return 当前时间的 Date 对象
     */
    public static Date now(){
        return parseDateTime(getFormatedDateString(8,FORMAT_SECOND));
    }

    /**
     * 获取当前时间（东八区），使用自定义格式
     *
     * @param format 日期格式，如 "yyyy-MM-dd"
     * @return 当前时间的 Date 对象
     */
    public static Date now(String format){
        return parseDateTime(getFormatedDateString(8,format),format);
    }

    /**
     * 获取当前时间的字符串表示，格式为 yyyy-MM-dd HH:mm:ss
     *
     * @return 当前时间字符串
     */
    public static String nowStr(){
        return getFormatedDateString(8, FORMAT_SECOND);
    }

    /**
     * 获取当前时间的字符串表示，使用自定义格式
     *
     * @param pattern 日期格式模式
     * @return 当前时间字符串
     */
    public static String nowStr(String pattern){
        return getFormatedDateString(8, pattern);
    }

    // ==================== 格式化与解析 ====================

    /**
     * 创建 SimpleDateFormat 实例（内部工具方法）
     *
     * @param formatStyle 日期格式模式
     * @return SimpleDateFormat 实例
     */
    private static SimpleDateFormat getSimpleDateFormat(String formatStyle) {
        return new SimpleDateFormat(formatStyle);
    }

    /**
     * 将 Date 对象格式化为指定格式的字符串
     *
     * @param date        要格式化的日期，为 null 时返回空字符串
     * @param formatStyle 日期格式模式
     * @return 格式化后的日期字符串
     */
    public static String format(Date date, String formatStyle) {
        if (Objects.isNull(date)) {
            return "";
        }
        return getSimpleDateFormat(formatStyle).format(date);
    }

    /**
     * 将 Date 格式化为日期字符串（yyyy-MM-dd）
     *
     * @param date 日期对象
     * @return 日期字符串
     */
    public static String formatDate(Date date) {
        return format(date, FORMAT_DATE);
    }

    /**
     * 将 Date 格式化为日期时间字符串（yyyy-MM-dd HH:mm:ss）
     *
     * @param date 日期对象
     * @return 日期时间字符串
     */
    public static String formatDateTime(Date date) {
        return format(date, FORMAT_SECOND);
    }

    /**
     * 将 Date 格式化为带毫秒的日期时间字符串（yyyy-MM-dd HH:mm:ss:SSS）
     *
     * @param date 日期对象
     * @return 带毫秒的日期时间字符串
     */
    public static String formatDateTimeStamp(Date date) {
        return format(date, FORMAT_MILLISECOND);
    }

    /**
     * 将 Date 格式化为 UTC 时间字符串
     * 使用 Asia/Shanghai 时区进行转换。
     *
     * @param date 日期对象
     * @return UTC 格式的时间字符串
     */
    public static String formatUtcTime(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_UTC);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(date);
    }

    /**
     * 将日期字符串解析为 Date 对象（yyyy-MM-dd）
     *
     * @param dateString 日期字符串
     * @return Date 对象，解析失败返回 null
     */
    public static Date parseDate(String dateString) {
        return parse(dateString, FORMAT_DATE);
    }

    /**
     * 将日期时间字符串解析为 Date 对象（yyyy-MM-dd HH:mm:ss）
     *
     * @param dateTimeStr 日期时间字符串
     * @return Date 对象，解析失败返回 null
     */
    public static Date parseDateTime(String dateTimeStr) {
        return parse(dateTimeStr, FORMAT_SECOND);
    }

    /**
     * 将日期时间字符串解析为 Date 对象（自定义格式）
     *
     * @param dateTimeStr 日期时间字符串
     * @param format       日期格式模式
     * @return Date 对象，解析失败返回 null
     */
    public static Date parseDateTime(String dateTimeStr,String format) {
        return parse(dateTimeStr, format);
    }

    /**
     * 将带毫秒的日期时间字符串解析为 Date 对象
     *
     * @param dateTimeStampStr 带毫秒的日期时间字符串
     * @return Date 对象，解析失败返回 null
     */
    public static Date parseDateTimeStamp(String dateTimeStampStr) {
        return parse(dateTimeStampStr, FORMAT_MILLISECOND);
    }

    /**
     * 将毫秒时间戳解析为 Date 对象
     *
     * @param timestamp 毫秒时间戳
     * @return Date 对象
     */
    public static Date parse(Long timestamp) {
        return new Date(timestamp);
    }

    /**
     * 将字符串按指定格式解析为 Date 对象
     *
     * @param dateString  日期字符串
     * @param formatStyle 日期格式模式
     * @return Date 对象，字符串为空或解析失败返回 null
     */
    public static Date parse(String dateString, String formatStyle) {
        String s = getString(dateString);
        if (s.isEmpty()) {
            return null;
        }
        try {
            return getSimpleDateFormat(formatStyle).parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 安全地获取字符串（null 转为空字符串并 trim）
     *
     * @param s 输入字符串
     * @return 处理后的字符串，null 返回 ""
     */
    private static String getString(String s) {
        return Objects.isNull(s) ? "" : s.trim();
    }

    // ==================== 日期边界 ====================

    /**
     * 获取指定日期的开始时间（当天 00:00:00.000）
     *
     * @param date 日期对象
     * @return 当天的开始时间，date 为 null 时返回 null
     */
    public static Date getDateStart(Date date) {
        if (Objects.isNull(date)) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 获取指定日期的结束时间（当天 23:59:59.999）
     *
     * @param date 日期对象
     * @return 当天的结束时间，date 为 null 时返回 null
     */
    public static Date getDateEnd(Date date) {
        if (Objects.isNull(date)) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    // ==================== 日期编号 ====================

    /**
     * 获取日期的数字编号（yyyyMMdd 格式）
     * 例如 2026-05-27 -> 20260527
     *
     * @param date 日期对象
     * @return 日期数字编号，date 为 null 时返回 0
     */
    public static int getDateNo(Date date) {
        if (Objects.isNull(date)) {
            return 0;
        }
        return Integer.valueOf(format(date, FORMAT_NO_DATE));
    }

    /**
     * 获取日期时间的数字编号（yyyyMMddHHmmss 格式）
     *
     * @param date 日期对象
     * @return 日期时间数字编号，date 为 null 时返回 0
     */
    public static long getDateTimeNo(Date date) {
        if (Objects.isNull(date)) {
            return 0L;
        }
        return Long.parseLong(format(date, FORMAT_NO_SECOND));
    }

    /**
     * 获取带毫秒的日期时间数字编号（yyyyMMddHHmmssSSS 格式）
     *
     * @param date 日期对象
     * @return 带毫秒的日期时间数字编号，date 为 null 时返回 0
     */
    public static long getDateTimeStampNo(Date date) {
        if (Objects.isNull(date)) {
            return 0L;
        }
        return Long.parseLong(format(date, FORMAT_NO_MILLISECOND));
    }

    // ==================== 星期相关 ====================

    /**
     * 获取日期对应的星期几（数字，周一=1，周日=7）
     *
     * @param date 日期对象
     * @return 星期几的数字，date 为 null 时返回 0
     */
    public static int getWeek(Date date) {
        if (Objects.isNull(date)) {
            return 0;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return getWeek(calendar);
    }

    /**
     * 获取日期对应的星期几（中文，如"周一""周日"）
     *
     * @param date 日期对象
     * @return 星期几的中文描述，date 为 null 时返回 "未知"
     */
    public static String getWeekStr(Date date) {
        if (Objects.isNull(date)) {
            return "未知";
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return getWeekStr(calendar);
    }

    /**
     * 从 Calendar 对象获取星期几（数字）
     * 注意：Calendar 的 DAY_OF_WEEK 中，周日=1，周六=7，需要转换为 ISO 标准（周一=1，周日=7）。
     *
     * @param calendar Calendar 对象
     * @return ISO 标准的星期数字
     */
    private static int getWeek(Calendar calendar) {
        switch (calendar.get(Calendar.DAY_OF_WEEK)) {
        case Calendar.MONDAY:
            return 1;
        case Calendar.TUESDAY:
            return 2;
        case Calendar.WEDNESDAY:
            return 3;
        case Calendar.THURSDAY:
            return 4;
        case Calendar.FRIDAY:
            return 5;
        case Calendar.SATURDAY:
            return 6;
        case Calendar.SUNDAY:
            return 7;
        default:
            return 0;
        }
    }

    /**
     * 从 Calendar 对象获取星期几（中文）
     *
     * @param calendar Calendar 对象
     * @return 星期几的中文描述
     */
    private static String getWeekStr(Calendar calendar) {
        switch (calendar.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                return "周一";
            case Calendar.TUESDAY:
                return "周二";
            case Calendar.WEDNESDAY:
                return "周三";
            case Calendar.THURSDAY:
                return "周四";
            case Calendar.FRIDAY:
                return "周五";
            case Calendar.SATURDAY:
                return "周六";
            case Calendar.SUNDAY:
                return "周日";
            default:
                return "未知";
        }
    }

    /**
     * 获取日期在当年的第几周
     *
     * @param date 日期对象
     * @return 年内周数，date 为 null 时返回 -1
     */
    public static int getWeekOfYear(Date date) {
        if (Objects.isNull(date)) {
            return -1;
        }
        int weeks = getWeekOfYearIgnoreLastYear(date);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int week = getWeek(calendar);
        if (week == 1) {
            return weeks;
        }
        return weeks - 1;
    }

    /**
     * 获取日期在当年的第几周（忽略跨年问题，简单按天数除以 7 计算）
     *
     * @param date 日期对象
     * @return 年内周数，date 为 null 时返回 -1
     */
    public static int getWeekOfYearIgnoreLastYear(Date date) {
        int seven = 7;
        if (Objects.isNull(date)) {
            return -1;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int days = calendar.get(Calendar.DAY_OF_YEAR);
        int weeks = days / seven;

        if (days % seven == 0) {
            return weeks;
        }

        return weeks + 1;
    }

    // ==================== 日期节点 ====================

    /**
     * 将 Date 对象转换为 DateNode 结构化对象
     * DateNode 包含年、月、日、时、分、秒、毫秒、星期、年内天数、年内周数等完整信息。
     *
     * @param date 日期对象
     * @return DateNode 结构化对象，date 为 null 时返回 null
     */
    public static DateNode getDateNode(Date date) {
        if (Objects.isNull(date)) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        DateNode node = new DateNode();
        node.setTime(format(date, FORMAT_MILLISECOND));
        node.setYear(calendar.get(Calendar.YEAR));
        node.setMonth(calendar.get(Calendar.MONTH) + 1);  // Calendar.MONTH 从 0 开始，需要 +1
        node.setDay(calendar.get(Calendar.DAY_OF_MONTH));
        node.setHour(calendar.get(Calendar.HOUR_OF_DAY));
        node.setMinute(calendar.get(Calendar.MINUTE));
        node.setSecond(calendar.get(Calendar.SECOND));
        node.setMillisecond(calendar.get(Calendar.MILLISECOND));
        node.setWeek(getWeek(calendar));
        node.setDayOfYear(calendar.get(Calendar.DAY_OF_YEAR));
        node.setWeekOfYear(getWeekOfYear(date));
        node.setWeekOfYearIgnoreLastYear(getWeekOfYearIgnoreLastYear(date));
        node.setMillisecondStamp(date.getTime());
        node.setSecondStamp(node.getMillisecondStamp() / 1000);
        return node;
    }

    // ==================== 日期加减运算 ====================

    /**
     * 对日期进行加减运算的通用方法
     *
     * @param date   原始日期
     * @param field  Calendar 字段常量（如 Calendar.YEAR、Calendar.DAY_OF_YEAR）
     * @param amount 加减的数量（正数为加，负数为减）
     * @return 运算后的新日期，date 为 null 时返回 null
     */
    public static Date add(Date date, int field, int amount) {
        if (Objects.isNull(date)) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(field, amount);
        return calendar.getTime();
    }

    /**
     * 在日期上加减年数
     *
     * @param date 原始日期
     * @param year 加减的年数（正数加，负数减）
     * @return 运算后的新日期
     */
    public static Date addYear(Date date, int year) {
        return add(date, Calendar.YEAR, year);
    }

    /**
     * 在日期上加减月数
     *
     * @param date  原始日期
     * @param month 加减的月数
     * @return 运算后的新日期
     */
    public static Date addMonth(Date date, int month) {
        return add(date, Calendar.MONTH, month);
    }

    /**
     * 在日期上加减天数
     *
     * @param date 原始日期
     * @param day  加减的天数
     * @return 运算后的新日期
     */
    public static Date addDay(Date date, int day) {
        return add(date, Calendar.DAY_OF_YEAR, day);
    }

    /**
     * 在日期上加减周数
     *
     * @param date 原始日期
     * @param week 加减的周数
     * @return 运算后的新日期
     */
    public static Date addWeek(Date date, int week) {
        return add(date, Calendar.WEEK_OF_YEAR, week);
    }

    /**
     * 在日期上加减小时数
     *
     * @param date 原始日期
     * @param hour 加减的小时数
     * @return 运算后的新日期
     */
    public static Date addHour(Date date, int hour) {
        return add(date, Calendar.HOUR_OF_DAY, hour);
    }

    /**
     * 在日期上加减分钟数
     *
     * @param date   原始日期
     * @param minute 加减的分钟数
     * @return 运算后的新日期
     */
    public static Date addMinute(Date date, int minute) {
        return add(date, Calendar.MINUTE, minute);
    }

    /**
     * 在日期上加减秒数
     *
     * @param date   原始日期
     * @param second 加减的秒数
     * @return 运算后的新日期
     */
    public static Date addSecond(Date date, int second) {
        return add(date, Calendar.SECOND, second);
    }

    /**
     * 在日期上加减毫秒数
     *
     * @param date        原始日期
     * @param millisecond 加减的毫秒数
     * @return 运算后的新日期
     */
    public static Date addMillisecond(Date date, int millisecond) {
        return add(date, Calendar.MILLISECOND, millisecond);
    }

    // ==================== 周和月的日期列表 ====================

    /**
     * 获取指定日期所在周中某一天的日期
     *
     * @param date  基准日期
     * @param index 星期几（1=周一，7=周日）
     * @return 对应星期几的日期，index 超出范围返回 null
     */
    public static Date getWeekDate(Date date, int index) {
        if (index < WEEK_1_MONDAY || index > WEEK_7_SUNDAY) {
            return null;
        }
        int week = getWeek(date);
        return addDay(date, index - week);
    }

    /**
     * 获取指定日期所在周的周一（开始日期）
     *
     * @param date 基准日期
     * @return 周一的日期（00:00:00）
     */
    public static Date getWeekDateStart(Date date) {
        return getDateStart(getWeekDate(date, WEEK_1_MONDAY));
    }

    /**
     * 获取指定日期所在周的周日（结束日期）
     *
     * @param date 基准日期
     * @return 周日的日期（23:59:59）
     */
    public static Date getWeekDateEnd(Date date) {
        return getWeekDateEnd(getWeekDate(date, WEEK_7_SUNDAY));
    }

    /**
     * 获取指定日期所在周的所有日期列表（周一到周日）
     *
     * @param date 基准日期
     * @return 包含 7 个 Date 的列表，date 为 null 时返回空列表
     */
    public static List<Date> getWeekDateList(Date date) {
        if (Objects.isNull(date)) {
            return Collections.emptyList();
        }

        Date weekFromDate = getWeekDateStart(date);

        Date weekeEndDate = getWeekDateEnd(date);
        return getBetweenDateList(weekFromDate, weekeEndDate, true);
    }

    /**
     * 获取指定日期字符串所在周的所有日期字符串列表
     *
     * @param dateString 日期字符串（yyyy-MM-dd）
     * @return 日期字符串列表
     */
    public static List<String> getWeekDateList(String dateString) {
        Date date = parseDate(dateString);
        if (Objects.isNull(date)) {
            return Collections.emptyList();
        }
        return getDateStrList(getWeekDateList(date));
    }

    /**
     * 获取指定日期所在月的所有日期列表
     *
     * @param date 基准日期
     * @return 当月所有日期的列表，date 为 null 时返回空列表
     */
    public static List<Date> getMonthDateList(Date date) {
        if (Objects.isNull(date)) {
            return Collections.emptyList();
        }
        Date monthDateStart = getMonthDateStart(date);
        Date monthDateEnd = getMonthDateEnd(date);
        return getBetweenDateList(monthDateStart, monthDateEnd, true);
    }

    /**
     * 获取指定日期字符串所在月的所有日期字符串列表
     *
     * @param dateString 日期字符串（yyyy-MM-dd）
     * @return 日期字符串列表
     */
    public static List<String> getMonthDateList(String dateString) {
        Date date = parseDate(dateString);
        if (Objects.isNull(date)) {
            return Collections.emptyList();
        }
        return getDateStrList(getMonthDateList(date));
    }

    /**
     * 获取指定日期所在月的第一天（开始时间）
     *
     * @param date 基准日期
     * @return 当月第一天的 00:00:00，date 为 null 时返回 null
     */
    public static Date getMonthDateStart(Date date) {
        if (Objects.isNull(date)) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return getDateStart(calendar.getTime());
    }

    /**
     * 获取指定日期所在月的最后一天（结束时间）
     *
     * @param date 基准日期
     * @return 当月最后一天的 23:59:59，date 为 null 时返回 null
     */
    public static Date getMonthDateEnd(Date date) {
        if (Objects.isNull(date)) {
            return null;
        }
        Date monthDateStart = getMonthDateStart(date);
        Date nextMonthDateStart = getMonthDateStart(addMonth(monthDateStart, 1));
        return getDateEnd(addDay(nextMonthDateStart, -1));
    }

    // ==================== 日期差值计算 ====================

    /**
     * 计算两个日期之间的秒数差（取绝对值）
     *
     * @param date1 日期1
     * @param date2 日期2
     * @return 两个日期之间的秒数差，任一日期为 null 时返回 -1
     */
    public static long countBetweenSecond(Date date1, Date date2) {
        if (Objects.isNull(date1) || Objects.isNull(date2)) {
            return -1;
        }

        long diffInMilliseconds = Math.abs(date2.getTime() - date1.getTime());
        return diffInMilliseconds / 1000;
    }

    /**
     * 获取两个日期之间的所有日期列表
     *
     * @param date1           日期1
     * @param date2           日期2
     * @param isContainParams 是否包含起止日期本身
     * @return 日期列表（按时间顺序排列）
     */
    public static List<Date> getBetweenDateList(Date date1, Date date2, boolean isContainParams) {
        if (Objects.isNull(date1) || Objects.isNull(date2)) {
            return Collections.emptyList();
        }

        // 确保 fromDate 早于 toDate
        Date fromDate = date1;
        Date toDate = date2;
        if (date2.before(date1)) {
            fromDate = date2;
            toDate = date1;
        }

        // 取日期的开始时间（00:00:00）进行比较
        Date from = getDateStart(fromDate);
        Date to = getDateStart(toDate);

        List<Date> dates = new ArrayList<Date>();
        if (isContainParams) {
            dates.add(from);
        }
        Date date = from;
        boolean isBefore = true;
        while (isBefore) {
            date = addDay(date, 1);
            isBefore = date.before(to);
            if (isBefore) {
                dates.add(getDateStart(date));
            }
        }
        if (isContainParams) {
            dates.add(to);
        }
        return dates;
    }

    /**
     * 获取两个日期字符串之间的所有日期字符串列表（不包含起止日期）
     *
     * @param dateString1 开始日期字符串
     * @param dateString2 结束日期字符串
     * @return 日期字符串列表
     */
    public static List<String> getBetweenDateList(String dateString1, String dateString2) {
        return getBetweenDateList(dateString1, dateString2, false);
    }

    /**
     * 获取两个日期字符串之间的所有日期字符串列表
     *
     * @param dateString1    开始日期字符串
     * @param dateString2    结束日期字符串
     * @param isContainParams 是否包含起止日期
     * @return 日期字符串列表
     */
    public static List<String> getBetweenDateList(String dateString1, String dateString2, boolean isContainParams) {
        Date date1 = parseDate(dateString1);
        Date date2 = parseDate(dateString2);
        List<Date> dates = getBetweenDateList(date1, date2, isContainParams);
        return getDateStrList(dates);
    }

    /**
     * 将 Date 列表转换为日期字符串列表
     *
     * @param dates Date 列表
     * @return 日期字符串列表（yyyy-MM-dd 格式）
     */
    public static List<String> getDateStrList(List<Date> dates) {
        if (dates.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> dateList = new ArrayList<String>();
        for (Date date : dates) {
            dateList.add(formatDate(date));
        }
        return dateList;
    }

    // ==================== 时区相关 ====================

    /**
     * 获取指定时区偏移量的当前时间格式化字符串
     *
     * @param timeZoneOffset 时区偏移量（小时），如 8 表示东八区，-5 表示西五区
     * @param pattern        日期格式模式
     * @return 格式化后的当前时间字符串
     */
    public static String getFormatedDateString(float timeZoneOffset, String pattern) {
        int thirteen = 13;
        int minusTwelve = -12;
        // 校验时区偏移量范围（-12 到 +13）
        if (timeZoneOffset > thirteen || timeZoneOffset < minusTwelve) {
            timeZoneOffset = 0;
        }

        // 将小时偏移量转换为毫秒
        int newTime = (int) (timeZoneOffset * 60 * 60 * 1000);
        TimeZone timeZone;
        String[] ids = TimeZone.getAvailableIDs(newTime);
        if (ids.length == 0) {
            timeZone = TimeZone.getDefault();
        } else {
            timeZone = new SimpleTimeZone(newTime, ids[0]);
        }

        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(timeZone);
        return sdf.format(new Date());
    }

    /**
     * 【日期节点内部类】
     * 将日期拆分为各个组成部分的结构化对象，便于单独获取年、月、日等信息。
     * 使用 Lombok @Data 自动生成 getter/setter 方法。
     */
    @Data
    static class DateNode {

        /** 年份 */
        private int year;

        /** 月份（1-12） */
        private int month;

        /** 日（1-31） */
        private int day;

        /** 小时（0-23） */
        private int hour;

        /** 分钟（0-59） */
        private int minute;

        /** 秒（0-59） */
        private int second;

        /** 毫秒（0-999） */
        private int millisecond;

        /** 星期几（1=周一，7=周日） */
        private int week;

        /** 年内第几天 */
        private int dayOfYear;

        /** 年内第几周 */
        private int weekOfYear;

        /** 年内第几周（忽略跨年） */
        private int weekOfYearIgnoreLastYear;

        /** 秒级时间戳 */
        private long secondStamp;

        /** 毫秒级时间戳 */
        private long millisecondStamp;

        /** 格式化的时间字符串（yyyy-MM-dd HH:mm:ss:SSS） */
        private String time;

    }

    /**
     * 将字符串按指定模式解析为 Date 对象
     *
     * @param dateStr 日期字符串
     * @param pattern 日期格式模式
     * @return Date 对象，任一参数为 null 时返回 null
     */
    public static Date getDate(String dateStr, String pattern) {
        return getDate(dateStr, pattern, null);
    }

    /**
     * 将字符串按指定模式解析为 Date 对象（带默认值）
     *
     * @param dateStr     日期字符串
     * @param pattern     日期格式模式
     * @param defaultDate 默认日期（解析失败时返回）
     * @return Date 对象，解析失败时返回 defaultDate
     * @throws IllegalArgumentException 如果解析失败且 defaultDate 为 null
     */
    public static Date getDate(String dateStr, String pattern, Date defaultDate) {
        if (dateStr != null && pattern != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                return sdf.parse(dateStr);
            } catch (ParseException e) {
                throw new IllegalArgumentException("字符串转化为日期失败！", e);
            }
        }
        return defaultDate;
    }
}
