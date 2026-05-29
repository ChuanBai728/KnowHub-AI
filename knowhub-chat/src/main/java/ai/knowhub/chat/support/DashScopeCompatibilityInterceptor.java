package ai.knowhub.chat.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 【DashScope 兼容性拦截器】
 *
 * 作用：在调用 AI 模型前后进行兼容性处理，解决 DashScope（阿里云灵积）API
 * 与标准 OpenAI API 之间的差异问题。
 *
 * 背景说明：
 * Spring AI Alibaba 使用 OpenAI 兼容的接口调用 DashScope 模型，
 * 但 DashScope 的实现与标准 OpenAI API 存在一些差异，需要在调用前/后进行适配。
 *
 * 主要兼容性处理：
 * 1. 并行工具调用（parallelToolCalls）：DashScope 不支持并行工具调用，需要关闭
 * 2. 流式使用标记（streamUsage/streamOptions）：DashScope 的流式实现与 OpenAI 不同
 * 3. 碎片化工具调用合并：DashScope 可能将一个工具调用拆分为多个片段返回
 *
 * 所属架构位置：属于 Spring AI Alibaba 的拦截器层（Interceptor Layer），
 * 通过 ModelInterceptor 接口实现，在模型调用前后执行兼容性处理。
 *
 * 设计模式说明：
 * 1. 「拦截器模式（Interceptor Pattern）」—— 在模型调用前后插入兼容性处理逻辑。
 * 2. 「策略模式（Strategy Pattern）」—— 根据不同的提供商（provider）执行不同的兼容策略。
 * 3. 「装饰器模式（Decorator Pattern）」—— 包装原始的模型请求和响应。
 *
 * @author knowhub
 */
@Slf4j
@Component
public class DashScopeCompatibilityInterceptor extends ModelInterceptor {

    /**
     * OpenAI 兼容接口的基础 URL
     * 用于判断当前使用的是哪个提供商（DashScope、SiliconFlow 等）。
     */
    private final String openAiBaseUrl;

    public DashScopeCompatibilityInterceptor(@Value("${spring.ai.openai.base-url:}") String openAiBaseUrl) {
        this.openAiBaseUrl = openAiBaseUrl;
    }

    /**
     * 拦截模型调用，执行兼容性处理
     *
     * 处理流程：
     * 1. 检查请求选项是否为 OpenAiChatOptions 类型
     * 2. 如果是，复制选项并调整不兼容的参数
     * 3. 调用下游处理器执行实际的模型调用
     * 4. 对响应进行归一化处理（如合并碎片化工具调用）
     *
     * @param request 模型请求对象，包含消息、选项、工具定义等
     * @param handler 下游模型调用处理器
     * @return 处理后的模型响应
     */
    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 生成本次调用的短追踪 ID，用于日志关联
        String callId = UUID.randomUUID().toString().substring(0, 8);

        // 如果请求选项不是 OpenAiChatOptions 类型，直接透传
        if (!(request.getOptions() instanceof OpenAiChatOptions options)) {
            String provider = resolveProvider(openAiBaseUrl);
            logModelRequest(callId, provider, null, request, false, false);
            return wrapModelResponse(callId, provider, null, request, handler.call(request));
        }

        // 复制选项，避免修改原始对象（不可变原则）
        OpenAiChatOptions compatibleOptions = options.copy();

        // 检测并标记需要调整的兼容性问题
        boolean adjustedParallelToolCalls = Boolean.TRUE.equals(compatibleOptions.getParallelToolCalls());
        boolean adjustedStreamUsage = Boolean.TRUE.equals(compatibleOptions.getStreamUsage())
            || compatibleOptions.getStreamOptions() != null;

        // 兼容性修正1：关闭并行工具调用（DashScope 不支持）
        if (adjustedParallelToolCalls) {
            compatibleOptions.setParallelToolCalls(Boolean.FALSE);
        }

        // 兼容性修正2：移除流式选项（DashScope 的流式实现与 OpenAI 不同）
        if (adjustedStreamUsage) {
            compatibleOptions.setStreamOptions(null);
        }

        log.debug(
            "DashScope请求兼容检查: model={}, parallelToolCalls={}{}{}, tools={}, messages={}",
            compatibleOptions.getModel(),
            compatibleOptions.getParallelToolCalls(),
            adjustedParallelToolCalls ? " (was true)" : "",
            adjustedStreamUsage ? ", streamOptions removed" : "",
            summarizeTools(request.getTools()),
            summarizeMessages(request.getMessages())
        );

        // 使用修正后的选项构建新的请求
        ModelRequest compatibleRequest = ModelRequest.builder(request)
            .options(compatibleOptions)
            .build();
        String provider = resolveProvider(openAiBaseUrl);
        logModelRequest(callId, provider, compatibleOptions, compatibleRequest, adjustedParallelToolCalls, adjustedStreamUsage);

        try {
            // 调用下游处理器，并对响应进行归一化处理
            return wrapModelResponse(callId, provider, compatibleOptions, compatibleRequest, handler.call(compatibleRequest));
        }
        catch (RuntimeException exception) {
            log.error(
                "模型调用异常[{}]: provider={}, model={}, tools={}, messages={}, error={}",
                callId,
                provider,
                compatibleOptions.getModel(),
                summarizeTools(compatibleRequest.getTools()),
                summarizeMessages(compatibleRequest.getMessages()),
                exception.getMessage(),
                exception
            );
            throw exception;
        }
    }

    @Override
    public String getName() {
        return "dashscope_compatibility_interceptor";
    }

    /**
     * 记录模型请求日志
     */
    private void logModelRequest(String callId,
                                 String provider,
                                 OpenAiChatOptions options,
                                 ModelRequest request,
                                 boolean adjustedParallelToolCalls,
                                 boolean adjustedStreamUsage) {
        log.info(
            "模型请求[{}]: provider={}, baseUrl={}, optionsClass={}, model={}, parallelToolCalls={}{}{}, tools={}, messageCount={}, tailMessages={}, lastAssistant={}, lastTool={}, messages={}",
            callId,
            provider,
            StrUtil.blankToDefault(openAiBaseUrl, "<empty>"),
            request.getOptions() != null ? request.getOptions().getClass().getName() : "<null>",
            options != null ? options.getModel() : "<unknown>",
            options != null ? options.getParallelToolCalls() : null,
            adjustedParallelToolCalls ? " (was true)" : "",
            adjustedStreamUsage ? ", streamOptions removed" : "",
            summarizeTools(request.getTools()),
            CollUtil.size(request.getMessages()),
            summarizeTailMessages(request.getMessages(), 3),
            summarizeLastAssistantMessage(request.getMessages()),
            summarizeLastToolMessage(request.getMessages()),
            summarizeMessages(request.getMessages())
        );
    }

    /**
     * 包装模型响应，进行归一化处理
     *
     * 处理逻辑：
     * 1. 对同步响应进行归一化
     * 2. 对流式响应（Flux）进行逐片归一化，并添加日志
     * 3. 对直接消息响应进行归一化
     */
    private ModelResponse wrapModelResponse(String callId,
                                            String provider,
                                            OpenAiChatOptions options,
                                            ModelRequest request,
                                            ModelResponse response) {

        ChatResponse normalizedChatResponse = normalizeChatResponseForProvider(provider, response != null ? response.getChatResponse() : null);
        if (response == null) {
            log.warn(
                "模型响应为空[{}]: provider={}, model={}, tools={}",
                callId,
                provider,
                options != null ? options.getModel() : "<unknown>",
                summarizeTools(request.getTools())
            );
            return null;
        }

        if (normalizedChatResponse != null) {
            log.info(
                "模型同步响应[{}]: provider={}, model={}, summary={}",
                callId,
                provider,
                options != null ? options.getModel() : "<unknown>",
                summarizeChatResponse(normalizedChatResponse)
            );
        }

        Object message = response.getMessage();
        if (message instanceof Flux<?> originalFlux) {
            // 流式响应：逐片归一化并记录日志
            AtomicInteger emissionCount = new AtomicInteger();
            Flux<?> wrappedFlux = originalFlux

                .map(item -> normalizeStreamItemForProvider(provider, item))
                .doOnSubscribe(subscription -> log.info(
                    "模型流开始[{}]: provider={}, model={}, tools={}",
                    callId,
                    provider,
                    options != null ? options.getModel() : "<unknown>",
                    summarizeTools(request.getTools())
                ))
                .doOnNext(item -> {
                    int index = emissionCount.incrementAndGet();
                    log.info(
                        "模型流片段[{}#{}]: provider={}, model={}, summary={}",
                        callId,
                        index,
                        provider,
                        options != null ? options.getModel() : "<unknown>",
                        summarizeStreamItem(item)
                    );
                })
                .doOnComplete(() -> {
                    if (emissionCount.get() == 0) {
                        log.warn(
                            "模型流为空[{}]: provider={}, model={}, tools={}, messages={}",
                            callId,
                            provider,
                            options != null ? options.getModel() : "<unknown>",
                            summarizeTools(request.getTools()),
                            summarizeMessages(request.getMessages())
                        );
                        return;
                    }
                    log.info(
                        "模型流完成[{}]: provider={}, model={}, emissionCount={}",
                        callId,
                        provider,
                        options != null ? options.getModel() : "<unknown>",
                        emissionCount.get()
                    );
                })
                .doOnError(error -> log.error(
                    "模型流异常[{}]: provider={}, model={}, emissionCount={}, error={}",
                    callId,
                    provider,
                    options != null ? options.getModel() : "<unknown>",
                    emissionCount.get(),
                    error.getMessage(),
                    error
                ));
            return new ModelResponse(wrappedFlux, normalizedChatResponse);
        }

        // 非流式响应：直接归一化
        Object normalizedMessage = normalizeDirectMessageForProvider(provider, message);
        if (message instanceof AssistantMessage assistantMessage) {
            log.info(
                "模型消息响应[{}]: provider={}, model={}, summary={}",
                callId,
                provider,
                options != null ? options.getModel() : "<unknown>",
                summarizeAssistantMessage((AssistantMessage) normalizedMessage)
            );
        }
        else if (normalizedMessage != null) {
            log.info(
                "模型消息响应[{}]: provider={}, model={}, type={}",
                callId,
                provider,
                options != null ? options.getModel() : "<unknown>",
                normalizedMessage.getClass().getName()
            );
        }
        else {
            log.warn(
                "模型消息为空[{}]: provider={}, model={}, tools={}",
                callId,
                provider,
                options != null ? options.getModel() : "<unknown>",
                summarizeTools(request.getTools())
            );
        }

        return new ModelResponse(normalizedMessage, normalizedChatResponse);
    }

    /**
     * 根据基础 URL 解析提供商名称
     */
    private String resolveProvider(String baseUrl) {
        if (StrUtil.isBlank(baseUrl)) {
            return "unknown";
        }
        String normalized = baseUrl.trim().toLowerCase();
        if (normalized.contains("siliconflow")) {
            return "siliconflow";
        }
        if (normalized.contains("dashscope") || normalized.contains("aliyuncs")) {
            return "dashscope";
        }
        return normalized;
    }

    /**
     * 归一化流式响应中的每个元素
     */
    private Object normalizeStreamItemForProvider(String provider, Object item) {
        if (item instanceof ChatResponse chatResponse) {
            return normalizeChatResponseForProvider(provider, chatResponse);
        }
        if (item instanceof AssistantMessage assistantMessage) {
            return normalizeAssistantMessageForProvider(provider, assistantMessage);
        }
        return item;
    }

    /**
     * 归一化直接消息响应
     */
    private Object normalizeDirectMessageForProvider(String provider, Object message) {
        if (message instanceof AssistantMessage assistantMessage) {

            return normalizeAssistantMessageForProvider(provider, assistantMessage);
        }
        return message;
    }

    /**
     * 归一化 ChatResponse，处理 DashScope 的工具调用碎片化问题
     */
    private ChatResponse normalizeChatResponseForProvider(String provider, ChatResponse chatResponse) {
        if (!isDashScopeProvider(provider) || chatResponse == null || CollUtil.isEmpty(chatResponse.getResults())) {
            return chatResponse;
        }

        boolean changed = false;
        List<Generation> normalizedResults = new ArrayList<>(chatResponse.getResults().size());
        for (Generation generation : chatResponse.getResults()) {
            if (generation == null) {
                normalizedResults.add(null);
                continue;
            }

            AssistantMessage normalizedOutput = normalizeAssistantMessageForProvider(provider, generation.getOutput());
            if (normalizedOutput != generation.getOutput()) {
                changed = true;
            }
            normalizedResults.add(new Generation(normalizedOutput, generation.getMetadata()));
        }

        if (!changed) {
            return chatResponse;
        }

        return new ChatResponse(normalizedResults, chatResponse.getMetadata());
    }

    /**
     * 归一化 AssistantMessage，合并 DashScope 的碎片化工具调用
     */
    private AssistantMessage normalizeAssistantMessageForProvider(String provider, AssistantMessage assistantMessage) {

        if (!isDashScopeProvider(provider) || assistantMessage == null || CollUtil.isEmpty(assistantMessage.getToolCalls())) {
            return assistantMessage;
        }

        List<AssistantMessage.ToolCall> normalizedToolCalls = mergeDashScopeFragmentedToolCalls(assistantMessage.getToolCalls());
        if (Objects.equals(normalizedToolCalls, assistantMessage.getToolCalls())) {
            return assistantMessage;
        }

        log.warn(
            "DashScope工具调用兼容修正: before={}, after={}",
            summarizeToolCalls(assistantMessage.getToolCalls()),
            summarizeToolCalls(normalizedToolCalls)
        );

        return AssistantMessage.builder()
            .content(assistantMessage.getText())
            .properties(assistantMessage.getMetadata())
            .toolCalls(normalizedToolCalls)
            .media(assistantMessage.getMedia())
            .build();
    }

    private boolean isDashScopeProvider(String provider) {
        return "dashscope".equals(provider);
    }

    /**
     * 合并 DashScope 的碎片化工具调用
     *
     * DashScope 在流式返回工具调用时，可能将一个工具调用拆分为多个片段（如 ID 和参数分开返回）。
     * 此方法通过工具调用 ID 将相同 ID 的碎片合并为完整的工具调用。
     *
     * @param toolCalls 原始工具调用列表（可能包含碎片）
     * @return 合并后的工具调用列表
     */
    private List<AssistantMessage.ToolCall> mergeDashScopeFragmentedToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (CollUtil.isEmpty(toolCalls)) {
            return List.of();
        }

        Map<String, AssistantMessage.ToolCall> mergedById = new LinkedHashMap<>();
        List<AssistantMessage.ToolCall> normalizedToolCalls = new ArrayList<>();

        for (int index = 0; index < toolCalls.size(); index++) {
            AssistantMessage.ToolCall toolCall = toolCalls.get(index);
            if (toolCall == null) {
                continue;
            }

            String mergeKey = StrUtil.isNotBlank(toolCall.id()) ? toolCall.id() : "__dashscope_missing_id__" + index;
            AssistantMessage.ToolCall existing = mergedById.get(mergeKey);
            if (existing == null) {
                mergedById.put(mergeKey, toolCall);
                normalizedToolCalls.add(toolCall);
                continue;
            }

            // 合并碎片：保留已有值，用新值填充空字段
            AssistantMessage.ToolCall merged = new AssistantMessage.ToolCall(
                StrUtil.blankToDefault(existing.id(), toolCall.id()),
                StrUtil.blankToDefault(existing.type(), toolCall.type()),
                StrUtil.blankToDefault(existing.name(), toolCall.name()),
                StrUtil.blankToDefault(existing.arguments(), toolCall.arguments())
            );

            mergedById.put(mergeKey, merged);

            int replaceIndex = normalizedToolCalls.indexOf(existing);
            if (replaceIndex >= 0) {
                normalizedToolCalls.set(replaceIndex, merged);
            }
        }

        return normalizedToolCalls;
    }

    // ==================== 日志摘要工具方法 ====================

    private String summarizeTools(List<String> tools) {
        return CollUtil.isEmpty(tools) ? "[]" : tools.toString();
    }

    private String summarizeTailMessages(List<Message> messages, int limit) {
        if (CollUtil.isEmpty(messages)) {
            return "[]";
        }
        int startIndex = Math.max(0, messages.size() - limit);
        List<String> tail = new ArrayList<>(messages.size() - startIndex);
        for (int index = startIndex; index < messages.size(); index++) {
            tail.add(summarizeMessage(messages.get(index)));
        }
        return tail.toString();
    }

    private String summarizeMessages(List<Message> messages) {
        if (CollUtil.isEmpty(messages)) {
            return "[]";
        }
        return messages.stream()
            .map(this::summarizeMessage)
            .collect(Collectors.joining(" | "));
    }

    private String summarizeLastAssistantMessage(List<Message> messages) {
        if (CollUtil.isEmpty(messages)) {
            return "<none>";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (message instanceof AssistantMessage assistantMessage) {
                return summarizeAssistantMessage(assistantMessage);
            }
        }
        return "<none>";
    }

    private String summarizeLastToolMessage(List<Message> messages) {
        if (CollUtil.isEmpty(messages)) {
            return "<none>";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                return summarizeToolResponseMessage(toolResponseMessage);
            }
        }
        return "<none>";
    }

    private String summarizeMessage(Message message) {
        String type = message.getMessageType() != null
            ? message.getMessageType().name()
            : message.getClass().getSimpleName();

        if (message instanceof AssistantMessage assistantMessage) {
            return type + ":" + summarizeAssistantMessage(assistantMessage);
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            return type + ":" + summarizeToolResponseMessage(toolResponseMessage);
        }
        if (message instanceof AbstractMessage abstractMessage && StrUtil.isNotBlank(abstractMessage.getText())) {
            String text = abstractMessage.getText().replaceAll("\\s+", " ").trim();
            if (text.length() > 80) {
                text = text.substring(0, 80) + "...";
            }
            return type + ":" + text;
        }
        return type;
    }

    private String summarizeStreamItem(Object item) {
        if (item instanceof ChatResponse chatResponse) {
            return summarizeChatResponse(chatResponse);
        }
        if (item instanceof AssistantMessage assistantMessage) {
            return summarizeAssistantMessage(assistantMessage);
        }
        if (item == null) {
            return "<null>";
        }
        return item.getClass().getName();
    }

    private String summarizeChatResponse(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return "chatResponse=null";
        }
        if (chatResponse.getResult() == null) {
            return "chatResponse.result=null";
        }
        return "results=" + (chatResponse.getResults() != null ? chatResponse.getResults().size() : 0)
            + ", output=" + summarizeAssistantMessage(chatResponse.getResult().getOutput());
    }

    private String summarizeAssistantMessage(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return "assistantMessage=null";
        }
        String text = assistantMessage.getText();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        int textLength = text != null ? text.length() : 0;
        int toolCallCount = toolCalls != null ? toolCalls.size() : 0;
        String preview = StrUtil.isNotBlank(text)
            ? StrUtil.maxLength(text.replaceAll("\\s+", " ").trim(), 120)
            : "";
        return "hasText=" + StrUtil.isNotBlank(text)
            + ", textLength=" + textLength
            + ", toolCalls=" + toolCallCount
            + (toolCallCount > 0 ? ", toolCallDetails=" + summarizeToolCalls(toolCalls) : "")
            + (StrUtil.isNotBlank(preview) ? ", preview=" + preview : "");
    }

    private String summarizeToolResponseMessage(ToolResponseMessage toolResponseMessage) {
        if (toolResponseMessage == null) {
            return "toolResponseMessage=null";
        }
        List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
        int responseCount = responses != null ? responses.size() : 0;
        String text = toolResponseMessage.getText();
        String preview = StrUtil.isNotBlank(text)
            ? StrUtil.maxLength(text.replaceAll("\\s+", " ").trim(), 120)
            : "";
        return "responses=" + responseCount
            + ", hasText=" + StrUtil.isNotBlank(text)
            + (responseCount > 0 ? ", responseDetails=" + summarizeToolResponses(responses) : "")
            + (StrUtil.isNotBlank(preview) ? ", preview=" + preview : "");
    }

    private String summarizeToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (CollUtil.isEmpty(toolCalls)) {
            return "[]";
        }
        return toolCalls.stream()
            .map(toolCall -> "{id=" + StrUtil.blankToDefault(toolCall.id(), "<empty>")
                + ", type=" + StrUtil.blankToDefault(toolCall.type(), "<empty>")
                + ", name=" + StrUtil.blankToDefault(toolCall.name(), "<empty>")
                + ", arguments=" + summarizeArguments(toolCall.arguments()) + "}")
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private String summarizeToolResponses(List<ToolResponseMessage.ToolResponse> responses) {
        if (CollUtil.isEmpty(responses)) {
            return "[]";
        }
        return responses.stream()
            .map(response -> "{id=" + StrUtil.blankToDefault(response.id(), "<empty>")
                + ", name=" + StrUtil.blankToDefault(response.name(), "<empty>")
                + ", responseData=" + summarizeArguments(response.responseData()) + "}")
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private String summarizeArguments(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "<empty>";
        }
        String normalized = raw.replaceAll("\\s+", " ").trim();
        return StrUtil.maxLength(normalized, 160);
    }
}
