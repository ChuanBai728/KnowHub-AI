package ai.knowhub.chat.rag.service;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.AnswerHistoryContext;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.RagPromptAssemblyResult;
import ai.knowhub.chat.rag.model.RagRetrievalContext;
import ai.knowhub.chat.rag.model.SubQuestionEvidence;
import ai.knowhub.prompt.PromptTemplateNames;
import ai.knowhub.prompt.PromptTemplateService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【RAG Prompt 组装服务 — RAG 流水线的"拼装车间"】
 *
 * 这个类负责将 RAG 检索到的证据（文档片段）组装成最终发给 LLM 的 Prompt。
 * 它是 RAG 流水线的最后一步，决定了 LLM 看到什么样的上下文。
 *
 * 组装的 Prompt 包含以下部分：
 * 1. System Prompt：系统提示词（定义 AI 的角色和行为规范）
 * 2. User Prompt：用户提示词，包含：
 *    - 当前日期
 *    - 原始问题
 *    - 改写后的检索问题（如果与原始问题不同）
 *    - 历史上下文（如果是追问场景）
 *    - 子问题列表（如果问题被拆分）
 *    - 证据块（每个子问题对应的检索结果）
 *
 * 预算控制：
 * 证据文本的长度受两个预算限制：
 * - totalBudget：所有证据的总字符数上限
 * - perSubQuestionBudget：每个子问题的证据字符数上限
 * 超出预算的证据会被省略，并在 Prompt 中标注"已省略"。
 *
 * 设计模式：建造者模式（Builder Pattern），通过 PromptBudget 内部类控制预算。
 *
 * 在 RAG 流水线中的位置：
 * 检索引擎返回证据 -> 【本类：组装 Prompt】-> LLM 生成回答
 */
@Service
public class RagPromptAssemblyService {

    /** RAG 配置属性 */
    private final ChatRagProperties properties;
    /** 提示词模板服务 */
    private final PromptTemplateService promptTemplateService;

    /**
     * 构造函数。
     */
    public RagPromptAssemblyService(ChatRagProperties properties,
                                    PromptTemplateService promptTemplateService) {
        this.properties = properties;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 构建系统提示词。
     *
     * 如果配置了自定义的 answerSystemPrompt，使用自定义的；
     * 否则使用默认的 RAG_ANSWER_SYSTEM 模板。
     */
    public String buildSystemPrompt() {

        return StrUtil.isNotBlank(properties.getAnswerSystemPrompt())
            ? properties.getAnswerSystemPrompt().trim()
            : promptTemplateService.render(PromptTemplateNames.RAG_ANSWER_SYSTEM, Map.of());
    }

    /**
     * 构建用户提示词（简化版本，只返回 userPrompt 字符串）。
     *
     * @param plan    执行计划
     * @param context 检索上下文
     * @return 用户提示词文本
     */
    public String buildUserPrompt(ConversationExecutionPlan plan, RagRetrievalContext context) {
        return assemble(plan, context).getUserPrompt();
    }

    /**
     * 组装完整的 Prompt（包含系统提示词和用户提示词）。
     *
     * 组装流程：
     * 1. 初始化预算控制器
     * 2. 渲染用户提示词模板（填充问题、历史、证据等变量）
     * 3. 返回组装结果（包含预算使用情况）
     *
     * @param plan    执行计划（包含问题、历史上下文等）
     * @param context 检索上下文（包含各子问题的证据列表）
     * @return RagPromptAssemblyResult 组装结果
     */
    public RagPromptAssemblyResult assemble(ConversationExecutionPlan plan, RagRetrievalContext context) {
        // Prompt 组装阶段会把问题、历史和证据放进模板，同时严格控制证据文本长度。
        PromptBudget promptBudget = new PromptBudget(
            Math.max(0, properties.getTotalEvidenceMaxChars()),
            Math.max(0, properties.getPerSubQuestionEvidenceMaxChars())
        );
        // 已渲染的引用键集合（用于去重）
        Set<String> renderedReferenceKeys = new LinkedHashSet<>();
        // 渲染用户提示词模板
        String userPrompt = promptTemplateService.render(PromptTemplateNames.RAG_ANSWER_USER, Map.of(
            "currentDate", StrUtil.blankToDefault(plan.getCurrentDateText(), ""),
            "originalQuestion", StrUtil.blankToDefault(plan.getOriginalQuestion(), ""),
            "hasRetrievalQuestion", hasRetrievalQuestion(plan),
            "retrievalQuestion", StrUtil.blankToDefault(plan.getRetrievalQuestion(), ""),
            "hasHistoryContext", hasHistoryContext(plan),
            "historyContext", buildHistoryContext(plan),
            "hasSubQuestions", hasSubQuestions(plan),
            "subQuestions", buildSubQuestions(plan),
            "evidenceBlocks", buildEvidenceBlocks(context, renderedReferenceKeys, promptBudget)
        ));
        return new RagPromptAssemblyResult(
            buildSystemPrompt(),
            userPrompt,
            promptBudget.totalBudget,
            promptBudget.perSubQuestionBudget,
            promptBudget.renderedReferenceCount,
            promptBudget.omittedReferenceCount,
            promptBudget.renderedReferenceDetails,
            promptBudget.omittedReferenceDetails
        );
    }

    /** 判断是否有不同于原始问题的检索问题 */
    private boolean hasRetrievalQuestion(ConversationExecutionPlan plan) {
        return StrUtil.isNotBlank(plan.getRetrievalQuestion()) && !plan.getRetrievalQuestion().equals(plan.getOriginalQuestion());
    }

    /** 判断是否有历史上下文 */
    private boolean hasHistoryContext(ConversationExecutionPlan plan) {
        AnswerHistoryContext answerHistoryContext = plan.getAnswerHistoryContext();
        return answerHistoryContext != null && !answerHistoryContext.isEmpty();
    }

    /** 构建历史上下文文本 */
    private String buildHistoryContext(ConversationExecutionPlan plan) {
        return hasHistoryContext(plan) ? plan.getAnswerHistoryContext().getRenderedText().trim() : "";
    }

    /** 判断是否有多个子问题 */
    private boolean hasSubQuestions(ConversationExecutionPlan plan) {
        return plan.getRetrievalSubQuestions() != null && plan.getRetrievalSubQuestions().size() > 1;
    }

    /** 构建子问题列表文本（编号列表格式） */
    private String buildSubQuestions(ConversationExecutionPlan plan) {
        if (!hasSubQuestions(plan)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < plan.getRetrievalSubQuestions().size(); index++) {
            builder.append(index + 1).append(". ").append(plan.getRetrievalSubQuestions().get(index)).append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 构建证据块文本。
     *
     * 每个子问题对应一个证据块，证据块内包含该子问题检索到的文档引用。
     * 引用证据会按预算逐条写入 Prompt；重复证据只复用编号，不重复占上下文。
     */
    private String buildEvidenceBlocks(RagRetrievalContext context,
                                       Set<String> renderedReferenceKeys,
                                       PromptBudget promptBudget) {
        StringBuilder builder = new StringBuilder();
        for (SubQuestionEvidence evidence : context.getSubQuestionEvidenceList()) {
            StringBuilder referenceBuilder = new StringBuilder();
            appendReferences(referenceBuilder, evidence.getReferences(), renderedReferenceKeys, promptBudget);
            builder.append(promptTemplateService.render(PromptTemplateNames.RAG_ANSWER_SUB_QUESTION_EVIDENCE, Map.of(
                "subQuestionIndex", evidence.getSubQuestionIndex(),
                "subQuestion", StrUtil.blankToDefault(evidence.getSubQuestion(), ""),
                "references", referenceBuilder.toString().trim()
            ))).append("\n\n");
        }
        return builder.toString().trim();
    }

    /**
     * 追加引用证据到 StringBuilder。
     *
     * 预算控制逻辑：
     * 1. 如果引用已渲染过（通过 uniqueKey 判断），只写入复用编号
     * 2. 如果引用未渲染过，构建完整的引用块并检查预算
     * 3. 预算不足时标记省略并停止追加
     */
    private void appendReferences(StringBuilder builder,
                                  List<SearchReference> references,
                                  Set<String> renderedReferenceKeys,
                                  PromptBudget promptBudget) {
        // 引用证据会按预算逐条写入 Prompt；重复证据只复用编号，不重复占上下文。
        if (references == null || references.isEmpty()) {
            builder.append(promptTemplateService.render(PromptTemplateNames.RAG_ANSWER_NO_EVIDENCE, Map.of())).append('\n');
            return;
        }
        promptBudget.resetSubQuestionBudget();
        boolean omitted = false;
        for (SearchReference reference : references) {
            String uniqueKey = reference.uniqueKey();
            // 如果已渲染过，只写入复用编号
            if (renderedReferenceKeys.contains(uniqueKey)) {
                String reuseLine = promptTemplateService.render(PromptTemplateNames.RAG_ANSWER_REUSE_REFERENCE, Map.of(
                    "referenceId", StrUtil.blankToDefault(reference.getReferenceId(), "")
                )) + "\n";
                if (promptBudget.tryConsume(reuseLine.length())) {
                    builder.append(reuseLine);
                }
                continue;
            }

            // WEB 类型的引用
            if ("WEB".equalsIgnoreCase(reference.getSourceType())) {
                String block = buildWebReferenceBlock(reference);
                if (promptBudget.tryConsume(block.length())) {
                    builder.append(block);
                    renderedReferenceKeys.add(uniqueKey);
                    promptBudget.markRendered(referenceSummary(reference, "已纳入 Prompt"));
                } else {
                    omitted = true;
                    promptBudget.markOmitted(referenceSummary(reference, "超出上下文预算，已省略"));
                    break;
                }
                continue;
            }
            // 文档类型的引用
            String block = buildDocumentReferenceBlock(reference);
            if (promptBudget.tryConsume(block.length())) {
                builder.append(block);
                renderedReferenceKeys.add(uniqueKey);
                promptBudget.markRendered(referenceSummary(reference, "已纳入 Prompt"));
            } else {
                omitted = true;
                promptBudget.markOmitted(referenceSummary(reference, "超出上下文预算，已省略"));
                break;
            }
        }
        // 如果有省略的证据，添加省略提示
        if (omitted) {
            builder.append(promptTemplateService.render(PromptTemplateNames.RAG_ANSWER_OMITTED_EVIDENCE, Map.of())).append('\n');
        }
    }

    /** 构建 WEB 类型的引用块 */
    private String buildWebReferenceBlock(SearchReference reference) {
        return promptTemplateService.render(PromptTemplateNames.RAG_ANSWER_WEB_REFERENCE, Map.of(
            "referenceId", StrUtil.blankToDefault(reference.getReferenceId(), ""),
            "title", StrUtil.blankToDefault(reference.getTitle(), "网页来源"),
            "url", StrUtil.blankToDefault(reference.getUrl(), "未知"),
            "snippet", trimSnippet(reference.getSnippet(), 900)
        )) + "\n\n";
    }

    /** 构建文档类型的引用块 */
    private String buildDocumentReferenceBlock(SearchReference reference) {
        return promptTemplateService.render(PromptTemplateNames.RAG_ANSWER_DOCUMENT_REFERENCE, Map.of(
            "referenceId", StrUtil.blankToDefault(reference.getReferenceId(), ""),
            "documentName", StrUtil.blankToDefault(
                StrUtil.blankToDefault(reference.getDocumentName(), reference.getTitle()),
                "文档来源"
            ),
            "sectionPath", StrUtil.blankToDefault(reference.getSectionPath(), "未识别"),
            "snippet", trimSnippet(reference.getSnippet(), 1100)
        )) + "\n\n";
    }

    /** 裁剪摘要文本到指定长度 */
    private String trimSnippet(String snippet, int maxChars) {
        if (StrUtil.isBlank(snippet)) {
            return "";
        }

        return snippet.length() <= maxChars ? snippet : snippet.substring(0, maxChars) + "...";
    }

    /** 构建引用摘要（用于日志和调试） */
    private String referenceSummary(SearchReference reference, String suffix) {
        if (reference == null) {
            return suffix;
        }
        String title = StrUtil.blankToDefault(reference.getDocumentName(), reference.getTitle());
        String path = StrUtil.blankToDefault(reference.getSectionPath(), reference.getUrl());
        String refId = StrUtil.blankToDefault(reference.getReferenceId(), "-");
        return "[" + refId + "] " + title + (StrUtil.isBlank(path) ? "" : " | " + path) + " | " + suffix;
    }

    /**
     * 【Prompt 预算控制器】
     *
     * 内部类，用于控制证据文本的总长度。
     * 两个维度的预算：
     * - totalBudget：所有证据的总字符数上限
     * - perSubQuestionBudget：每个子问题的证据字符数上限
     *
     * 设计模式：值对象模式，预算状态通过 tryConsume 方法管理。
     */
    private static final class PromptBudget {

        /** 总预算（字符数） */
        private final int totalBudget;
        /** 每个子问题的预算（字符数） */
        private final int perSubQuestionBudget;
        /** 剩余总预算 */
        private int remainingTotal;
        /** 剩余子问题预算 */
        private int remainingSubQuestion;
        /** 已渲染的引用数量 */
        private int renderedReferenceCount;
        /** 被省略的引用数量 */
        private int omittedReferenceCount;
        /** 已渲染引用的详情列表 */
        private final List<String> renderedReferenceDetails = new ArrayList<>();
        /** 被省略引用的详情列表 */
        private final List<String> omittedReferenceDetails = new ArrayList<>();

        private PromptBudget(int totalBudget, int perSubQuestionBudget) {
            this.totalBudget = totalBudget;
            this.perSubQuestionBudget = perSubQuestionBudget;
            this.remainingTotal = totalBudget;
            this.remainingSubQuestion = perSubQuestionBudget;
        }

        /** 重置子问题预算（每个子问题开始时调用） */
        private void resetSubQuestionBudget() {
            this.remainingSubQuestion = perSubQuestionBudget;
        }

        /**
         * 尝试消费指定大小的预算。
         *
         * @param size 要消费的字符数
         * @return true 表示预算充足，已消费；false 表示预算不足
         */
        private boolean tryConsume(int size) {
            if (totalBudget <= 0 || perSubQuestionBudget <= 0) {
                return false;
            }
            if (size > remainingTotal || size > remainingSubQuestion) {
                return false;
            }
            remainingTotal -= size;
            remainingSubQuestion -= size;
            return true;
        }

        /** 标记一个引用已渲染 */
        private void markRendered(String detail) {
            renderedReferenceCount++;
            if (StrUtil.isNotBlank(detail)) {
                renderedReferenceDetails.add(detail);
            }
        }

        /** 标记一个引用被省略 */
        private void markOmitted(String detail) {
            omittedReferenceCount++;
            if (StrUtil.isNotBlank(detail)) {
                omittedReferenceDetails.add(detail);
            }
        }
    }
}
