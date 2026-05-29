package ai.knowhub.chat.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.model.debug.ChatModelUsageTrace;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 【可观测的聊天模型服务】
 *
 * 对 Spring AI 的 ChatModel 进行封装，增加了"可观测性"（Observability）功能。
 * 每次调用大模型时，都会自动记录：
 *   1. 调用阶段名称（stageName）：如 "query_rewrite"、"recommendation"、"summary"
 *   2. Token 用量：输入 token 数、输出 token 数、总 token 数
 *   3. 耗时：从请求发起到响应完成的毫秒数
 *   4. 费用估算：根据模型类型和 token 数量估算本次调用的成本
 *   5. 调用状态：COMPLETED（成功）或 FAILED（失败）
 *
 * 设计模式：装饰器模式（Decorator Pattern）。
 * 在不修改 ChatModel 原有逻辑的情况下，为其添加了追踪和日志功能。
 *
 * 两种调用方式：
 * - callText(): 同步调用，等待模型完整响应后返回文本
 * - streamText(): 流式调用，返回 Flux<String> 逐片段推送
 *
 * 使用场景：
 * - RecommendationService 用 callText() 生成推荐追问
 * - PersistentConversationMemoryService 用 callText() 生成会话摘要
 * - ChatPreparationOrchestrator 用 callText() 做查询改写
 *
 * 费用估算规则（硬编码）：
 * - qwen-plus: 输入 0.004 元/千 token，输出 0.012 元/千 token
 * - deepseek: 输入 0.002 元/千 token，输出 0.008 元/千 token
 * - 其他模型: 暂不估算
 */
@Slf4j
@Service
public class ObservedChatModelService {

    /** Spring AI 的聊天模型接口，由 Spring 自动注入具体实现（如 DashScope、OpenAI 等） */
    private final ChatModel chatModel;

    /**
     * 构造函数。
     *
     * @param chatModel Spring AI 的聊天模型实例
     */
    public ObservedChatModelService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 同步调用大模型生成文本（使用默认参数）。
     *
     * @param stageName     调用阶段名称（如 "recommendation"、"summary"）
     * @param systemPrompt  系统提示词（可以为 null）
     * @param userPrompt    用户提示词
     * @param traceRecorder 追踪记录器（可以为 null，为 null 时不记录追踪）
     * @return 模型生成的文本内容
     */
    public String callText(String stageName,
                           String systemPrompt,
                           String userPrompt,
                           ConversationTraceRecorder traceRecorder) {
        return callText(stageName, systemPrompt, userPrompt, null, traceRecorder);
    }

    /**
     * 同步调用大模型生成文本（可自定义调用参数）。
     *
     * @param stageName     调用阶段名称
     * @param systemPrompt  系统提示词
     * @param userPrompt    用户提示词
     * @param callOptions   自定义调用参数（如 temperature、model 等，可以为 null）
     * @param traceRecorder 追踪记录器
     * @return 模型生成的文本内容
     */
    public String callText(String stageName,
                           String systemPrompt,
                           String userPrompt,
                           ChatOptions callOptions,
                           ConversationTraceRecorder traceRecorder) {
        long startTime = System.currentTimeMillis();
        String provider = resolveProvider();
        String model = resolveModel();
        try {
            // 合并默认参数和自定义参数
            ChatOptions effectiveOptions = mergeOptions(callOptions);
            logStageCallOptions(stageName, provider, model, effectiveOptions);
            // 调用模型
            ChatResponse response = chatModel.call(buildPrompt(systemPrompt, userPrompt, effectiveOptions));
            // 提取响应文本
            String responseText = response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? ""
                : StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
            // 构建并记录 token 用量追踪
            ChatModelUsageTrace usageTrace = buildUsageTrace(
                stageName,
                provider,
                model,
                response == null ? null : response.getMetadata(),
                System.currentTimeMillis() - startTime,
                "COMPLETED",
                systemPrompt,
                userPrompt,
                responseText
            );
            appendUsage(traceRecorder, usageTrace);
            return responseText;
        }
        catch (RuntimeException exception) {
            // 调用失败时也记录追踪（状态为 FAILED）
            appendUsage(traceRecorder, ChatModelUsageTrace.builder()
                .stageName(stageName)
                .provider(provider)
                .model(model)
                .durationMs(System.currentTimeMillis() - startTime)
                .promptTokens(estimateTokens(systemPrompt) + estimateTokens(userPrompt))
                .status("FAILED")
                .build());
            throw exception;
        }
    }

    /**
     * 流式调用大模型生成文本。
     *
     * 与 callText 的区别：
     * - callText 等待模型完整响应后才返回
     * - streamText 返回一个 Flux，模型每生成一个片段就立即推送
     * - 流式调用适合 SSE 场景，前端可以逐步显示回答内容
     *
     * @param stageName     调用阶段名称
     * @param systemPrompt  系统提示词
     * @param userPrompt    用户提示词
     * @param traceRecorder 追踪记录器
     * @return 文本片段的响应式流
     */
    public Flux<String> streamText(String stageName,
                                   String systemPrompt,
                                   String userPrompt,
                                   ConversationTraceRecorder traceRecorder) {
        String provider = resolveProvider();
        String model = resolveModel();
        long startTime = System.currentTimeMillis();
        // 使用 AtomicReference 收集最后一次响应的元数据
        AtomicReference<ChatResponseMetadata> metadataRef = new AtomicReference<>();
        AtomicLong durationRef = new AtomicLong(0L);
        // 使用 StringBuilder 收集完整的输出文本（用于 token 估算）
        StringBuilder outputBuilder = new StringBuilder();

        return chatModel.stream(buildPrompt(systemPrompt, userPrompt))
            .map(response -> {
                // 收集元数据和输出文本
                if (response != null && response.getMetadata() != null) {
                    metadataRef.set(response.getMetadata());
                }
                if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                    return "";
                }
                return StrUtil.blankToDefault(response.getResult().getOutput().getText(), "");
            })
            .filter(StrUtil::isNotBlank)
            .doOnNext(outputBuilder::append)
            .doOnComplete(() -> {
                // 流完成时记录追踪
                long durationMs = System.currentTimeMillis() - startTime;
                durationRef.set(durationMs);
                appendUsage(traceRecorder, buildUsageTrace(stageName, provider, model, metadataRef.get(), durationMs, "COMPLETED", systemPrompt, userPrompt, outputBuilder.toString()));
            })
            .doOnError(error -> appendUsage(traceRecorder, ChatModelUsageTrace.builder()
                .stageName(stageName)
                .provider(provider)
                .model(model)
                .promptTokens(estimateTokens(systemPrompt) + estimateTokens(userPrompt))
                .completionTokens(estimateTokens(outputBuilder.toString()))
                .totalTokens(estimateTokens(systemPrompt) + estimateTokens(userPrompt) + estimateTokens(outputBuilder.toString()))
                .estimatedCost(estimateCost(model, estimateTokens(systemPrompt) + estimateTokens(userPrompt), estimateTokens(outputBuilder.toString())))
                .durationMs(durationRef.get() > 0 ? durationRef.get() : System.currentTimeMillis() - startTime)
                .status("FAILED")
                .build()));
    }

    /**
     * 构建 Prompt 对象（使用默认参数）。
     */
    private Prompt buildPrompt(String systemPrompt, String userPrompt) {
        return buildPrompt(systemPrompt, userPrompt, null);
    }

    /**
     * 构建 Prompt 对象。
     *
     * @param systemPrompt 系统提示词（可以为空，为空时不添加 SystemMessage）
     * @param userPrompt   用户提示词
     * @param callOptions  调用参数（可以为 null）
     * @return Spring AI 的 Prompt 对象
     */
    private Prompt buildPrompt(String systemPrompt, String userPrompt, ChatOptions callOptions) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(StrUtil.blankToDefault(userPrompt, "")));
        ChatOptions mergedOptions = mergeOptions(callOptions);
        return mergedOptions == null ? new Prompt(messages) : new Prompt(messages, mergedOptions);
    }

    /**
     * 合并默认参数和自定义调用参数。
     *
     * 如果自定义参数中的某个字段为 null，则使用默认参数的值。
     * 支持 OpenAiChatOptions 的所有字段：model、temperature、topP、
     * reasoningEffort、verbosity、extraBody 等。
     *
     * @param callOptions 自定义调用参数
     * @return 合并后的调用参数
     */
    private ChatOptions mergeOptions(ChatOptions callOptions) {
        if (callOptions == null) {
            return null;
        }
        ChatOptions defaultOptions = chatModel.getDefaultOptions();
        if (defaultOptions instanceof OpenAiChatOptions defaultOpenAi
            && callOptions instanceof OpenAiChatOptions overrideOpenAi) {
            OpenAiChatOptions merged = defaultOpenAi.copy();
            if (overrideOpenAi.getModel() != null) {
                merged.setModel(overrideOpenAi.getModel());
            }
            if (overrideOpenAi.getTemperature() != null) {
                merged.setTemperature(overrideOpenAi.getTemperature());
            }
            if (overrideOpenAi.getTopP() != null) {
                merged.setTopP(overrideOpenAi.getTopP());
            }
            if (overrideOpenAi.getReasoningEffort() != null) {
                merged.setReasoningEffort(overrideOpenAi.getReasoningEffort());
            }
            if (overrideOpenAi.getVerbosity() != null) {
                merged.setVerbosity(overrideOpenAi.getVerbosity());
            }
            if (overrideOpenAi.getExtraBody() != null && !overrideOpenAi.getExtraBody().isEmpty()) {
                Map<String, Object> mergedExtraBody = new LinkedHashMap<>();
                if (merged.getExtraBody() != null && !merged.getExtraBody().isEmpty()) {
                    mergedExtraBody.putAll(merged.getExtraBody());
                }
                mergedExtraBody.putAll(overrideOpenAi.getExtraBody());
                merged.setExtraBody(mergedExtraBody);
            }
            return merged;
        }
        return callOptions;
    }

    /**
     * 记录模型调用参数到日志。
     */
    private void logStageCallOptions(String stageName,
                                     String provider,
                                     String fallbackModel,
                                     ChatOptions effectiveOptions) {
        if (!(effectiveOptions instanceof OpenAiChatOptions openAiOptions)) {
            if (effectiveOptions != null) {
                log.info("模型调用参数: stage={}, provider={}, model={}, optionsClass={}",
                    StrUtil.blankToDefault(stageName, ""),
                    provider,
                    fallbackModel,
                    effectiveOptions.getClass().getName());
            }
            return;
        }
        log.info("模型调用参数: stage={}, provider={}, model={}, temperature={}, topP={}, reasoningEffort={}, verbosity={}, extraBody={}",
            StrUtil.blankToDefault(stageName, ""),
            provider,
            StrUtil.blankToDefault(openAiOptions.getModel(), fallbackModel),
            openAiOptions.getTemperature(),
            openAiOptions.getTopP(),
            StrUtil.blankToDefault(openAiOptions.getReasoningEffort(), ""),
            StrUtil.blankToDefault(openAiOptions.getVerbosity(), ""),
            openAiOptions.getExtraBody() == null ? Map.of() : openAiOptions.getExtraBody());
    }

    /**
     * 将模型调用追踪记录追加到追踪记录器。
     */
    private void appendUsage(ConversationTraceRecorder traceRecorder, ChatModelUsageTrace trace) {
        if (traceRecorder != null && trace != null) {
            traceRecorder.addModelUsageTrace(trace);
        }
    }

    /**
     * 构建模型调用追踪记录。
     *
     * 从 ChatResponseMetadata 中提取真实的 token 用量，
     * 如果元数据中没有则使用估算值。
     */
    private ChatModelUsageTrace buildUsageTrace(String stageName,
                                                String provider,
                                                String model,
                                                ChatResponseMetadata metadata,
                                                long durationMs,
                                                String status,
                                                String systemPrompt,
                                                String userPrompt,
                                                String responseText) {
        Usage usage = metadata == null ? null : metadata.getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        Integer totalTokens = usage == null ? null : usage.getTotalTokens();
        // 如果真实 token 数不可用，使用估算值
        if (promptTokens == null || promptTokens <= 0) {
            promptTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
        }
        if (completionTokens == null || completionTokens <= 0) {
            completionTokens = estimateTokens(responseText);
        }
        if (totalTokens == null || totalTokens <= 0) {
            totalTokens = (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens);
        }
        return ChatModelUsageTrace.builder()
            .stageName(stageName)
            .provider(provider)
            .model(StrUtil.blankToDefault(metadata == null ? model : metadata.getModel(), model))
            .promptTokens(promptTokens)
            .completionTokens(completionTokens)
            .totalTokens(totalTokens)
            .estimatedCost(estimateCost(model, promptTokens, completionTokens))
            .durationMs(durationMs)
            .status(status)
            .build();
    }

    /**
     * 估算文本的 token 数量。
     *
     * 使用简单的启发式规则：每个 token 约 4 个字符（中英文混合场景）。
     * 这是一个粗略估算，真实 token 数需要通过 tokenizer 计算。
     *
     * @param content 文本内容
     * @return 估算的 token 数量
     */
    private Integer estimateTokens(String content) {
        if (StrUtil.isBlank(content)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.trim().length() / 4.0));
    }

    /**
     * 推断当前使用的模型提供商。
     *
     * 通过 ChatModel 实现类的类名来判断：
     * - 包含 "deepseek" -> "deepseek"
     * - 包含 "openai" -> "openai-compatible"
     * - 包含 "ollama" -> "ollama"
     * - 其他 -> "unknown"
     */
    private String resolveProvider() {
        String className = chatModel.getClass().getName().toLowerCase();
        if (className.contains("deepseek")) {
            return "deepseek";
        }
        if (className.contains("openai")) {
            return "openai-compatible";
        }
        if (className.contains("ollama")) {
            return "ollama";
        }
        return "unknown";
    }

    /**
     * 获取当前配置的模型名称。
     */
    private String resolveModel() {
        ChatOptions options = chatModel.getDefaultOptions();
        return options == null ? "" : StrUtil.blankToDefault(options.getModel(), "");
    }

    /**
     * 估算本次模型调用的费用（人民币，元）。
     *
     * 根据模型类型和 token 数量计算：
     * - qwen-plus: 输入 0.004 元/千 token，输出 0.012 元/千 token
     * - deepseek: 输入 0.002 元/千 token，输出 0.008 元/千 token
     * - 其他模型: 返回 null（无法估算）
     *
     * @param model           模型名称
     * @param promptTokens    输入 token 数
     * @param completionTokens 输出 token 数
     * @return 估算费用（元），无法估算时返回 null
     */
    private Double estimateCost(String model, Integer promptTokens, Integer completionTokens) {
        if ((promptTokens == null || promptTokens <= 0) && (completionTokens == null || completionTokens <= 0)) {
            return null;
        }
        String normalizedModel = StrUtil.blankToDefault(model, "").toLowerCase();
        double promptRatePer1k;
        double completionRatePer1k;
        if (normalizedModel.contains("qwen-plus")) {
            promptRatePer1k = 0.004;
            completionRatePer1k = 0.012;
        }
        else if (normalizedModel.contains("deepseek")) {
            promptRatePer1k = 0.002;
            completionRatePer1k = 0.008;
        }
        else {
            promptRatePer1k = 0.0;
            completionRatePer1k = 0.0;
        }
        double promptCost = (promptTokens == null ? 0D : promptTokens / 1000D) * promptRatePer1k;
        double completionCost = (completionTokens == null ? 0D : completionTokens / 1000D) * completionRatePer1k;
        double total = promptCost + completionCost;
        return total > 0D ? total : null;
    }
}
