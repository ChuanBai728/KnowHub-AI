package ai.knowhub.document.support;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.document.config.DocumentManageProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档结构信号提取器（Document Structure Signal Extractor）
 *
 * 【类的作用】
 * 文档结构分析的第一层——信号提取。将文档的每一行文本分类为结构信号
 * （标题、列表项、表格行、引用、正文等），并标注置信度和分类原因。
 *
 * 【在架构中的角色】
 * 属于文档结构解析流水线的入口。输出的信号批次（SignalBatch）会传递给
 * DocumentStructureAmbiguityResolver 进行歧义消解。
 *
 * 【支持的信号类型】
 * - DOCUMENT_TITLE：文档标题
 * - HEADING：确定的标题（Markdown、章节、十进制数字等）
 * - HEADING_CANDIDATE：标题候选（需要 LLM 消解）
 * - STEP_ITEM：明确的步骤项（第1步、步骤三 等）
 * - LIST_ITEM：列表项（无序列表、有序列表、checkbox 等）
 * - TABLE_ROW：表格行
 * - QUOTE：引用行
 * - BODY：正文
 * - BLANK：空行
 * - NOISE：噪音行（页码、版权信息、重复页眉等）
 *
 * 【设计模式】
 * 使用正则表达式链式匹配，按优先级从高到低依次匹配。
 * 内部使用 record 类型 LineContext 管理上下文信息。
 */
@Component
public class DocumentStructureSignalExtractor {

    // ==================== 正则表达式定义 ====================

    /** Markdown 标题正则：# ~ ###### */
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    /** 十进制数字标题正则：1.1、2.3.1 等 */
    private static final Pattern DECIMAL_HEADING_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)+)\\s*[、.]?\\s*(.+)$");

    /** 单级数字行正则：1、2. 等 */
    private static final Pattern SINGLE_LEVEL_DIGIT_PATTERN = Pattern.compile("^(\\d+)\\s*[、.]\\s*(.+)$");

    /** 中文章节正则：第一章、第2节 等 */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^(第([一二三四五六七八九十百\\d]+)[章节条部分])\\s*(.+)$");

    /** 附录标记正则：附录A、附录一 等 */
    private static final Pattern APPENDIX_PATTERN = Pattern.compile("^(附录\\s*([A-Za-z一二三四五六七八九十百\\d]+))(?:\\s+(.+))?$");

    /** 中文大纲序号正则：一、二、三 等 */
    private static final Pattern CHINESE_OUTLINE_PATTERN = Pattern.compile("^([一二三四五六七八九十百]+)[、.]\\s*(.+)$");

    /** 明确步骤正则：第1步、步骤三 等 */
    private static final Pattern EXPLICIT_STEP_PATTERN = Pattern.compile("^(?:第\\s*([0-9一二三四五六七八九十百]+)\\s*步|步骤\\s*([0-9一二三四五六七八九十百]+))\\s*[:：、.]?\\s*(.+)$");

    /** 无序列表正则：- * + 开头 */
    private static final Pattern BULLET_PATTERN = Pattern.compile("^([-*+•])\\s+(.+)$");

    /** Checkbox 列表正则：[ ] 或 [x] 开头 */
    private static final Pattern CHECKBOX_PATTERN = Pattern.compile("^\\[(?: |x|X)]\\s+(.+)$");

    /** 页码噪音正则：第X页、Page X、X/Y 等 */
    private static final Pattern PAGE_NOISE_PATTERN = Pattern.compile("^(?:第\\s*\\d+\\s*页|Page\\s*\\d+|\\d+\\s*/\\s*\\d+)$", Pattern.CASE_INSENSITIVE);

    /** 版权噪音正则：版权所有、未经授权 等 */
    private static final Pattern COPYRIGHT_NOISE_PATTERN = Pattern.compile(".*(?:版权所有|未经授权|内部使用|copyright|all rights reserved|保密).*", Pattern.CASE_INSENSITIVE);

    /** 版本页脚噪音正则：V1.0、版本、修订 等 */
    private static final Pattern VERSION_FOOTER_PATTERN = Pattern.compile(".*(?:\\bV\\d+(?:\\.\\d+)*\\b|版本|修订|Rev\\.?\\s*\\d+).*", Pattern.CASE_INSENSITIVE);

    /** 内联步骤边界正则：用于拆分一行中的多个步骤 */
    private static final Pattern INLINE_EXPLICIT_STEP_BOUNDARY_PATTERN = Pattern.compile("(?=(?:第\\s*[0-9一二三四五六七八九十百]+\\s*步|步骤\\s*[0-9一二三四五六七八九十百]+)\\s*[:：、.])");

    /** 表格分隔符正则 */
    private static final Pattern TABLE_SPLIT_PATTERN = Pattern.compile("\\|");

    // ==================== 依赖注入 ====================

    /** 文档管理配置属性 */
    private final DocumentManageProperties properties;

    /** 文档行分类器（作为兜底） */
    private final DocumentLineClassifier documentLineClassifier;

    /**
     * 构造器注入
     *
     * @param properties            文档管理配置
     * @param documentLineClassifier 文档行分类器
     */
    public DocumentStructureSignalExtractor(DocumentManageProperties properties,
                                            DocumentLineClassifier documentLineClassifier) {
        this.properties = properties;
        this.documentLineClassifier = documentLineClassifier;
    }

    /**
     * 从文档文本中提取结构信号
     *
     * @param documentTitle 文档标题
     * @param parsedText    解析后的纯文本
     * @return 信号批次，包含上下文行和信号列表
     */
    public DocumentStructureSignalBatch extract(String documentTitle, String parsedText) {
        String normalizedTitle = safeText(documentTitle);
        // 构建逻辑行列表（处理内联拆分）
        List<DocumentStructureLogicalLine> logicalLines = buildLogicalLines(parsedText);
        // 统计每行出现频率（用于识别重复噪音）
        Map<String, Integer> lineFrequency = buildLineFrequency(logicalLines);
        List<DocumentStructureSignal> signals = new ArrayList<>(logicalLines.size() + 1);

        // 将文档标题作为第一个信号（行号为 0）
        if (StrUtil.isNotBlank(normalizedTitle)) {
            signals.add(DocumentStructureSignal.builder()
                .lineNo(0)
                .rawText(normalizedTitle)
                .normalizedText(normalizedTitle)
                .kind(DocumentStructureSignalKind.DOCUMENT_TITLE)
                .title(normalizedTitle)
                .levelHint(0)
                .confidence(1.0D)
                .build());
        }

        // 遍历所有逻辑行，逐行分类
        for (int index = 0; index < logicalLines.size(); index++) {
            DocumentStructureLogicalLine logicalLine = logicalLines.get(index);
            LineContext context = buildContext(logicalLines, index);
            signals.add(classify(normalizedTitle, logicalLine, context, lineFrequency));
        }

        // 提取上下文行文本
        List<String> contextLines = logicalLines.stream()
            .map(DocumentStructureLogicalLine::normalizedText)
            .toList();
        return new DocumentStructureSignalBatch(contextLines, signals);
    }

    /**
     * 对单个逻辑行进行分类
     *
     * 【分类优先级】
     * 1. 空行 → BLANK
     * 2. 重复噪音 → NOISE
     * 3. 页码噪音 → NOISE
     * 4. Markdown 标题 → HEADING（重复标题标题为 NOISE）
     * 5. 明确步骤 → STEP_ITEM
     * 6. 中文章节 → HEADING（重复标题为 NOISE）
     * 7. 附录标记 → HEADING
     * 8. 十进制数字标题 → HEADING
     * 9. 表格行 → TABLE_ROW
     * 10. 引用行 → QUOTE
     * 11. Checkbox 列表 → LIST_ITEM
     * 12. 无序列表 → LIST_ITEM
     * 13. 单级数字行 → LIST_ITEM 或 HEADING_CANDIDATE（需要上下文判断）
     * 14. 中文大纲序号 → LIST_ITEM 或 HEADING_CANDIDATE（需要上下文判断）
     * 15. 兜底分类器结果 → 根据内容判断
     * 16. 以上都不匹配 → BODY
     */
    private DocumentStructureSignal classify(String documentTitle,
                                             DocumentStructureLogicalLine logicalLine,
                                             LineContext context,
                                             Map<String, Integer> lineFrequency) {
        int lineNo = logicalLine.lineNo();
        String rawText = logicalLine.rawText();
        String normalized = logicalLine.normalizedText();
        String previousNonBlank = context.previousNonBlank() == null ? "" : context.previousNonBlank().normalizedText();
        String nextNonBlank = context.nextNonBlank() == null ? "" : context.nextNonBlank().normalizedText();

        // 空行
        if (normalized.isBlank()) {
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.BLANK, "", "", 0, null, List.of(), 1.0D);
        }

        // 重复噪音（页眉页脚、版权信息等）
        if (isRepeatedNoise(documentTitle, normalized, lineFrequency.getOrDefault(normalized, 0))) {
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.NOISE, "", "", 0, null,
                List.of("repeated-running-header-or-footer"), 0.99D);
        }

        // 页码噪音
        if (PAGE_NOISE_PATTERN.matcher(normalized).matches()) {
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.NOISE, "", "", 0, null,
                List.of("page-noise"), 0.98D);
        }

        // Markdown 标题
        Matcher markdown = MARKDOWN_HEADING_PATTERN.matcher(normalized);
        if (markdown.matches()) {
            String title = markdown.group(2).trim();
            // 如果标题与文档标题重复，标记为噪音
            if (sameDocumentTitle(documentTitle, title)) {
                return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.NOISE, "", title, 0, null,
                    List.of("duplicate-document-title"), 0.99D);
            }
            DocumentStructureSignal signal = signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.HEADING,
                extractCode(title), title, markdown.group(1).length(), null, List.of("markdown-heading"), 0.98D);
            signal.setNumericPath(extractNumericPath(signal.getNodeCode()));
            return signal;
        }

        // 明确步骤（第1步、步骤三 等）
        Matcher explicitStep = EXPLICIT_STEP_PATTERN.matcher(normalized);
        if (explicitStep.matches()) {
            Integer itemIndex = parseLooseNumber(StrUtil.blankToDefault(explicitStep.group(1), explicitStep.group(2)));
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.STEP_ITEM, "", explicitStep.group(3).trim(), null, itemIndex,
                List.of("explicit-step"), 0.96D);
        }

        // 中文章节（第一章、第2节 等）
        Matcher chapter = CHAPTER_PATTERN.matcher(normalized);
        if (chapter.matches()) {
            String code = chapter.group(1).trim();
            String title = chapter.group(3).trim();
            if (sameDocumentTitle(documentTitle, title)) {
                return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.NOISE, code, title, 0, null,
                    List.of("duplicate-document-title"), 0.99D);
            }
            DocumentStructureSignal signal = signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.HEADING,
                code, title, 1, null, List.of("chapter-heading"), 0.96D);
            Integer chapterNo = parseLooseNumber(chapter.group(2));
            if (chapterNo != null && chapterNo > 0) {
                signal.setNumericPath(List.of(chapterNo));
            }
            return signal;
        }

        // 附录标记（附录A、附录一 等）
        Matcher appendix = APPENDIX_PATTERN.matcher(normalized);
        if (appendix.matches()) {
            String code = appendix.group(1).trim();
            String title = StrUtil.blankToDefault(appendix.group(3), code).trim();
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.HEADING,
                code, title, 1, null, List.of("appendix-heading"), 0.92D);
        }

        // 十进制数字标题（1.1、2.3.1 等）
        Matcher decimal = DECIMAL_HEADING_PATTERN.matcher(normalized);
        if (decimal.matches()) {
            String code = decimal.group(1).trim();
            String title = decimal.group(2).trim();
            DocumentStructureSignal signal = signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.HEADING,
                code, title, Math.max(1, code.split("\\.").length), null, List.of("decimal-heading"), 0.95D);
            signal.setNumericPath(extractNumericPath(code));
            return signal;
        }

        // 表格行
        if (isTableRow(normalized)) {
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.TABLE_ROW, "", normalized, null, null,
                List.of("table-row"), 0.90D);
        }

        // 引用行
        if (normalized.startsWith(">")) {
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.QUOTE, "", normalized, null, null,
                List.of("quote"), 0.88D);
        }

        // Checkbox 列表
        Matcher checkbox = CHECKBOX_PATTERN.matcher(normalized);
        if (checkbox.matches()) {
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.LIST_ITEM, "", checkbox.group(1).trim(), null, null,
                List.of("checkbox-list"), 0.92D);
        }

        // 无序列表
        Matcher bullet = BULLET_PATTERN.matcher(normalized);
        if (bullet.matches()) {
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.LIST_ITEM, "", bullet.group(2).trim(), null, null,
                List.of("bullet-list"), 0.90D);
        }

        // 单级数字行（1、2. 等）— 需要上下文判断是标题还是列表项
        Matcher singleDigit = SINGLE_LEVEL_DIGIT_PATTERN.matcher(normalized);
        if (singleDigit.matches()) {
            String title = singleDigit.group(2).trim();
            Integer itemIndex = parseLooseNumber(singleDigit.group(1));
            boolean sequential = isNeighborSequence(itemIndex, OrderedMarkerFamily.ARABIC_SINGLE, context);
            boolean introducedByLeadIn = previousIntroducesList(context.previousNonBlank());
            boolean headingLike = !sequential
                && !introducedByLeadIn
                && looksLikePlainHeading(title, context);
            DocumentStructureSignal signal = signal(
                lineNo,
                rawText,
                normalized,
                logicalLine.indentLevel(),
                headingLike ? DocumentStructureSignalKind.HEADING_CANDIDATE : DocumentStructureSignalKind.LIST_ITEM,
                singleDigit.group(1).trim(),
                title,
                headingLike ? 1 : null,
                itemIndex,
                List.of(headingLike ? "single-digit-ambiguous-heading"
                    : sequential ? "single-digit-sequence-list" : "single-digit-list"),
                headingLike ? 0.62D : sequential || introducedByLeadIn ? 0.93D : 0.88D
            );
            if (headingLike && itemIndex != null && itemIndex > 0) {
                signal.setNumericPath(List.of(itemIndex));
            }
            return signal;
        }

        // 中文大纲序号（一、二、三 等）— 同样需要上下文判断
        Matcher chineseOutline = CHINESE_OUTLINE_PATTERN.matcher(normalized);
        if (chineseOutline.matches()) {
            String title = chineseOutline.group(2).trim();
            Integer index = parseLooseNumber(chineseOutline.group(1));
            boolean sequential = isNeighborSequence(index, OrderedMarkerFamily.CHINESE_OUTLINE, context);
            boolean introducedByLeadIn = previousIntroducesList(context.previousNonBlank());
            boolean headingLike = !sequential
                && !introducedByLeadIn
                && looksLikePlainHeading(title, context);
            DocumentStructureSignal signal = signal(
                lineNo,
                rawText,
                normalized,
                logicalLine.indentLevel(),
                headingLike ? DocumentStructureSignalKind.HEADING_CANDIDATE : DocumentStructureSignalKind.LIST_ITEM,
                chineseOutline.group(1).trim(),
                title,
                headingLike ? 1 : null,
                index,
                List.of(headingLike ? "chinese-outline-ambiguous-heading"
                    : sequential ? "chinese-outline-sequence-list" : "chinese-outline-list"),
                headingLike ? 0.60D : sequential || introducedByLeadIn ? 0.92D : 0.86D
            );
            if (headingLike && index != null && index > 0) {
                signal.setNumericPath(List.of(index));
            }
            return signal;
        }

        // 兜底：使用 DocumentLineClassifier 进行分类
        DocumentLineClassifier.LineClassification fallback = documentLineClassifier.classify(normalized);
        if (!fallback.isHeading() && looksLikePlainHeading(normalized, context)) {
            return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.HEADING_CANDIDATE,
                "", normalized, inferPlainHeadingLevel(context), null, List.of("plain-heading-candidate"), 0.58D);
        }

        // 默认：正文
        return signal(lineNo, rawText, normalized, logicalLine.indentLevel(), DocumentStructureSignalKind.BODY,
            "", normalized, null, null, List.of("body"), 1.0D);
    }

    /**
     * 构建 DocumentStructureSignal 对象的工厂方法
     */
    private DocumentStructureSignal signal(int lineNo,
                                           String rawText,
                                           String normalized,
                                           int indentLevel,
                                           DocumentStructureSignalKind kind,
                                           String code,
                                           String title,
                                           Integer levelHint,
                                           Integer itemIndex,
                                           List<String> reasons,
                                           double confidence) {
        return DocumentStructureSignal.builder()
            .lineNo(lineNo)
            .rawText(rawText)
            .normalizedText(normalized)
            .kind(kind)
            .nodeCode(StrUtil.blankToDefault(code, ""))
            .title(StrUtil.blankToDefault(title, normalized))
            .levelHint(levelHint)
            .indentLevel(indentLevel)
            .itemIndex(itemIndex)
            .reasons(new ArrayList<>(reasons))
            .confidence(confidence)
            .build();
    }

    /**
     * 从标题文本中提取编码（编号标识）
     */
    private String extractCode(String title) {
        Matcher decimal = DECIMAL_HEADING_PATTERN.matcher(title);
        if (decimal.matches()) {
            return decimal.group(1).trim();
        }
        Matcher chapter = CHAPTER_PATTERN.matcher(title);
        if (chapter.matches()) {
            return chapter.group(1).trim();
        }
        Matcher appendix = APPENDIX_PATTERN.matcher(title);
        if (appendix.matches()) {
            return appendix.group(1).trim();
        }
        return "";
    }

    /**
     * 从编码字符串中提取数字路径
     * 例如 "1.2.3" → [1, 2, 3]，"第一章" → [1]
     */
    private List<Integer> extractNumericPath(String code) {
        String normalized = safeText(code);
        if (normalized.isBlank()) {
            return List.of();
        }
        if (normalized.contains(".")) {
            List<Integer> path = new ArrayList<>();
            for (String segment : normalized.split("\\.")) {
                if (!segment.chars().allMatch(Character::isDigit)) {
                    return List.of();
                }
                path.add(Integer.parseInt(segment));
            }
            return path;
        }
        Matcher chapter = CHAPTER_PATTERN.matcher(normalized + " 标题");
        if (chapter.find()) {
            Integer chapterNo = parseLooseNumber(chapter.group(2));
            if (chapterNo != null && chapterNo > 0) {
                return List.of(chapterNo);
            }
        }
        return List.of();
    }

    /**
     * 判断是否为表格行
     */
    private boolean isTableRow(String normalized) {
        if (normalized.startsWith("|") && normalized.endsWith("|")) {
            return true;
        }
        if (normalized.contains("\t")) {
            return true;
        }
        if (TABLE_SPLIT_PATTERN.split(normalized).length >= 3 && normalized.contains("|")) {
            return true;
        }
        return normalized.matches("^[:\\-\\s|]+$");
    }

    /**
     * 判断文本是否看起来像普通标题（基于上下文）
     *
     * 【判断条件】
     * - 长度不超过配置上限
     * - 不以句子结束标点结尾
     * - 不包含 URL
     * - 不是表格行
     * - 不是分隔线
     * - 前后有空行（孤立行）
     * - 下一行看起来像内容
     * - 不包含逗号、分号等标点（名词性短语）
     */
    private boolean looksLikePlainHeading(String text,
                                          LineContext context) {
        String normalized = safeText(text);
        if (text.isBlank()) {
            return false;
        }
        if (normalized.length() > properties.getStructureParsing().getMaxPlainHeadingChars()) {
            return false;
        }
        if (endsWithSentencePunctuation(normalized)) {
            return false;
        }
        if (normalized.contains("http://") || normalized.contains("https://")) {
            return false;
        }
        if (normalized.startsWith("|") || normalized.endsWith("|")) {
            return false;
        }
        if (normalized.matches("^[\\-=_]{3,}$")) {
            return false;
        }
        boolean isolated = context.blankBefore() || context.blankAfter();
        boolean nextLooksContent = context.nextNonBlank() != null
            && StrUtil.isNotBlank(context.nextNonBlank().normalizedText())
            && !context.nextNonBlank().normalizedText().matches("^[:\\-\\s|]+$");
        boolean nounLike = !normalized.contains("，")
            && !normalized.contains("；")
            && !normalized.contains("。")
            && !normalized.contains("：")
            && !normalized.toLowerCase(Locale.ROOT).startsWith("http");
        return isolated && nextLooksContent && nounLike;
    }

    /**
     * 推断普通标题的层级
     */
    private int inferPlainHeadingLevel(LineContext context) {
        if (context == null || context.blankBefore()) {
            return 1;
        }
        return 2;
    }

    /**
     * 判断文本是否以句子结束标点结尾
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
     * 构建逻辑行列表
     * 处理物理行到逻辑行的映射，支持一行拆分为多个逻辑行（内联步骤拆分）
     */
    private List<DocumentStructureLogicalLine> buildLogicalLines(String parsedText) {
        String[] rawLines = StrUtil.blankToDefault(parsedText, "").split("\n", -1);
        List<DocumentStructureLogicalLine> logicalLines = new ArrayList<>(rawLines.length);
        int logicalLineNo = 1;
        for (int index = 0; index < rawLines.length; index++) {
            String rawLine = StrUtil.blankToDefault(rawLines[index], "");
            List<String> segments = splitInlineSegments(rawLine);
            if (segments.isEmpty()) {
                logicalLines.add(new DocumentStructureLogicalLine(
                    logicalLineNo++,
                    index + 1,
                    1,
                    0,
                    rawLine,
                    safeText(rawLine)
                ));
                continue;
            }
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                String segment = segments.get(segmentIndex);
                logicalLines.add(new DocumentStructureLogicalLine(
                    logicalLineNo++,
                    index + 1,
                    segmentIndex + 1,
                    countIndentLevel(segment),
                    segment,
                    safeText(segment)
                ));
            }
        }
        return logicalLines;
    }

    /**
     * 拆分一行中的内联段落（如一行中包含多个步骤）
     */
    private List<String> splitInlineSegments(String rawLine) {
        if (rawLine == null) {
            return List.of();
        }
        if (rawLine.trim().isEmpty()) {
            return List.of();
        }
        String trimmed = rawLine.trim();
        // 不拆分 Markdown 标题、表格行、引用行和分隔线
        if (trimmed.startsWith("#")
            || trimmed.startsWith("|")
            || trimmed.startsWith(">")
            || trimmed.matches("^[:\\-\\s|]+$")) {
            return List.of(rawLine);
        }

        // 查找内联步骤边界
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        Matcher matcher = INLINE_EXPLICIT_STEP_BOUNDARY_PATTERN.matcher(rawLine);
        while (matcher.find()) {
            if (matcher.start() > 0) {
                boundaries.add(matcher.start());
            }
        }
        if (boundaries.size() == 1) {
            return List.of(rawLine);
        }
        // 按边界拆分
        List<String> segments = new ArrayList<>();
        for (int index = 0; index < boundaries.size(); index++) {
            int start = boundaries.get(index);
            int end = index == boundaries.size() - 1 ? rawLine.length() : boundaries.get(index + 1);
            String segment = rawLine.substring(start, end).trim();
            if (StrUtil.isNotBlank(segment)) {
                segments.add(segment);
            }
        }
        return segments.isEmpty() ? List.of(rawLine) : segments;
    }

    /**
     * 计算文本的缩进层级（空格数，tab 算 4 个空格）
     */
    private int countIndentLevel(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int indent = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == ' ') {
                indent++;
                continue;
            }
            if (current == '\t') {
                indent += 4;
                continue;
            }
            break;
        }
        return indent;
    }

    /**
     * 统计每行文本的出现频率
     */
    private Map<String, Integer> buildLineFrequency(List<DocumentStructureLogicalLine> logicalLines) {
        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (DocumentStructureLogicalLine logicalLine : logicalLines) {
            if (logicalLine == null || StrUtil.isBlank(logicalLine.normalizedText())) {
                continue;
            }
            frequency.merge(logicalLine.normalizedText(), 1, Integer::sum);
        }
        return frequency;
    }

    /**
     * 构建当前行的上下文信息（前后行和空行状态）
     */
    private LineContext buildContext(List<DocumentStructureLogicalLine> logicalLines, int currentIndex) {
        DocumentStructureLogicalLine previousNonBlank = null;
        boolean blankBefore = false;
        for (int index = currentIndex - 1; index >= 0; index--) {
            DocumentStructureLogicalLine candidate = logicalLines.get(index);
            if (StrUtil.isBlank(candidate.normalizedText())) {
                blankBefore = true;
                continue;
            }
            previousNonBlank = candidate;
            break;
        }
        DocumentStructureLogicalLine nextNonBlank = null;
        boolean blankAfter = false;
        for (int index = currentIndex + 1; index < logicalLines.size(); index++) {
            DocumentStructureLogicalLine candidate = logicalLines.get(index);
            if (StrUtil.isBlank(candidate.normalizedText())) {
                blankAfter = true;
                continue;
            }
            nextNonBlank = candidate;
            break;
        }
        return new LineContext(previousNonBlank, nextNonBlank, blankBefore, blankAfter);
    }

    /**
     * 判断是否为重复噪音（页眉页脚、版权信息等）
     */
    private boolean isRepeatedNoise(String documentTitle,
                                    String normalized,
                                    int frequency) {
        if (frequency < 2 || StrUtil.isBlank(normalized)) {
            return false;
        }
        if (sameDocumentTitle(documentTitle, normalized)) {
            return true;
        }
        if (COPYRIGHT_NOISE_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        return frequency >= 3
            && normalized.length() <= 120
            && (VERSION_FOOTER_PATTERN.matcher(normalized).matches() || normalized.contains("|"));
    }

    /**
     * 判断候选文本是否与文档标题相同
     */
    private boolean sameDocumentTitle(String documentTitle,
                                      String candidate) {
        String left = normalizeComparableTitle(documentTitle);
        String right = normalizeComparableTitle(candidate);
        return StrUtil.isNotBlank(left) && left.equals(right);
    }

    /**
     * 标准化标题文本用于比较（去除 # 号、扩展名、空白，转小写）
     */
    private String normalizeComparableTitle(String text) {
        String normalized = safeText(text);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized
            .replaceAll("^#+\\s*", "")
            .replaceAll("\\.[A-Za-z0-9]{1,6}$", "")
            .replaceAll("\\s+", "")
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 判断前一行是否是列表引导行（以冒号结尾）
     */
    private boolean previousIntroducesList(DocumentStructureLogicalLine previousNonBlank) {
        if (previousNonBlank == null) {
            return false;
        }
        String previous = safeText(previousNonBlank.normalizedText());
        return previous.endsWith("：") || previous.endsWith(":");
    }

    /**
     * 判断相邻行是否构成连续序列
     */
    private boolean isNeighborSequence(Integer itemIndex,
                                       OrderedMarkerFamily family,
                                       LineContext context) {
        if (itemIndex == null || family == null) {
            return false;
        }
        return isSequenceNeighbor(context.previousNonBlank(), itemIndex, family, -1)
            || isSequenceNeighbor(context.nextNonBlank(), itemIndex, family, 1);
    }

    /**
     * 判断候选行是否是序列邻居（前一个或后一个序号）
     */
    private boolean isSequenceNeighbor(DocumentStructureLogicalLine candidate,
                                       Integer itemIndex,
                                       OrderedMarkerFamily family,
                                       int offset) {
        if (candidate == null || itemIndex == null) {
            return false;
        }
        Integer candidateIndex = resolveOrderedIndex(candidate.normalizedText(), family);
        return candidateIndex != null && candidateIndex.intValue() == itemIndex.intValue() + offset;
    }

    /**
     * 从文本中解析有序标记的索引值
     */
    private Integer resolveOrderedIndex(String text,
                                        OrderedMarkerFamily family) {
        String normalized = safeText(text);
        if (normalized.isBlank()) {
            return null;
        }
        return switch (family) {
            case ARABIC_SINGLE -> {
                Matcher matcher = SINGLE_LEVEL_DIGIT_PATTERN.matcher(normalized);
                yield matcher.matches() ? parseLooseNumber(matcher.group(1)) : null;
            }
            case CHINESE_OUTLINE -> {
                Matcher matcher = CHINESE_OUTLINE_PATTERN.matcher(normalized);
                yield matcher.matches() ? parseLooseNumber(matcher.group(1)) : null;
            }
        };
    }

    /**
     * 解析宽松的数字（支持阿拉伯数字和中文数字）
     * 例如："1" → 1，"三" → 3，"十二" → 12，"二十三" → 23
     */
    private Integer parseLooseNumber(String text) {
        String normalized = safeText(text);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(normalized);
        }
        Map<Character, Integer> digitMap = Map.of(
            '一', 1, '二', 2, '三', 3, '四', 4, '五', 5,
            '六', 6, '七', 7, '八', 8, '九', 9
        );
        if ("十".equals(normalized)) {
            return 10;
        }
        if (normalized.startsWith("十") && normalized.length() == 2) {
            return 10 + digitMap.getOrDefault(normalized.charAt(1), 0);
        }
        if (normalized.endsWith("十") && normalized.length() == 2) {
            return digitMap.getOrDefault(normalized.charAt(0), 0) * 10;
        }
        if (normalized.contains("十") && normalized.length() == 3) {
            return digitMap.getOrDefault(normalized.charAt(0), 0) * 10 + digitMap.getOrDefault(normalized.charAt(2), 0);
        }
        return digitMap.get(normalized.charAt(0));
    }

    /**
     * 安全的文本标准化
     */
    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * 有序标记家族枚举
     * 用于区分不同类型的有序列表标记
     */
    private enum OrderedMarkerFamily {
        ARABIC_SINGLE,   // 阿拉伯数字单级：1、2、3 等
        CHINESE_OUTLINE  // 中文大纲序号：一、二、三 等
    }

    /**
     * 行上下文记录类
     * 存储当前行的上下文信息，用于辅助分类判断
     *
     * @param previousNonBlank 前一个非空行
     * @param nextNonBlank     后一个非空行
     * @param blankBefore      前面是否有空行
     * @param blankAfter       后面是否有空行
     */
    private record LineContext(
        DocumentStructureLogicalLine previousNonBlank,
        DocumentStructureLogicalLine nextNonBlank,
        boolean blankBefore,
        boolean blankAfter
    ) {
    }
}
