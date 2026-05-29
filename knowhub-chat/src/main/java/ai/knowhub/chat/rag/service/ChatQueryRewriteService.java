package ai.knowhub.chat.rag.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.RagRewriteResult;
import ai.knowhub.chat.service.ConversationTraceRecorder;
import ai.knowhub.chat.service.ObservedChatModelService;
import ai.knowhub.prompt.PromptTemplateNames;
import ai.knowhub.prompt.PromptTemplateService;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 【聊天查询改写服务 — RAG 流水线的"问题翻译官"】
 *
 * 这个类负责在 RAG 检索之前，把用户的原始问题改写成更适合检索引擎理解的表达。
 *
 * 改写的目标不是改变用户意图，而是：
 * 1. 把口语化问题变成更规范的检索查询（例如"那个咋配" -> "如何配置 XX"）
 * 2. 为短追问补充历史上下文（例如"为什么" -> "为什么 XX 服务需要配置 YY"）
 * 3. 把复合问题拆成多个子问题（例如"A 和 B 分别是什么" -> ["A 是什么", "B 是什么"]）
 *
 * 设计模式：
 * - 策略模式：LLM 改写失败时自动回退到基于规则的保守改写
 * - 模板方法模式：通过 PromptTemplateService 渲染改写提示词
 *
 * 在 RAG 流水线中的位置：
 * 用户问题 -> 【本类：查询改写】-> 改写后的问题/子问题 -> 检索引擎
 */
@Slf4j
@Service
public class ChatQueryRewriteService {

    /** 匹配编号式多问题模式，如 "1) xxx 2) xxx" 或 "a) xxx b) xxx" */
    private static final Pattern NUMBERED_MULTI_QUESTION_PATTERN = Pattern.compile("(^|\\s)(\\d+[)\\.、]|[A-Za-z][)])");
    /** 匹配多行文本（用于判断是否为多问题） */
    private static final Pattern MULTI_LINE_PATTERN = Pattern.compile("\\n+");

    /** 带观测能力的聊天模型服务，用于调用 LLM 进行问题改写 */
    private final ObservedChatModelService observedChatModelService;
    /** JSON 解析器，用于解析 LLM 返回的改写结果 */
    private final ObjectMapper objectMapper;
    /** RAG 配置属性 */
    private final ChatRagProperties properties;
    /** 提示词模板服务，用于渲染改写提示词 */
    private final PromptTemplateService promptTemplateService;

    /**
     * 构造函数，通过 Spring 依赖注入所有需要的服务。
     */
    public ChatQueryRewriteService(ObservedChatModelService observedChatModelService,
                                   ObjectMapper objectMapper,
                                   ChatRagProperties properties,
                                   PromptTemplateService promptTemplateService) {
        this.observedChatModelService = observedChatModelService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 改写问题（不带追踪记录器的简化版本）。
     *
     * @param question       用户原始问题
     * @param historySummary 历史对话摘要
     * @return 改写结果，包含改写后的问题和子问题列表
     */
    public RagRewriteResult rewrite(String question, String historySummary) {
        return rewrite(question, historySummary, null);
    }

    /**
     * 改写问题（完整版本，支持追踪记录）。
     *
     * 改写流程：
     * 1. 标准化问题文本
     * 2. 判断是否需要改写（短问题、有多问题特征、有历史上下文）
     * 3. 如果需要改写，调用 LLM 进行智能改写
     * 4. 如果 LLM 改写失败或结果不可用，回退到基于规则的保守改写
     *
     * @param question       用户原始问题
     * @param historySummary 历史对话摘要（用于补充上下文）
     * @param traceRecorder  追踪记录器（可选，用于记录改写过程）
     * @return 改写结果，包含改写后的问题和子问题列表
     */
    public RagRewriteResult rewrite(String question,
                                    String historySummary,
                                    ConversationTraceRecorder traceRecorder) {
        // 改写服务的目标不是改变用户意图，而是把口语化问题变成更适合检索的表达。
        String normalizedQuestion = StrUtil.trim(question);
        if (StrUtil.isBlank(normalizedQuestion)) {
            return new RagRewriteResult("", List.of());
        }
        // 如果改写功能未关闭，或者问题不需要改写，直接返回原始问题
        if (!properties.isRewriteEnabled() || !needsRewrite(normalizedQuestion, historySummary)) {
            RagRewriteResult fallback = fallback(normalizedQuestion);
            log.info("RAG 改写跳过: question='{}', rewritten='{}', subQuestions={}",
                normalizedQuestion,
                fallback.getRewrittenQuestion(),
                fallback.getSubQuestions());
            return fallback;
        }
        try {
            // 渲染改写提示词模板
            String prompt = promptTemplateService.render(PromptTemplateNames.CHAT_QUERY_REWRITE, Map.of(
                "history", StrUtil.isNotBlank(historySummary) ? historySummary : "无历史上下文",
                "question", normalizedQuestion
            ));
            // 调用 LLM 进行问题改写
            String raw = observedChatModelService.callText("rewrite", null, prompt, buildRewriteCallOptions(), traceRecorder);
            // 解析并规范化 LLM 返回的改写结果
            RagRewriteResult parsed = normalizeRewriteResult(normalizedQuestion, parse(raw));
            if (parsed != null && StrUtil.isNotBlank(parsed.getRewrittenQuestion())) {
                parsed.setRawModelOutput(raw);
                log.info("RAG 改写完成: question='{}', rewritten='{}', subQuestions={}",
                    normalizedQuestion,
                    parsed.getRewrittenQuestion(),
                    parsed.getSubQuestions());
                return parsed;
            }
            log.warn("RAG 改写结果不可用，回退到规则改写: question='{}', raw='{}'",
                normalizedQuestion,
                StrUtil.blankToDefault(raw, ""));
        }
        catch (Exception exception) {
            log.warn("RAG 改写失败，回退到规则改写: question='{}', message={}",
                normalizedQuestion,
                exception.getMessage());
        }
        return fallback(normalizedQuestion);
    }

    /**
     * 构建 LLM 改写调用的模型参数（temperature、topP、thinking 等）。
     *
     * 如果配置了 rewriteOptions 且 enabled=true，则使用自定义参数；
     * 否则返回 null，使用模型默认参数。
     */
    private ChatOptions buildRewriteCallOptions() {
        ChatRagProperties.RewriteOptionsProperties rewriteOptions = properties.getRewriteOptions();
        if (rewriteOptions == null || !rewriteOptions.isEnabled()) {
            log.info("RAG 改写模型参数: overrideEnabled=false, useDefaultModelOptions=true");
            return null;
        }
        log.info("RAG 改写模型参数: overrideEnabled=true, temperature={}, topP={}, thinking={}",
            rewriteOptions.getTemperature(),
            rewriteOptions.getTopP(),
            rewriteOptions.getThinking());
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (rewriteOptions.getTemperature() != null) {
            builder.temperature(rewriteOptions.getTemperature());
        }
        if (rewriteOptions.getTopP() != null) {
            builder.topP(rewriteOptions.getTopP());
        }
        if (rewriteOptions.getThinking() != null) {
            builder.extraBody(Map.of("thinking", rewriteOptions.getThinking()));
        }
        return builder.build();
    }

    /**
     * 规则改写兜底：LLM 改写不可用时的保守策略。
     *
     * 策略：不强行扩写问题，只在明确是多问题时做简单拆分。
     * 例如 "A 是什么？B 怎么用？" -> ["A 是什么", "B 怎么用"]
     */
    private RagRewriteResult fallback(String normalizedQuestion) {
        // 模型改写不可用时走保守规则：不强行扩写，只在明确多问题时做简单拆分。
        if (!looksLikeExplicitMultiQuestion(normalizedQuestion)) {
            return new RagRewriteResult(normalizedQuestion, List.of(normalizedQuestion));
        }
        return new RagRewriteResult(normalizedQuestion, ruleBasedSplit(normalizedQuestion));
    }

    /**
     * 判断问题是否需要改写。
     *
     * 需要改写的条件：
     * - 没有历史上下文时：问题很短（<8字）或多问题
     * - 有历史上下文时：问题较短（<18字）或多问题
     *
     * 短问题通常缺少主语或上下文，改写可以补充语义。
     */
    private boolean needsRewrite(String question, String historySummary) {
        if (StrUtil.isBlank(historySummary)) {
            return question.length() < 8 || looksLikeExplicitMultiQuestion(question);
        }
        return question.length() < 18 || looksLikeExplicitMultiQuestion(question);
    }

    /**
     * 判断问题是否"看起来像"明确的多问题。
     *
     * 判断依据（满足任一即可）：
     * - 包含 2 个以上的问号
     * - 包含分号（中英文）
     * - 包含多行非空文本
     * - 包含编号模式（如 "1. xxx 2. xxx"）
     * - 包含"分别"这个词
     */
    private boolean looksLikeExplicitMultiQuestion(String question) {
        String normalized = StrUtil.blankToDefault(question, "").trim();
        if (normalized.isBlank()) {
            return false;
        }
        // 统计问号数量（中英文都算）
        long questionMarkCount = normalized.chars().filter(ch -> ch == '?' || ch == '？').count();
        if (questionMarkCount >= 2) {
            return true;
        }
        // 包含分号
        if (normalized.contains("；") || normalized.contains(";")) {
            return true;
        }
        // 包含多行非空文本
        if (MULTI_LINE_PATTERN.matcher(normalized).find()) {
            long nonBlankLineCount = Arrays.stream(normalized.split("\\n+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .count();
            if (nonBlankLineCount >= 2) {
                return true;
            }
        }
        // 包含编号模式
        if (NUMBERED_MULTI_QUESTION_PATTERN.matcher(normalized).find()) {
            return true;
        }
        // 包含"分别"
        return normalized.contains("分别");
    }

    /**
     * 规范化 LLM 返回的改写结果。
     *
     * LLM 可能会过度拆分子问题，这里会做收敛：
     * 1. 如果原始问题不像多问题，即使 LLM 建议拆分也不拆
     * 2. 子问题去重、去空
     * 3. 子问题数量不超过 maxSubQuestions 配置
     */
    private RagRewriteResult normalizeRewriteResult(String originalQuestion,
                                                    ParsedRewritePayload parsed) {
        // LLM 返回的 JSON 还要再做收敛，避免它把一个普通问题过度拆分成太多子问题。
        if (parsed == null) {
            return null;
        }
        String rewrite = StrUtil.blankToDefault(parsed.rewrite(), originalQuestion).trim();
        if (StrUtil.isBlank(rewrite)) {
            return null;
        }
        List<String> subQuestions = parsed.subQuestions() == null
            ? new ArrayList<>()
            : parsed.subQuestions().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        boolean explicitMultiQuestion = looksLikeExplicitMultiQuestion(originalQuestion);
        boolean shouldSplit = Boolean.TRUE.equals(parsed.shouldSplit());
        // 如果 LLM 建议拆分但原始问题不像多问题，拒绝拆分
        if (!shouldSplit || !explicitMultiQuestion) {
            if (shouldSplit && !explicitMultiQuestion && subQuestions.size() > 1) {
                log.info("RAG 改写子问题收敛: question='{}', rewrite='{}', originalSubQuestionCount={}, reason='llm-split-rejected-by-conservative-structure-check'",
                    originalQuestion,
                    rewrite,
                    subQuestions.size());
            }
            subQuestions = List.of(rewrite);
        }
        else if (subQuestions.isEmpty()) {
            // LLM 建议拆分但没给出子问题，用规则拆分兜底
            List<String> fallbackSplit = ruleBasedSplit(originalQuestion);
            if (fallbackSplit.size() > 1) {
                subQuestions = fallbackSplit;
            }
            else {
                subQuestions = List.of(rewrite);
            }
        }
        // 如果只有一个子问题且与改写结果不同，用改写结果替换
        if (subQuestions.size() == 1 && !StrUtil.equals(subQuestions.get(0), rewrite) && !shouldSplit) {
            subQuestions = List.of(rewrite);
        }
        // 限制子问题数量
        if (subQuestions.size() > properties.getMaxSubQuestions()) {
            subQuestions = subQuestions.subList(0, properties.getMaxSubQuestions());
        }
        return new RagRewriteResult(rewrite, subQuestions);
    }

    /**
     * 解析 LLM 返回的 JSON 改写结果。
     *
     * 期望的 JSON 格式：
     * {
     *   "rewrite": "改写后的问题",
     *   "should_split": true/false,
     *   "sub_questions": ["子问题1", "子问题2"]
     * }
     */
    private ParsedRewritePayload parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw.trim());
            String rewrite = root.path("rewrite").asText("").trim();
            if (StrUtil.isBlank(rewrite)) {
                return null;
            }
            Boolean shouldSplit = root.has("should_split") && !root.get("should_split").isNull()
                ? root.path("should_split").asBoolean(false)
                : null;
            List<String> parsedSubQuestions = new ArrayList<>();
            JsonNode subQuestionNode = root.path("sub_questions");
            if (subQuestionNode.isArray()) {
                subQuestionNode.forEach(item -> {
                    String text = item.asText("").trim();
                    if (StrUtil.isNotBlank(text)) {
                        parsedSubQuestions.add(text);
                    }
                });
            }
            return new ParsedRewritePayload(rewrite, shouldSplit, parsedSubQuestions);
        }
        catch (Exception exception) {
            log.warn("解析问题改写结果失败，raw={}", raw, exception);
            return null;
        }
    }

    /**
     * 基于规则的问题拆分。
     *
     * 拆分依据：问号、分号、换行符。
     * 例如 "A 是什么？B 怎么用？" -> ["A 是什么", "B 怎么用"]
     */
    private List<String> ruleBasedSplit(String question) {
        List<String> result = Arrays.stream(question.split("[?？；;\n]+"))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .limit(properties.getMaxSubQuestions())
            .toList();
        if (result.isEmpty()) {
            return List.of(question);
        }
        // 使用 LinkedHashSet 去重并保持顺序
        return new ArrayList<>(new LinkedHashSet<>(result));
    }

    /**
     * LLM 改写结果的内部数据结构（record 类型，不可变）。
     *
     * @param rewrite      改写后的主问题
     * @param shouldSplit  LLM 是否建议拆分
     * @param subQuestions LLM 给出的子问题列表
     */
    private record ParsedRewritePayload(String rewrite, Boolean shouldSplit, List<String> subQuestions) {
    }
}
