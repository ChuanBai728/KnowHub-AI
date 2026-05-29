package ai.knowhub.chat.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.knowhub.chat.model.SearchReference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【流式事件写入器】
 *
 * 作用：将各种类型的 SSE（Server-Sent Events）事件序列化为 JSON 字符串。
 * 是前端与后端实时通信的核心组件，负责格式化所有推送给前端的事件。
 *
 * 所属架构位置：属于 SSE 流式通信层的核心组件。
 * 对话处理过程中的各个阶段通过此类将事件推送给前端，包括：
 * - 文本事件（AI 回答的文本片段）
 * - 思考事件（AI 的思考步骤）
 * - 状态事件（处理进度提示）
 * - 错误事件（异常信息）
 * - 引用事件（检索到的知识来源）
 * - 推荐事件（推荐的后续问题）
 *
 * 设计模式说明：
 * 1. 「组件模式（Component）」—— 使用 @Component 注解，由 Spring 容器管理。
 * 2. 「工厂方法模式」—— 提供多种事件类型的创建方法。
 * 3. 「统一格式」—— 所有事件都遵循统一的 JSON 格式：
 *    {"type": "事件类型", "content": "内容", "timestamp": "时间戳", ...}
 *
 * @author knowhub
 */
@Component
public class StreamEventWriter {

    /**
     * Jackson JSON 序列化器
     * 用于将事件对象序列化为 JSON 字符串。
     */
    private final ObjectMapper objectMapper;

    public StreamEventWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 创建文本事件（无元数据）
     *
     * @param content 文本内容（AI 回答的片段）
     * @return JSON 格式的事件字符串
     */
    public String text(String content) {
        return text(content, null);
    }

    /**
     * 创建文本事件
     *
     * @param content  文本内容
     * @param metadata 事件元数据（可选）
     * @return JSON 格式的事件字符串
     */
    public String text(String content, StreamEventMetadata metadata) {

        return write(event("text", content, metadata));
    }

    /**
     * 创建思考事件（无元数据）
     *
     * @param content 思考步骤内容
     * @return JSON 格式的事件字符串
     */
    public String thinking(String content) {
        return thinking(content, null);
    }

    /**
     * 创建思考事件
     *
     * @param content  思考步骤内容
     * @param metadata 事件元数据（可选）
     * @return JSON 格式的事件字符串
     */
    public String thinking(String content, StreamEventMetadata metadata) {

        return write(event("thinking", content, metadata));
    }

    /**
     * 创建状态事件（无元数据）
     *
     * @param content 状态提示内容，如 "正在检索知识库..."
     * @return JSON 格式的事件字符串
     */
    public String status(String content) {
        return status(content, null);
    }

    /**
     * 创建状态事件
     *
     * @param content  状态提示内容
     * @param metadata 事件元数据（可选）
     * @return JSON 格式的事件字符串
     */
    public String status(String content, StreamEventMetadata metadata) {

        return write(event("status", content, metadata));
    }

    /**
     * 创建错误事件（无元数据）
     *
     * @param content 错误信息
     * @return JSON 格式的事件字符串
     */
    public String error(String content) {
        return error(content, null);
    }

    /**
     * 创建错误事件
     *
     * @param content  错误信息
     * @param metadata 事件元数据（可选）
     * @return JSON 格式的事件字符串
     */
    public String error(String content, StreamEventMetadata metadata) {

        return write(event("error", content, metadata));
    }

    /**
     * 创建引用事件（无元数据）
     *
     * @param references 搜索引用列表（知识来源）
     * @return JSON 格式的事件字符串，额外包含 count 字段
     */
    public String references(List<SearchReference> references) {
        return references(references, null);
    }

    /**
     * 创建引用事件
     *
     * @param references 搜索引用列表
     * @param metadata   事件元数据（可选）
     * @return JSON 格式的事件字符串，额外包含 count 字段
     */
    public String references(List<SearchReference> references, StreamEventMetadata metadata) {

        Map<String, Object> payload = event("reference", references, metadata);
        payload.put("count", references != null ? references.size() : 0);
        return write(payload);
    }

    /**
     * 创建推荐问题事件（无元数据）
     *
     * @param recommendations 推荐问题列表
     * @return JSON 格式的事件字符串，额外包含 count 字段
     */
    public String recommendations(List<String> recommendations) {
        return recommendations(recommendations, null);
    }

    /**
     * 创建推荐问题事件
     *
     * @param recommendations 推荐问题列表
     * @param metadata        事件元数据（可选）
     * @return JSON 格式的事件字符串，额外包含 count 字段
     */
    public String recommendations(List<String> recommendations, StreamEventMetadata metadata) {

        Map<String, Object> payload = event("recommend", recommendations, metadata);
        payload.put("count", recommendations != null ? recommendations.size() : 0);
        return write(payload);
    }

    /**
     * 构建统一格式的事件 Map
     *
     * @param type     事件类型（text/thinking/status/error/reference/recommend）
     * @param content  事件内容
     * @param metadata 事件元数据（可选）
     * @return 事件 Map
     */
    private Map<String, Object> event(String type, Object content, StreamEventMetadata metadata) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("content", content);
        payload.put("timestamp", Instant.now().toString());
        if (metadata != null) {

            if (metadata.conversationId() != null && !metadata.conversationId().isBlank()) {
                payload.put("conversationId", metadata.conversationId());
            }
            if (metadata.exchangeId() != null && metadata.exchangeId() > 0) {
                payload.put("exchangeId", metadata.exchangeId());
            }
        }
        return payload;
    }

    /**
     * 将事件 Map 序列化为 JSON 字符串
     */
    private String write(Map<String, Object> payload) {

        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("流式事件序列化失败", exception);
        }
    }
}
