package ai.knowhub.document.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档行分类器（Document Line Classifier）
 *
 * 【类的作用】
 * 对文档中的每一行文本进行分类，判断其属于标题（HEADING）、列表项（LIST_ITEM）
 * 还是正文（BODY）。这是文档结构分析的基础步骤。
 *
 * 【在架构中的角色】
 * 属于文档结构解析流程的第一层——行级分类。后续的 DocumentStructureSignalExtractor
 * 会使用本分类器作为兜底（fallback）机制，当更复杂的信号提取规则无法匹配时，
 * 回退到本分类器的结果。
 *
 * 【支持的标题格式】
 * - Markdown 标题：# ~ ######
 * - 多级数字标题：1.1、2.3.1 等
 * - 中文章节标题：第一章、第二章 等
 * - 中文大纲序号：一、二、三 等
 * - 附录标记：附录A、附录一 等
 * - 明确步骤：第1步、步骤三 等
 *
 * 【设计模式】
 * 使用策略模式的思想，通过正则表达式链式匹配来分类文本行。
 * 内部使用 record 类型 LineClassification 作为不可变的分类结果。
 */
@Component
public class DocumentLineClassifier {

    /**
     * Markdown 标题正则
     * 匹配 1-6 个 # 号开头的 Markdown 标题，如 "# 标题"、"### 三级标题"
     * group(1) = # 号（用于确定标题层级），group(2) = 标题文本
     */
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    /**
     * 多级数字标题正则
     * 匹配如 "1.1 标题"、"2.3.1 内容" 这样的多级数字编号
     * group(1) = 数字编号（如 "2.3.1"），group(2) = 标题文本
     */
    private static final Pattern MULTI_LEVEL_DIGIT_HEADING_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)+)\\s*[、.]?\\s*(.+)$");

    /**
     * 单级数字行正则
     * 匹配如 "1、内容"、"2. 内容" 这样的单级数字编号
     * group(1) = 数字，group(2) = 内容文本
     */
    private static final Pattern SINGLE_LEVEL_DIGIT_LINE_PATTERN = Pattern.compile("^(\\d+)\\s*[、.]\\s*(.+)$");

    /**
     * 中文章节正则
     * 匹配如 "第一章 概述"、"第2节 背景" 这样的中文章节标记
     * group(1) = 章节标记，group(2) = 标题文本
     */
    private static final Pattern CHINESE_CHAPTER_PATTERN = Pattern.compile("^(第[一二三四五六七八九十百\\d]+[章节条部分])\\s*(.+)$");

    /**
     * 中文大纲序号正则
     * 匹配如 "一、概述"、"三、总结" 这样的中文数字大纲序号
     * group(1) = 中文数字，group(2) = 内容文本
     */
    private static final Pattern CHINESE_OUTLINE_PATTERN = Pattern.compile("^([一二三四五六七八九十百]+)[、.]\\s*(.+)$");

    /**
     * 附录标记正则
     * 匹配如 "附录A"、"附录一"、"附录 1" 这样的附录标记
     * group(1) = 附录标记，group(2) = 附录标题（可选）
     */
    private static final Pattern APPENDIX_PATTERN = Pattern.compile("^(附录\\s*[A-Za-z一二三四五六七八九十百\\d]+)(?:\\s+(.+))?$");

    /**
     * 明确步骤正则
     * 匹配如 "第1步：执行操作"、"步骤三、检查结果" 这样的步骤标记
     * group(1) 或 group(2) = 步骤编号，group(3) = 步骤内容
     */
    private static final Pattern EXPLICIT_STEP_PATTERN = Pattern.compile("^(?:第\\s*([0-9一二三四五六七八九十百]+)\\s*步|步骤\\s*([0-9一二三四五六七八九十百]+))\\s*[:：、.]?\\s*(.+)$");

    /**
     * 对一行文本进行分类
     *
     * 【处理流程】
     * 1. 先对文本进行空白标准化处理
     * 2. 按优先级依次匹配各种标题模式
     * 3. 匹配列表项模式
     * 4. 如果都不匹配，归类为正文（BODY）
     *
     * @param line 待分类的文本行
     * @return 分类结果 LineClassification，包含类型、层级、标题和原始文本
     */
    public LineClassification classify(String line) {
        String normalized = safeText(line);
        if (normalized.isBlank()) {
            return new LineClassification(LineKind.BODY, 0, normalized, normalized);
        }

        // 优先匹配 Markdown 标题（# ~ ######）
        Matcher markdownMatcher = MARKDOWN_HEADING_PATTERN.matcher(normalized);
        if (markdownMatcher.matches()) {
            int level = markdownMatcher.group(1).length(); // # 号数量即为层级
            return heading(level, markdownMatcher.group(2).trim(), normalized);
        }

        // 匹配附录标记（附录A、附录一 等）
        Matcher appendixMatcher = APPENDIX_PATTERN.matcher(normalized);
        if (appendixMatcher.matches()) {
            return heading(1, normalized, normalized); // 附录视为一级标题
        }

        // 匹配明确步骤（第1步、步骤三 等）→ 列表项
        Matcher explicitStepMatcher = EXPLICIT_STEP_PATTERN.matcher(normalized);
        if (explicitStepMatcher.matches()) {
            return listItem(normalized);
        }

        // 匹配中文章节标题（第一章、第2节 等）
        Matcher chapterMatcher = CHINESE_CHAPTER_PATTERN.matcher(normalized);
        if (chapterMatcher.matches()) {
            return heading(2, normalized, normalized); // 中文章节视为二级标题
        }

        // 匹配多级数字标题（1.1、2.3.1 等）
        Matcher multiLevelDigitMatcher = MULTI_LEVEL_DIGIT_HEADING_PATTERN.matcher(normalized);
        if (multiLevelDigitMatcher.matches()) {
            String prefix = multiLevelDigitMatcher.group(1);
            return heading(prefix.split("\\.").length, normalized, normalized); // 层级 = 点号数量
        }

        // 匹配中文大纲序号（一、二、三 等）
        // 需要根据内容判断是标题还是列表项
        Matcher chineseOutlineMatcher = CHINESE_OUTLINE_PATTERN.matcher(normalized);
        if (chineseOutlineMatcher.matches()) {
            String content = chineseOutlineMatcher.group(2).trim();
            if (looksLikeHeadingContent(content)) {
                return heading(1, normalized, normalized); // 内容像标题 → 一级标题
            }
            return listItem(normalized); // 否则 → 列表项
        }

        // 匹配单级数字行（1、2. 等）
        // 同样需要根据内容判断是标题还是列表项
        Matcher singleLevelDigitMatcher = SINGLE_LEVEL_DIGIT_LINE_PATTERN.matcher(normalized);
        if (singleLevelDigitMatcher.matches()) {
            String content = singleLevelDigitMatcher.group(2).trim();
            if (looksLikeHeadingContent(content)) {
                return heading(1, normalized, normalized); // 内容像标题 → 一级标题
            }
            return listItem(normalized); // 否则 → 列表项
        }

        // 匹配无序列表标记（- * + 及其 checkbox 变体）
        if (normalized.startsWith("- ")
            || normalized.startsWith("* ")
            || normalized.startsWith("+ ")
            || normalized.startsWith("- [")
            || normalized.startsWith("* [")
            || normalized.startsWith("+ [")) {
            return listItem(normalized);
        }

        // 以上都不匹配 → 归类为正文
        return new LineClassification(LineKind.BODY, 0, normalized, normalized);
    }

    /**
     * 创建标题分类结果
     *
     * @param level   标题层级（最小为 1）
     * @param title   标题文本
     * @param rawText 原始文本
     * @return 标题类型的分类结果
     */
    private LineClassification heading(int level, String title, String rawText) {
        return new LineClassification(LineKind.HEADING, Math.max(level, 1), safeText(title), safeText(rawText));
    }

    /**
     * 创建列表项分类结果
     *
     * @param rawText 原始文本
     * @return 列表项类型的分类结果
     */
    private LineClassification listItem(String rawText) {
        return new LineClassification(LineKind.LIST_ITEM, 0, safeText(rawText), safeText(rawText));
    }

    /**
     * 判断内容文本是否看起来像标题
     *
     * 【判断逻辑】
     * 标题通常具有以下特征：
     * - 不以句号等结束标点结尾
     * - 长度较短（不超过24个字符）
     * - 不包含逗号、分号、句号、冒号等标点
     *
     * @param content 待判断的内容文本
     * @return true 表示像标题，false 表示不像
     */
    private boolean looksLikeHeadingContent(String content) {
        String normalized = safeText(content);
        if (normalized.isBlank()) {
            return false;
        }

        // 以句号等结束标点结尾的通常是完整句子，不是标题
        if (endsWithSentencePunctuation(normalized)) {
            return false;
        }
        // 标题通常较短
        if (normalized.length() > 24) {
            return false;
        }
        // 标题通常不包含这些标点符号
        return !normalized.contains("，")
            && !normalized.contains("；")
            && !normalized.contains("。")
            && !normalized.contains("：");
    }

    /**
     * 判断文本是否以句子结束标点结尾
     *
     * @param text 待判断的文本
     * @return true 表示以结束标点结尾
     */
    private boolean endsWithSentencePunctuation(String text) {
        return text.endsWith("。")
            || text.endsWith("！")
            || text.endsWith("？")
            || text.endsWith("；")
            || text.endsWith(".")
            || text.endsWith("!")
            || text.endsWith("?")
            || text.endsWith(";");
    }

    /**
     * 安全的文本标准化处理
     * null 转为空字符串，并去除首尾空白
     *
     * @param text 原始文本
     * @return 标准化后的文本，不会返回 null
     */
    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * 行类型枚举（Line Kind）
     *
     * HEADING   - 标题行
     * LIST_ITEM - 列表项行
     * BODY      - 正文行
     */
    public enum LineKind {
        HEADING,
        LIST_ITEM,
        BODY
    }

    /**
     * 行分类结果（Line Classification）
     *
     * 使用 Java record 定义的不可变数据对象，存储分类结果。
     *
     * @param kind     行类型（标题/列表项/正文）
     * @param level    标题层级（仅标题类型有效，其他类型为 0）
     * @param title    标题文本
     * @param rawText  原始文本
     */
    public record LineClassification(
        LineKind kind,
        int level,
        String title,
        String rawText
    ) {
        /**
         * 判断是否为标题行
         * @return true 表示是标题
         */
        public boolean isHeading() {
            return kind == LineKind.HEADING;
        }

        /**
         * 判断是否为列表项行
         * @return true 表示是列表项
         */
        public boolean isListItem() {
            return kind == LineKind.LIST_ITEM;
        }
    }
}
