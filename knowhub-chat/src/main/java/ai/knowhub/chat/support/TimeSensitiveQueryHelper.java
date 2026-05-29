package ai.knowhub.chat.support;

import cn.hutool.core.util.StrUtil;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 【时间敏感查询辅助工具类】
 *
 * 作用：判断用户问题是否涉及时间敏感信息，并进行相应的查询优化。
 * 时间敏感查询是指那些回答需要依赖当前日期/时间的问题，
 * 如"今天天气怎么样"、"最新的股价是多少"等。
 *
 * 所属架构位置：属于查询改写（Query Rewriting）模块的支持层。
 * 在 RAG 检索前，系统需要判断问题是否需要时效性信息，
 * 以决定是否使用网络搜索而非知识库检索，以及是否需要在查询中加入日期锚定。
 *
 * 设计模式说明：
 * 1. 「工具类模式（Utility Class）」—— 封装时间敏感性判断逻辑。
 * 2. 「不可实例化」—— 私有构造函数，所有方法都是静态的。
 * 3. 「关键词匹配模式」—— 使用预定义的关键词列表进行模式匹配。
 *
 * 关键概念说明：
 * - 日期锚定（Date Anchoring）：在搜索查询中加入当前日期，
 *   如将 "今天天气" 改写为 "今天天气 2026年5月27日"，确保搜索结果的时效性。
 * - 新鲜搜索（Fresh Search）：优先使用网络搜索获取最新信息，
 *   而非从知识库中检索可能过时的信息。
 *
 * @author knowhub
 */
public final class TimeSensitiveQueryHelper {

    /**
     * 显式日期模式正则表达式
     * 匹配用户问题中明确写出的日期，如 "2026-05-27"、"2026年5月27日"、"5月27日" 等。
     */
    private static final Pattern EXPLICIT_DATE_PATTERN = Pattern.compile(
        "(\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)|(\\d{1,2}月\\d{1,2}日)"
    );

    /**
     * 相对时间关键词列表
     * 这些词表示相对当前时间的时间点，需要日期锚定才能确定具体日期。
     */
    private static final List<String> RELATIVE_TIME_KEYWORDS = List.of(
        "今天", "今日", "明天", "明日", "昨天", "昨日", "后天", "前天",
        "现在", "当前", "目前", "此刻", "实时", "最新", "刚刚",
        "本周", "这周", "本月", "这个月", "今年", "本年度", "本季度",
        "周几", "星期几", "几号", "日期", "几月几号"
    );

    /**
     * 新鲜信息领域关键词列表
     * 这些词表示需要实时/最新信息的领域，通常需要网络搜索。
     */
    private static final List<String> FRESH_INFORMATION_KEYWORDS = List.of(
        "天气", "气温", "温度", "降雨", "下雨", "下雪", "空气质量", "aqi",
        "限号", "限行", "尾号限行",
        "汇率", "金价", "黄金价格", "银价", "油价",
        "股价", "行情", "大盘", "指数",
        "新闻", "头条", "热搜", "热榜",
        "路况", "拥堵",
        "票房", "排片",
        "航班", "班次", "列车", "高铁", "火车", "地铁运营",
        "比分", "赛果", "赛程", "比赛结果",
        "预警", "台风"
    );

    /**
     * 日历类关键词列表
     * 这些词表示询问日期/星期的问题，需要当前日期信息。
     */
    private static final List<String> CALENDAR_KEYWORDS = List.of(
        "周几", "星期几", "几号", "日期", "几月几号", "星期", "周"
    );

    /**
     * 历史意图关键词列表
     * 这些词表示用户想查询的是历史信息，不应使用新鲜搜索。
     */
    private static final List<String> HISTORICAL_HINTS = List.of(
        "历史", "过去", "去年", "前年", "上周", "上个月", "上月", "上一周",
        "上一月", "往年", "历年", "当时", "之前", "回顾", "曾经"
    );

    /**
     * 私有构造函数，防止实例化
     */
    private TimeSensitiveQueryHelper() {
    }

    /**
     * 判断问题是否需要日期锚定
     *
     * 当问题涉及相对时间（如"今天"、"本周"）或需要实时信息（如天气、股价）时，
     * 需要在搜索查询中加入当前日期作为锚点，以确保搜索结果的时效性。
     *
     * 特殊情况：如果问题带有历史意图（如"去年的今天"），则不需要日期锚定。
     *
     * @param query 用户问题文本
     * @return true 表示需要日期锚定
     */
    public static boolean requiresCurrentDateAnchoring(String query) {
        if (StrUtil.isBlank(query)) {
            return false;
        }
        // 历史意图 + 非相对时间 + 非日历问题 -> 不需要锚定
        if (hasHistoricalIntent(query) && !hasRelativeTimeReference(query) && !looksCalendarQuestion(query)) {
            return false;
        }
        return hasRelativeTimeReference(query)
            || looksCurrentInfoDomain(query)
            || looksCalendarQuestion(query);
    }

    /**
     * 判断问题是否需要新鲜搜索（网络搜索）
     *
     * 当问题需要最新信息（如天气、股价、新闻）时，
     * 应优先使用网络搜索而非知识库检索。
     *
     * 特殊情况：
     * - 历史意图 -> 不需要新鲜搜索
     * - 包含显式日期 -> 不需要新鲜搜索（用户已指定了时间）
     * - 日历问题 -> 不需要新鲜搜索（可直接计算）
     *
     * @param query 用户问题文本
     * @return true 表示需要新鲜搜索
     */
    public static boolean requiresFreshSearch(String query) {
        if (StrUtil.isBlank(query)) {
            return false;
        }
        if (hasHistoricalIntent(query) || containsExplicitDate(query)) {
            return false;
        }
        if (looksCalendarQuestion(query)) {
            return false;
        }

        String normalized = normalize(query);
        return looksCurrentInfoDomain(normalized)
            || containsAny(normalized, "最新", "实时", "当前", "现在", "目前", "刚刚");
    }

    /**
     * 构建有效的搜索查询（加入日期锚定）
     *
     * 如果问题需要日期锚定，将当前日期和时间提示附加到查询中。
     * 例如："今天天气" -> "今天天气 2026年5月27日 今天"
     *
     * @param query       原始用户问题
     * @param currentDate 当前日期文本，如 "2026年5月27日"
     * @return 增强后的搜索查询
     */
    public static String buildEffectiveSearchQuery(String query, String currentDate) {
        if (StrUtil.isBlank(query)) {
            return query;
        }

        String trimmedQuery = query.trim();
        if (StrUtil.isBlank(currentDate)) {
            return trimmedQuery;
        }
        // 不需要锚定的情况：已有显式日期、已包含当前日期、历史意图
        if (!requiresCurrentDateAnchoring(trimmedQuery)) {
            return trimmedQuery;
        }
        if (containsExplicitDate(trimmedQuery) || trimmedQuery.contains(currentDate) || hasHistoricalIntent(trimmedQuery)) {
            return trimmedQuery;
        }
        // 附加当前日期和时间提示
        return trimmedQuery + " " + currentDate + " " + deriveTemporalHint(trimmedQuery);
    }

    /**
     * 判断问题中是否包含显式日期
     */
    public static boolean containsExplicitDate(String query) {
        return StrUtil.isNotBlank(query) && EXPLICIT_DATE_PATTERN.matcher(query).find();
    }

    /**
     * 判断问题中是否包含相对时间引用
     */
    public static boolean hasRelativeTimeReference(String query) {
        return containsAny(normalize(query), RELATIVE_TIME_KEYWORDS);
    }

    /**
     * 判断问题是否为日历类问题（如"今天星期几"）
     */
    public static boolean looksCalendarQuestion(String query) {
        return containsAny(normalize(query), CALENDAR_KEYWORDS);
    }

    /**
     * 判断问题是否属于需要实时信息的领域
     */
    public static boolean looksCurrentInfoDomain(String query) {
        return containsAny(normalize(query), FRESH_INFORMATION_KEYWORDS);
    }

    /**
     * 判断问题是否带有历史意图
     */
    public static boolean hasHistoricalIntent(String query) {
        return containsAny(normalize(query), HISTORICAL_HINTS);
    }

    /**
     * 根据问题内容推导时间提示词
     *
     * 用于在搜索查询中附加更具体的时间上下文。
     * 例如："明天的天气" -> 提示词为 "明天"
     *
     * @param query 用户问题
     * @return 时间提示词，默认为 "今天"
     */
    private static String deriveTemporalHint(String query) {
        String normalized = normalize(query);
        if (containsAny(normalized, "明天", "明日")) {
            return "明天";
        }
        if (containsAny(normalized, "昨天", "昨日", "前天")) {
            return "昨天";
        }
        if (containsAny(normalized, "本周", "这周")) {
            return "本周";
        }
        if (containsAny(normalized, "本月", "这个月")) {
            return "本月";
        }
        if (containsAny(normalized, "今年", "本年度", "本季度")) {
            return "今年";
        }
        if (containsAny(normalized, "最新", "实时", "当前", "现在", "目前", "刚刚")) {
            return "最新";
        }
        return "今天";
    }

    /**
     * 文本标准化：去除首尾空格并转为小写
     */
    private static String normalize(String query) {
        return StrUtil.isNotBlank(query) ? query.trim().toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 判断查询中是否包含候选词列表中的任意一个
     */
    private static boolean containsAny(String query, List<String> candidates) {
        if (StrUtil.isBlank(query)) {
            return false;
        }
        for (String candidate : candidates) {
            if (query.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断查询中是否包含候选词数组中的任意一个
     */
    private static boolean containsAny(String query, String... candidates) {
        if (StrUtil.isBlank(query)) {
            return false;
        }
        for (String candidate : candidates) {
            if (query.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
