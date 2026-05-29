package ai.knowhub.chat.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.config.TavilySearchProperties;
import ai.knowhub.chat.model.debug.ChatDebugTrace;
import ai.knowhub.chat.model.debug.ChatToolTrace;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.support.ChatContextKeys;
import ai.knowhub.chat.support.RestClientFactorySupport;
import ai.knowhub.chat.support.SinkEmitHelper;
import ai.knowhub.chat.support.StreamEventMetadata;
import ai.knowhub.chat.support.StreamEventWriter;
import ai.knowhub.chat.support.TimeSensitiveQueryHelper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tavily 联网搜索工具。
 *
 * 该类是 Spring AI Alibaba Agent 框架中注册的一个"工具"（Tool），
 * 允许 LLM 在推理过程中自主决定是否需要联网搜索来回答用户问题。
 * 当 LLM 判断需要搜索时，会生成工具调用请求，Spring AI 框架会自动
 * 调用该工具的 search() 方法执行搜索，并将结果返回给 LLM。
 *
 * 在架构中的角色
 * 属于"工具层"（Tool Layer），是 ReAct Agent 的核心组件之一。
 * ReAct（Reasoning + Acting）模式中，LLM 交替进行"推理"和"行动"，
 * 联网搜索就是"行动"的一种。该工具通过 @Component 注解注册到
 * Spring IoC 容器，在 ChatAgentConfiguration 中被绑定到 Agent 图上。
 *
 * 设计模式
 * 
 *   <b>策略模式</b> - 通过 TavilySearchProperties 配置不同的搜索策略
 *   <b>模板方法模式</b> - search() 方法定义了搜索的标准流程：校验 -> 构建查询 -> 调用API -> 处理结果
 *   <b>观察者模式</b> - 通过 StreamEventWriter 和 Sinks.Many 向 SSE 流发布中间思考过程
 * Lombok 注解说明
 * 
 *   @Slf4j - 自动生成 log 日志对象，用于输出日志信息
 *   @Component - Spring 组件注解，将该类注册到 IoC 容器中
 * 
 */
@Slf4j
@Component
public class TavilySearchTool {

    /**
     * Tavily API 支持的搜索主题白名单。
     *
     * 只有 "general"（通用）、"news"（新闻）、"finance"（金融）三种主题被允许。
     * 如果用户或配置传入了不在白名单中的主题，会自动回退为 "general"。
     */
    private static final Set<String> ALLOWED_TOPICS = Set.of("general", "news", "finance");

    /**
     * Tavily 搜索配置属性，包含 API Key、基础 URL、搜索深度等配置项。
     */
    private final TavilySearchProperties properties;

    /**
     * SSE 流事件写入器，用于将搜索过程中的中间思考步骤推送给前端。
     */
    private final StreamEventWriter streamEventWriter;

    /**
     * Spring RestClient 实例，用于向 Tavily API 发送 HTTP POST 请求。
     */
    private final RestClient restClient;

    /**
     * 构造方法，通过依赖注入获取配置和服务。
     *
     * @param properties       Tavily 搜索配置属性
     * @param streamEventWriter SSE 流事件写入器
     */
    public TavilySearchTool(TavilySearchProperties properties, StreamEventWriter streamEventWriter) {
        this.properties = properties;
        this.streamEventWriter = streamEventWriter;
        // 使用工厂方法创建 RestClient，配置连接超时和读取超时
        this.restClient = RestClientFactorySupport.create(
            properties.getBaseUrl(),
            properties.getConnectTimeoutMs(),
            properties.getReadTimeoutMs()
        );
    }

    /**
     * 执行 Tavily 联网搜索。
     *
     * 这是 LLM 通过工具调用机制实际调用的方法。完整流程：
     * 
     *   校验请求参数和配置（query 非空、工具已启用、API Key 已配置）
     *   注册工具追踪记录（用于调试和性能监控）
     *   向 SSE 流发布"正在搜索"的思考消息
     *   构建增强查询（自动添加日期前缀以提升时效性）
     *   调用 Tavily REST API 获取搜索结果
     *   解析结果，构建引用列表（SearchReference）
     *   将引用追加到上下文中（供后续回答生成使用）
     *   返回结构化的搜索结果给 LLM
     * @param request      搜索请求参数，由 LLM 自动生成
     * @param toolContext   Spring AI 的工具上下文，包含 Agent 执行的运行时信息
     * @return 搜索结果对象，包含 AI 摘要答案和搜索引用列表
     * @throws IllegalArgumentException 如果 query 为空
     * @throws IllegalStateException    如果工具已禁用或 API Key 未配置
     */
    public TavilySearchToolResult search(TavilySearchDto request, ToolContext toolContext) {

        // ========== 1. 参数校验 ==========
        String rawQuery = request != null && StrUtil.isNotBlank(request.getQuery()) ? request.getQuery().trim() : "";
        if (StrUtil.isBlank(rawQuery)) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Tavily 搜索工具当前已禁用");
        }
        if (StrUtil.isBlank(properties.getApiKey())) {
            throw new IllegalStateException("Tavily API Key 未配置");
        }

        // ========== 2. 注册追踪信息和发布思考消息 ==========
        long startTime = System.currentTimeMillis();
        String topic = resolveTopic(request);
        ChatToolTrace toolTrace = registerToolTrace(toolContext, ChatToolTrace.builder()
            .toolName("tavily_search")
            .status("RUNNING")
            .inputSummary(rawQuery)
            .topic(topic)
            .build());
        markToolUsed(toolContext, "tavily_search");
        publishThinking(toolContext, "🔍 正在联网搜索: " + rawQuery);

        try {

            // ========== 3. 构建增强查询并调用 Tavily API ==========
            String effectiveQuery = buildEffectiveQuery(rawQuery, toolContext);
            if (toolTrace != null) {
                toolTrace.setEffectiveInput(effectiveQuery);
            }

            // 使用 RestClient 发送 POST 请求到 Tavily 搜索 API
            TavilySearchApiResponse response = restClient.post()
                .uri(properties.getSearchPath())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(new TavilySearchApiRequest(
                    effectiveQuery,
                    topic,
                    properties.getSearchDepth(),
                    request != null && request.getMaxResults() != null && request.getMaxResults() > 0
                        ? request.getMaxResults()
                        : properties.getMaxResults(),
                    properties.isIncludeAnswer(),
                    properties.isIncludeRawContent()
                ))
                .retrieve()
                .body(TavilySearchApiResponse.class);

            if (response == null) {
                throw new IllegalStateException("Tavily 返回空响应");
            }

            // ========== 4. 解析搜索结果，构建引用列表 ==========
            List<SearchReference> references = new ArrayList<>();
            if (response.results() != null) {
                for (TavilyResultItem item : response.results()) {
                    if (StrUtil.isBlank(item.url())) {
                        continue;
                    }
                    references.add(new SearchReference(
                        item.title(),
                        item.url(),
                        StrUtil.isNotBlank(item.content()) ? item.content() : ""
                    ));
                }
            }

            // ========== 5. 保存引用到上下文并发布完成消息 ==========
            appendReferences(toolContext, references);
            publishThinking(toolContext, "📚 搜索完成，找到 " + references.size() + " 条候选来源");
            completeToolTrace(toolTrace, response, references.size(), startTime);

            // ========== 6. 返回结构化搜索结果 ==========
            return new TavilySearchToolResult(
                effectiveQuery,
                StrUtil.isNotBlank(response.answer()) ? response.answer() : "",
                List.copyOf(references)
            );
        }
        catch (RuntimeException exception) {

            // ========== 异常处理：记录失败追踪并重新抛出异常 ==========
            failToolTrace(toolTrace, exception, startTime);
            publishThinking(toolContext, "⚠️ 搜索失败: " + exception.getMessage());
            log.warn("Tavily 搜索失败, query={}", rawQuery, exception);
            throw exception;
        }
    }

    /**
     * 构建增强后的搜索查询。
     *
     * 通过 TimeSensitiveQueryHelper 为原始查询添加日期等时间敏感信息，
     * 使搜索结果更具时效性。例如将"今天的新闻"增强为"2024-01-15 今天的新闻"。
     *
     * @param query        原始查询词
     * @param toolContext   工具上下文，用于获取当前日期信息
     * @return 增强后的查询词
     */
    private String buildEffectiveQuery(String query, ToolContext toolContext) {
        if (StrUtil.isBlank(query)) {
            return query;
        }

        return TimeSensitiveQueryHelper.buildEffectiveSearchQuery(query, resolveCurrentDate(toolContext));
    }

    /**
     * 从工具上下文中解析当前日期。
     *
     * 从 Agent 图的 RunnableConfig 上下文中获取 CURRENT_DATE 键对应的值，
     * 该值在对话开始时被设置，用于时间敏感查询的增强。
     *
     * @param toolContext 工具上下文
     * @return 当前日期字符串，如果未找到则返回空字符串
     */
    private String resolveCurrentDate(ToolContext toolContext) {
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null) {
            return "";
        }
        Object value = config.context().get(ChatContextKeys.CURRENT_DATE);
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            return text.trim();
        }
        return "";
    }

    /**
     * 解析并确定搜索主题。
     *
     * 优先使用请求中指定的主题，其次使用配置文件中的默认主题，
     * 最终回退为 "general"（通用搜索）。主题值会经过白名单校验。
     *
     * @param request 搜索请求
     * @return 合法的主题字符串
     */
    private String resolveTopic(TavilySearchDto request) {

        // 优先使用请求中指定的主题
        String requestedTopic = normalizeTopic(request != null ? request.getTopic() : null);
        if (requestedTopic != null) {
            return requestedTopic;
        }

        // 其次使用配置文件中的默认主题
        String configuredTopic = normalizeTopic(properties.getTopic());
        if (configuredTopic != null) {
            return configuredTopic;
        }

        // 配置不合法时记录警告并回退
        if (StrUtil.isNotBlank(properties.getTopic())) {
            log.warn("Tavily 默认 topic 配置不合法: {}, 自动回退为 general", properties.getTopic());
        }
        return "general";
    }

    /**
     * 标准化主题字符串。
     *
     * 将主题转换为小写并与白名单比较，不在白名单中的主题返回 null。
     *
     * @param rawTopic 原始主题字符串
     * @return 标准化后的主题，不在白名单中则返回 null
     */
    private String normalizeTopic(String rawTopic) {
        if (StrUtil.isBlank(rawTopic)) {
            return null;
        }

        String normalized = rawTopic.trim().toLowerCase(Locale.ROOT);
        if (ALLOWED_TOPICS.contains(normalized)) {
            return normalized;
        }

        log.warn("收到不受支持的 Tavily topic: {}, 允许值仅为 {}", rawTopic, ALLOWED_TOPICS);
        return null;
    }

    /**
     * 将搜索引用追加到 Agent 上下文中。
     *
     * 搜索结果引用会被收集到上下文的 REFERENCES 列表中，
     * 供后续的答案生成阶段使用（用于展示来源链接）。
     *
     * @param toolContext  工具上下文
     * @param references   搜索结果引用列表
     */
    @SuppressWarnings("unchecked")
    private void appendReferences(ToolContext toolContext, List<SearchReference> references) {
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null || references.isEmpty()) {
            return;
        }

        Object container = config.context().get(ChatContextKeys.REFERENCES);
        if (container instanceof List<?> list) {
            ((List<SearchReference>) list).addAll(references);
        }
    }

    /**
     * 标记工具已被使用。
     *
     * 将工具名称添加到上下文的 USED_TOOLS 集合中，
     * 用于追踪本次对话使用了哪些工具，便于前端展示和调试。
     *
     * @param toolContext 工具上下文
     * @param toolName    工具名称
     */
    @SuppressWarnings("unchecked")
    private void markToolUsed(ToolContext toolContext, String toolName) {
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null) {
            return;
        }

        Object container = config.context().get(ChatContextKeys.USED_TOOLS);
        if (container instanceof Set<?> set) {
            ((Set<String>) set).add(toolName);
        }
    }

    /**
     * 发布思考消息到 SSE 流。
     *
     * 将中间思考过程（如"正在联网搜索"、"搜索完成"等）通过 Sinks.Many
     * 推送给前端，实现 SSE（Server-Sent Events）流式响应。
     * 同时将思考步骤记录到 THINKING_STEPS 列表中。
     *
     * @param toolContext 工具上下文
     * @param content     思考内容文本
     */
    @SuppressWarnings("unchecked")
    private void publishThinking(ToolContext toolContext, String content) {
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null) {
            return;
        }

        // 通过 Sinks.Many 发布 SSE 事件到前端
        Object sinkCandidate = config.context().get(ChatContextKeys.EVENT_SINK);
        StreamEventMetadata metadata = resolveMetadata(config);
        if (sinkCandidate instanceof Sinks.Many<?> sink) {
            SinkEmitHelper.emitNext((Sinks.Many<String>) sink, streamEventWriter.thinking(content, metadata));
        }

        // 将思考步骤记录到列表中
        Object stepsCandidate = config.context().get(ChatContextKeys.THINKING_STEPS);
        if (stepsCandidate instanceof List<?> list) {
            ((List<String>) list).add(content);
        }
    }

    /**
     * 从配置上下文中解析事件元数据。
     *
     * @param config 运行时配置
     * @return 事件元数据对象，未找到则返回 null
     */
    private StreamEventMetadata resolveMetadata(RunnableConfig config) {
        if (config == null) {
            return null;
        }
        Object metadataCandidate = config.context().get(ChatContextKeys.EVENT_METADATA);
        if (metadataCandidate instanceof StreamEventMetadata metadata) {
            return metadata;
        }
        return null;
    }

    /**
     * 注册工具追踪记录到调试追踪系统。
     *
     * 将本次工具调用的追踪信息添加到 ChatDebugTrace 中，
     * 用于调试面板展示工具执行的详细过程（输入、输出、耗时等）。
     *
     * @param toolContext 工具上下文
     * @param trace       工具追踪记录
     * @return 注册后的追踪记录
     */
    private ChatToolTrace registerToolTrace(ToolContext toolContext, ChatToolTrace trace) {
        if (trace == null) {
            return null;
        }
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null) {
            return trace;
        }
        Object candidate = config.context().get(ChatContextKeys.DEBUG_TRACE);
        if (candidate instanceof ChatDebugTrace debugTrace) {
            debugTrace.getToolTraces().add(trace);
        }
        return trace;
    }

    /**
     * 标记工具追踪为完成状态。
     *
     * 搜索成功后更新追踪记录的状态为 "COMPLETED"，记录引用数量、
     * 执行耗时和输出摘要。
     *
     * @param toolTrace      工具追踪记录
     * @param response       Tavily API 响应
     * @param referenceCount 搜索结果引用数量
     * @param startTime      开始时间戳（毫秒）
     */
    private void completeToolTrace(ChatToolTrace toolTrace,
                                   TavilySearchApiResponse response,
                                   int referenceCount,
                                   long startTime) {
        if (toolTrace == null) {
            return;
        }
        toolTrace.setStatus("COMPLETED");
        toolTrace.setReferenceCount(referenceCount);
        toolTrace.setDurationMs(Math.max(0L, System.currentTimeMillis() - startTime));
        String answer = response == null ? "" : StrUtil.blankToDefault(response.answer(), "");
        if (StrUtil.isNotBlank(answer)) {
            toolTrace.setOutputSummary("联网结果已返回，答案摘要：" + clipText(answer, 160));
            return;
        }
        toolTrace.setOutputSummary("联网结果已返回，候选来源 " + referenceCount + " 条");
    }

    /**
     * 标记工具追踪为失败状态。
     *
     * 搜索失败后更新追踪记录的状态为 "FAILED"，记录错误信息和耗时。
     *
     * @param toolTrace 工具追踪记录
     * @param exception 异常信息
     * @param startTime 开始时间戳（毫秒）
     */
    private void failToolTrace(ChatToolTrace toolTrace, RuntimeException exception, long startTime) {
        if (toolTrace == null) {
            return;
        }
        toolTrace.setStatus("FAILED");
        toolTrace.setDurationMs(Math.max(0L, System.currentTimeMillis() - startTime));
        toolTrace.setErrorMessage(exception == null ? "" : StrUtil.blankToDefault(exception.getMessage(), ""));
    }

    /**
     * 截断文本到指定最大长度。
     *
     * 用于生成追踪摘要时限制文本长度，超出部分用 "..." 替代。
     *
     * @param value     原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String clipText(String value, int maxLength) {
        if (StrUtil.isBlank(value) || maxLength <= 0) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    /**
     * Tavily API 请求体记录类（Record）。
     *
     * 使用 Java Record 特性（Java 16+）定义不可变的数据载体，
     * 用于序列化为 JSON 请求体发送给 Tavily API。
     *
     * 字段说明
     * 
     *   query - 搜索查询词
     *   topic - 搜索主题
     *   searchDepth - 搜索深度（basic/advanced），映射为 JSON 字段 "search_depth"
     *   maxResults - 最大结果数，映射为 JSON 字段 "max_results"
     *   includeAnswer - 是否包含 AI 摘要答案，映射为 "include_answer"
     *   includeRawContent - 是否包含原始内容，映射为 "include_raw_content"
     * @JsonProperty 注解说明
     * Jackson 的 @JsonProperty 注解用于指定 Java 字段与 JSON 字段名的映射关系，
     * 因为 Tavily API 使用下划线命名风格（snake_case），而 Java 使用驼峰命名风格（camelCase）。
     */
    private record TavilySearchApiRequest(
        String query,
        String topic,
        @JsonProperty("search_depth")
        String searchDepth,
        @JsonProperty("max_results")
        int maxResults,
        @JsonProperty("include_answer")
        boolean includeAnswer,
        @JsonProperty("include_raw_content")
        boolean includeRawContent
    ) {
    }

    /**
     * Tavily API 响应体记录类（Record）。
     *
     * 用于反序列化 Tavily API 返回的 JSON 响应。
     * 包含 AI 生成的摘要答案（answer）和搜索结果列表（results）。
     */
    private record TavilySearchApiResponse(
        String answer,
        List<TavilyResultItem> results
    ) {
    }

    /**
     * Tavily 搜索结果条目记录类（Record）。
     *
     * 表示单条搜索结果，包含标题（title）、URL 和内容摘要（content）。
     * 这些数据会被转换为 SearchReference 对象，供 LLM 参考和前端展示。
     */
    private record TavilyResultItem(
        String title,
        String url,
        String content
    ) {
    }
}
