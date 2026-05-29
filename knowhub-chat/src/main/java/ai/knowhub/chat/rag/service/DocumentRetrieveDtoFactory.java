package ai.knowhub.chat.rag.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.DocumentNavigationDecision;
import ai.knowhub.chat.rag.model.HistoryPlanningContext;
import ai.knowhub.document.model.DocumentRetrieveFilters;
import ai.knowhub.document.model.DocumentRetrieveDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【文档检索请求工厂 — RAG 流水线的"检索请求构造器"】
 *
 * 这个类负责为每个子问题构建完整的检索请求（{@link DocumentRetrieveDto}）。
 * 检索请求不仅包含问题文本，还会带上：
 * - 文档范围（指定在哪些文档中检索）
 * - 任务版本（指定使用哪个索引任务的结果）
 * - 过滤条件（从问题中提取的年份、章节、文档类型等线索）
 * - 查询上下文提示（从历史对话和导航决策中提取的补充语义）
 *
 * 设计模式：工厂模式（Factory Pattern），封装了检索请求的复杂构建逻辑。
 *
 * 在 RAG 流水线中的位置：
 * 执行计划 -> 【本类：构建检索请求】-> RagRetrievalEngine -> 检索通道
 */
@Slf4j
@Component
public class DocumentRetrieveDtoFactory {

    /** 匹配年份（如 2024、2025），用于从问题中提取时间过滤条件 */
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");

    /** 匹配章节引用（如"第3章"、"附录A"），用于从问题中提取章节过滤条件 */
    private static final Pattern SECTION_PATTERN = Pattern.compile("(第\\s*[一二三四五六七八九十百0-9]+\\s*[章节条部分])|(附录\\s*[A-Za-z一二三四五六七八九十0-9]+)");

    /**
     * 文档名称提示词——从问题中识别文档类型线索。
     * 例如用户问"部署手册中的 XX"，"部署手册"就是文档名称提示。
     */
    private static final List<String> DOCUMENT_NAME_HINTS = List.of(
        "部署手册", "配置手册", "操作手册", "用户手册", "快速开始", "接入指南", "FAQ", "常见问题",
        "说明文档", "说明书", "规范", "指南", "手册", "文档"
    );

    /**
     * 业务分类提示词——从问题中识别业务领域线索。
     */
    private static final List<String> BUSINESS_CATEGORY_HINTS = List.of(
        "流程", "规则", "操作手册", "部署", "配置", "接入", "协议", "故障", "排错", "规范", "说明"
    );

    /**
     * 文档标签提示词——从问题中识别标签线索（年份、主题等）。
     */
    private static final List<String> DOCUMENT_TAG_HINTS = List.of(
        "2024", "2025", "2026", "部署", "配置", "接入", "协议", "FAQ", "故障", "排错", "升级", "兼容"
    );

    /**
     * 构建检索请求。
     *
     * 构建流程：
     * 1. 标准化子问题文本
     * 2. 构建查询增强（补充历史上下文和导航提示）
     * 3. 从问题中提取过滤条件（年份、章节、文档类型等）
     * 4. 组装完整的检索请求
     *
     * @param subQuestion 子问题文本
     * @param plan        执行计划（包含文档范围、任务ID等）
     * @param topK        返回的最大文档数量
     * @return DocumentRetrieveDto 完整的检索请求
     */
    public DocumentRetrieveDto build(String subQuestion, ConversationExecutionPlan plan, int topK) {
        // 检索请求不仅包含问题，还会带上文档范围、任务版本、过滤条件和上下文提示。
        String normalizedQuestion = StrUtil.blankToDefault(subQuestion, "").trim();
        // 构建查询增强（补充上下文）
        QueryAugmentation augmentation = buildQueryAugmentation(
            normalizedQuestion,
            plan.getHistoryPlanningContext(),
            plan.getNavigationDecision()
        );
        // 从问题中提取过滤条件
        DocumentRetrieveFilters filters = buildFilters(normalizedQuestion);
        // 组装检索请求
        DocumentRetrieveDto request = new DocumentRetrieveDto(
            normalizedQuestion,                    // 原始子问题
            augmentation.retrievalQuery(),          // 增强后的检索查询（可能包含历史上下文）
            plan.getSelectedDocumentId(),           // 选中的文档ID
            plan.getSelectedTaskId(),               // 选中的任务ID
            topK,                                   // 返回的最大文档数
            filters,                                // 过滤条件
            augmentation.queryContextHints()        // 查询上下文提示
        );
        // 设置文档ID列表和任务ID列表（支持多文档检索）
        request.setDocumentIds(plan.getRetrievalDocumentIds() == null || plan.getRetrievalDocumentIds().isEmpty()
            ? (plan.getSelectedDocumentId() == null ? List.of() : List.of(plan.getSelectedDocumentId()))
            : plan.getRetrievalDocumentIds());
        request.setTaskIds(plan.getRetrievalTaskIds() == null || plan.getRetrievalTaskIds().isEmpty()
            ? (plan.getSelectedTaskId() == null ? List.of() : List.of(plan.getSelectedTaskId()))
            : plan.getRetrievalTaskIds());
        log.info("检索请求构造: originalSubQuestion='{}', retrievalQuery='{}', documentId={}, taskId={}, sectionHints={}, yearHints={}, queryContextHints={}",
            normalizedQuestion,
            request.getRetrievalQuery(),
            request.getDocumentId(),
            request.getTaskId(),
            filters == null ? List.of() : filters.getSectionPathHints(),
            filters == null ? List.of() : filters.getYearHints(),
            request.getQueryContextHints());
        return request;
    }

    /**
     * 构建查询增强信息。
     *
     * 短追问常常缺主语，例如"那它怎么配置"，这里会用历史和导航提示补全检索语义。
     * 例如：原始问题"那它怎么配置" + 历史提示"Redis" -> 增强后"那它怎么配置 Redis"
     *
     * @param normalizedQuestion    标准化后的问题
     * @param historyPlanningContext 历史规划上下文
     * @param navigationDecision    导航决策
     * @return QueryAugmentation 查询增强结果
     */
    private QueryAugmentation buildQueryAugmentation(String normalizedQuestion,
                                                     HistoryPlanningContext historyPlanningContext,
                                                     DocumentNavigationDecision navigationDecision) {
        // 短追问常常缺主语，例如"那它怎么配置"，这里会用历史和导航提示补全检索语义。
        if (StrUtil.isBlank(normalizedQuestion)) {
            return new QueryAugmentation("", List.of());
        }
        // 从导航决策中提取提示词
        List<String> navigationHints = navigationDecision == null || navigationDecision.getQueryContextHints() == null
            ? List.of()
            : navigationDecision.getQueryContextHints().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .limit(4)
                .toList();
        // 如果不像短追问，或者没有历史上下文，直接使用原始问题
        if (!looksLikeShortFollowUp(normalizedQuestion)
            || historyPlanningContext == null
            || historyPlanningContext.getQueryContextHints() == null
            || historyPlanningContext.getQueryContextHints().isEmpty()) {
            if (navigationHints.isEmpty()) {
                return new QueryAugmentation(normalizedQuestion, extractMeaningfulTerms(normalizedQuestion));
            }
            // 只有导航提示，没有历史提示
            String retrievalQuery = (normalizedQuestion + " " + String.join(" ", navigationHints)).trim();
            List<String> queryHints = new ArrayList<>(navigationHints);
            queryHints.addAll(extractMeaningfulTerms(normalizedQuestion));
            return new QueryAugmentation(retrievalQuery, queryHints.stream().distinct().limit(8).toList());
        }
        // 短追问 + 有历史上下文：合并历史提示和导航提示
        List<String> normalizedHints = historyPlanningContext.getQueryContextHints().stream()
            .filter(StrUtil::isNotBlank)
            .map(String::trim)
            .distinct()
            .limit(4)
            .toList();
        List<String> allHints = new ArrayList<>(normalizedHints);
        allHints.addAll(navigationHints);
        if (allHints.isEmpty()) {
            return new QueryAugmentation(normalizedQuestion, extractMeaningfulTerms(normalizedQuestion));
        }
        String retrievalQuery = (normalizedQuestion + " " + String.join(" ", allHints)).trim();
        List<String> queryContextHints = new ArrayList<>(allHints);
        queryContextHints.addAll(extractMeaningfulTerms(normalizedQuestion));
        return new QueryAugmentation(retrievalQuery, queryContextHints.stream().distinct().limit(8).toList());
    }

    /**
     * 从问题中构建过滤条件。
     *
     * 这些过滤条件来自用户问题中的显式线索：
     * - 年份：如"2024 年的部署手册" -> yearHints=["2024"]
     * - 章节：如"第三章的内容" -> sectionPathHints=["第三章"]
     * - 文档类型：如"FAQ 中" -> documentNameHints=["FAQ"]
     * - 业务分类：如"部署流程" -> businessCategoryHints=["部署", "流程"]
     * - 文档标签：如"2025 年升级" -> documentTagHints=["2025", "升级"]
     */
    private DocumentRetrieveFilters buildFilters(String question) {
        // 这些过滤条件来自用户问题中的显式线索，例如年份、章节、文档类型或业务标签。
        if (StrUtil.isBlank(question)) {
            return DocumentRetrieveFilters.builder().build();
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> documentNameHints = new LinkedHashSet<>();
        LinkedHashSet<String> businessCategoryHints = new LinkedHashSet<>();
        LinkedHashSet<String> documentTagHints = new LinkedHashSet<>();
        LinkedHashSet<String> sectionPathHints = new LinkedHashSet<>();
        LinkedHashSet<String> yearHints = new LinkedHashSet<>();

        // 提取年份
        Matcher yearMatcher = YEAR_PATTERN.matcher(question);
        while (yearMatcher.find()) {
            yearHints.add(yearMatcher.group(1));
        }

        // 提取章节引用
        Matcher sectionMatcher = SECTION_PATTERN.matcher(question);
        while (sectionMatcher.find()) {
            if (StrUtil.isNotBlank(sectionMatcher.group())) {
                sectionPathHints.add(sectionMatcher.group().replaceAll("\\s+", ""));
            }
        }

        // 提取文档名称提示
        for (String hint : DOCUMENT_NAME_HINTS) {
            if (normalized.contains(hint.toLowerCase(Locale.ROOT))) {
                documentNameHints.add(hint);
            }
        }
        // 提取业务分类提示
        for (String hint : BUSINESS_CATEGORY_HINTS) {
            if (normalized.contains(hint.toLowerCase(Locale.ROOT))) {
                businessCategoryHints.add(hint);
            }
        }
        // 提取文档标签提示
        for (String hint : DOCUMENT_TAG_HINTS) {
            if (normalized.contains(hint.toLowerCase(Locale.ROOT))) {
                documentTagHints.add(hint);
            }
        }

        return DocumentRetrieveFilters.builder()
            .documentNameHints(new ArrayList<>(documentNameHints))
            .businessCategoryHints(new ArrayList<>(businessCategoryHints))
            .documentTagHints(new ArrayList<>(documentTagHints))
            .sectionPathHints(new ArrayList<>(sectionPathHints))
            .yearHints(new ArrayList<>(yearHints))
            .build();
    }

    /**
     * 判断问题是否像短追问。
     * 短追问的特征：长度 < 12 字，或包含"它"、"这个"、"那个"、"刚才"、"前面"、"上面"等指代词。
     */
    private boolean looksLikeShortFollowUp(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return question.length() < 12
            || question.contains("它")
            || question.contains("这个")
            || question.contains("那个")
            || question.contains("刚才")
            || question.contains("前面")
            || question.contains("上面");
    }

    /**
     * 从问题中提取有意义的关键词（长度 >= 2 的词）。
     * 用作查询上下文提示，帮助检索引擎理解问题语义。
     */
    private List<String> extractMeaningfulTerms(String question) {
        if (StrUtil.isBlank(question)) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String segment : question.split("[\\s、，,；;：:（）()\\-的和及与或]+")) {
            String trimmed = segment.trim();
            if (trimmed.length() >= 2) {
                terms.add(trimmed);
            }
        }
        return new ArrayList<>(terms).stream().limit(6).toList();
    }

    /**
     * 查询增强结果（内部数据结构）。
     *
     * @param retrievalQuery    增强后的检索查询（可能包含历史上下文提示）
     * @param queryContextHints 查询上下文提示词列表
     */
    private record QueryAugmentation(String retrievalQuery, List<String> queryContextHints) {
    }
}
