package ai.knowhub.chat.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallExecutionContext;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 【Tavily 搜索工具输入参数兜底拦截器】
 *
 * 作用：在 Tavily 网络搜索工具被调用前，检查并修正输入参数。
 * 当 AI 模型生成的 Tavily 工具调用参数不规范或缺失时，
 * 此拦截器会自动补充或修正参数，确保工具调用不会因参数问题而失败。
 *
 * 背景说明：
 * AI 模型在调用工具时，有时会生成不规范的参数（如空 query、错误格式等），
 * 如果不加处理直接传给 Tavily API，会导致调用失败。
 * 此拦截器作为「最后一道防线」，确保工具调用的参数有效性。
 *
 * 所属架构位置：属于 Spring AI Alibaba 的工具拦截器层（Tool Interceptor Layer），
 * 通过 ToolInterceptor 接口实现，在工具调用前执行参数校验和修正。
 *
 * 设计模式说明：
 * 1. 「拦截器模式（Interceptor Pattern）」—— 在工具调用前插入参数校验逻辑。
 * 2. 「兜底模式（Fallback Pattern）」—— 当参数不规范时，使用备选方案（用户原始问题）。
 *
 * 参数修正策略：
 * 1. 如果 arguments 为空 -> 使用用户原始问题作为 query
 * 2. 如果 arguments 不是有效 JSON -> 将其作为 query 值
 * 3. 如果 arguments 是 JSON 但缺少 query -> 补充 query 字段
 * 4. 如果 arguments 是纯文本字符串 -> 包装为 {"query": "文本"} 格式
 *
 * @author knowhub
 */
@Component
public class TavilyToolInputFallbackInterceptor extends ToolInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TavilyToolInputFallbackInterceptor.class);

    /** Tavily 搜索工具的名称常量 */
    private static final String TAVILY_TOOL_NAME = "tavily_search";

    /** Jackson JSON 处理器 */
    private final ObjectMapper objectMapper;

    public TavilyToolInputFallbackInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "tavilyToolInputFallbackInterceptor";
    }

    /**
     * 拦截工具调用，对 Tavily 搜索工具的输入参数进行校验和修正
     *
     * @param request 工具调用请求，包含工具名、参数等
     * @param handler 下游工具调用处理器
     * @return 工具调用响应
     */
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        // 只处理 tavily_search 工具，其他工具直接透传
        if (!TAVILY_TOOL_NAME.equals(request.getToolName())) {
            return handler.call(request);
        }

        // 归一化参数
        String normalizedArguments = normalizeArguments(request);
        if (StrUtil.isBlank(normalizedArguments)) {
            log.warn("工具 {} 缺少可用入参，toolCallId={}", request.getToolName(), request.getToolCallId());
            return ToolCallResponse.error(
                request.getToolCallId(),
                request.getToolName(),
                "tavily_search 工具缺少可用的 query 参数"
            );
        }

        // 如果参数未变化，直接透传
        if (normalizedArguments.equals(request.getArguments())) {
            return handler.call(request);
        }

        // 构建修正后的请求
        ToolCallRequest.Builder builder = ToolCallRequest.builder(request)
            .arguments(normalizedArguments);
        request.getExecutionContext().ifPresent(builder::executionContext);

        log.warn("工具 {} 收到空或不规范的 arguments，已自动改写为 {}", request.getToolName(), normalizedArguments);
        return handler.call(builder.build());
    }

    /**
     * 归一化工具调用参数
     *
     * @param request 工具调用请求
     * @return 归一化后的 JSON 参数字符串，如果无法修正则返回 null
     */
    private String normalizeArguments(ToolCallRequest request) {
        String arguments = request.getArguments();
        String fallbackQuery = resolveFallbackQuery(request);

        // 空参数：直接使用兜底 query
        if (StrUtil.isBlank(arguments)) {
            return buildQueryPayload(fallbackQuery);
        }

        try {
            JsonNode rootNode = objectMapper.readTree(arguments);

            // 情况1：参数是 JSON 对象
            if (rootNode != null && rootNode.isObject()) {
                ObjectNode objectNode = ((ObjectNode) rootNode).deepCopy();
                // 如果已有有效的 query 字段，直接返回
                if (StrUtil.isNotBlank(objectNode.path("query").asText())) {
                    return arguments;
                }
                // 缺少 query，尝试补充
                if (StrUtil.isBlank(fallbackQuery)) {
                    return null;
                }
                objectNode.put("query", fallbackQuery);
                return objectMapper.writeValueAsString(objectNode);
            }

            // 情况2：参数是纯文本字符串
            if (rootNode != null && rootNode.isTextual() && StrUtil.isNotBlank(rootNode.asText())) {
                return buildQueryPayload(rootNode.asText().trim());
            }

            // 情况3：其他格式，使用兜底 query
            return buildQueryPayload(fallbackQuery);
        }
        catch (JsonProcessingException exception) {

            // 情况4：JSON 解析失败，将原始文本作为 query
            if (StrUtil.isNotBlank(arguments)) {
                return buildQueryPayload(arguments.trim());
            }
            return buildQueryPayload(fallbackQuery);
        }
    }

    /**
     * 从执行上下文中获取兜底 query（即用户原始问题）
     *
     * 当 AI 生成的工具参数无效时，使用用户的原始问题作为兜底搜索词。
     *
     * @param request 工具调用请求
     * @return 用户原始问题文本，如果无法获取则返回空字符串
     */
    private String resolveFallbackQuery(ToolCallRequest request) {

        return request.getExecutionContext()
            .map(ToolCallExecutionContext::config)
            .map(RunnableConfig::context)
            .map(context -> context.get(ChatContextKeys.QUESTION))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(StrUtil::isNotBlank)
            .map(String::trim)
            .orElse("");
    }

    /**
     * 构建 Tavily 搜索工具的 query 参数 JSON
     *
     * @param query 搜索查询文本
     * @return JSON 格式的参数字符串，如 {"query": "搜索词"}
     */
    private String buildQueryPayload(String query) {
        if (StrUtil.isBlank(query)) {
            return null;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", query.trim());

        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("构造 tavily_search 入参 JSON 失败", exception);
        }
    }
}
