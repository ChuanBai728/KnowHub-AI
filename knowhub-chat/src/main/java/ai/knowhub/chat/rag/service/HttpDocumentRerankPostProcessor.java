package ai.knowhub.chat.rag.service;

import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.support.RestClientFactorySupport;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【HTTP 文档重排序后处理器 — RAG 流水线的"精排器"】
 *
 * 这个类负责对检索到的文档进行重排序（Rerank），使用外部 Rerank API
 * 重新评估"问题和文档"的相关性，通常比原始的向量/关键词召回排序更准确。
 *
 * 什么是 Rerank？
 * 初始检索（向量检索 + 关键词检索）是"粗排"，速度快但精度有限。
 * Rerank 是"精排"，使用专门的交叉编码器模型（Cross-Encoder）逐个评估
 * 问题和文档的相关性分数，精度更高但速度较慢。
 *
 * 工作流程：
 * 1. 将检索到的文档文本列表发送给 Rerank API
 * 2. API 返回每个文档的相关性分数和排名
 * 3. 按相关性分数重新排序，取 TopN 个文档
 * 4. 将 Rerank 分数等元信息写入文档的 metadata
 *
 * 设计模式：
 * - 策略模式：实现了 Spring AI 的 DocumentPostProcessor 接口
 * - 降级模式：Rerank API 调用失败时自动回退到原始排序
 *
 * 在 RAG 流水线中的位置：
 * 检索通道返回文档 -> RRF 融合 -> 父块提升 -> 【本类：Rerank 精排】-> TopK 裁剪 -> Prompt
 */
@Slf4j
@Component
public class HttpDocumentRerankPostProcessor implements DocumentPostProcessor {

    /** RAG 配置属性（包含 Rerank 的 URL、API Key、模型名等） */
    private final ChatRagProperties properties;
    /** HTTP 客户端，用于调用 Rerank API */
    private final RestClient restClient;

    /**
     * 构造函数。
     *
     * @param properties RAG 配置属性
     */
    public HttpDocumentRerankPostProcessor(ChatRagProperties properties) {
        this.properties = properties;
        // 创建带超时配置的 HTTP 客户端
        this.restClient = RestClientFactorySupport.create(
            null,
            properties.getRerank().getConnectTimeoutMs(),
            properties.getRerank().getReadTimeoutMs()
        );
    }

    /**
     * 对文档列表进行重排序。
     *
     * 处理流程：
     * 1. 检查文档列表是否为空
     * 2. 检查 Rerank 是否启用且配置了 API Key
     * 3. 提取文档文本，构建请求体
     * 4. 调用 Rerank API
     * 5. 按相关性分数重新排序
     * 6. 将 Rerank 元信息写入文档 metadata
     *
     * @param query     查询对象（包含用户问题）
     * @param documents 待重排序的文档列表
     * @return 重排序后的文档列表（按相关性分数降序）
     */
    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public List<Document> process(@NotNull Query query, @NotNull List<Document> documents) {
        if (CollectionUtil.isEmpty(documents)) {
            return documents;
        }

        ChatRagProperties.RerankProperties rerankProperties = properties.getRerank();
        // TopN 不能超过文档总数
        int topN = Math.min(Math.max(rerankProperties.getTopN(), 1), documents.size());

        // 如果 Rerank 未启用，直接返回原始列表
        if (!rerankProperties.isEnabled()) {
            return documents;
        }
        // 如果未配置 API Key，返回原始列表并警告
        if (rerankProperties.getApiKey() == null || rerankProperties.getApiKey().isBlank()) {
            log.warn("Rerank 已开启但未配置 apiKey，自动回退为原始排序");
            return documents;
        }

        // 提取所有文档的文本内容
        List<String> texts = documents.stream().map(Document::getText).toList();
        // 构建 Rerank API 请求体
        Map<String, Object> body = Map.of(
            "model", rerankProperties.getModel(),   // Rerank 模型名称
            "query", query.text(),                   // 用户问题
            "documents", texts,                      // 文档文本列表
            "top_n", topN,                           // 返回前 N 个
            "return_documents", false                // 不返回文档原文（节省带宽）
        );

        long startTime = System.currentTimeMillis();
        try {
            // 调用 Rerank API
            Map<String, Object> response = restClient.post()
                .uri(rerankProperties.getUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + rerankProperties.getApiKey())
                .body(body)
                .retrieve()
                .body(Map.class);
            long durationMs = System.currentTimeMillis() - startTime;

            // 检查返回结果
            if (response == null || !(response.get("results") instanceof List<?> resultList)) {
                log.warn("Rerank 返回结果为空或格式错误，自动回退为原始排序");
                return documents;
            }

            // 按相关性分数重新排序，取 TopN
            return ((List<Map<String, Object>>) resultList).stream()
                .sorted(Comparator.comparingDouble(result -> -((Number) result.get("relevance_score")).doubleValue()))
                .map(result -> {
                    int index = ((Number) result.get("index")).intValue();
                    double score = ((Number) result.get("relevance_score")).doubleValue();
                    Document document = documents.get(index);
                    // 将 Rerank 元信息写入文档 metadata
                    document.getMetadata().put("rerankScore", score);
                    document.getMetadata().put("rerankModel", rerankProperties.getModel());
                    document.getMetadata().put("rerankQuery", query.text());
                    document.getMetadata().put("rerankDurationMs", durationMs);
                    document.getMetadata().put("rerankOriginalIndex", index);
                    return document;
                })
                .limit(topN)
                .collect(Collectors.toList());
        }
        catch (Exception exception) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.warn("Rerank 调用失败（耗时 {} ms），自动回退为原始排序: {}", durationMs, exception.getMessage());
            return documents;
        }
    }
}
