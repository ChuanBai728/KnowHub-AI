package ai.knowhub.document.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.data.KnowHubDocumentChunk;
import ai.knowhub.document.service.DocumentVectorGateway;
import ai.knowhub.document.support.DocumentPgVectorConstants;
import ai.knowhub.enums.DocumentManageCode;
import ai.knowhub.enums.DocumentVectorStatusEnum;
import ai.knowhub.enums.DocumentVectorStoreTypeEnum;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 【文档向量化网关 - PGVector 实现】
 *
 * 设计模式：网关模式（Gateway Pattern）
 *
 * 这个类负责将文档的 chunk（子块）文本转换为向量（embedding），并存储到 PostgreSQL 的 pgvector 扩展中。
 * 它是 DocumentVectorGateway 接口的默认实现，也是向量化流程的核心执行者。
 *
 * 核心流程：
 *   1. 接收一批 chunk 列表
 *   2. 过滤掉空白无效的 chunk
 *   3. 按批次调用 EmbeddingModel 将文本转为浮点向量
 *   4. 使用 UPSERT（INSERT ON CONFLICT UPDATE）语句将向量写入 pgvector 表
 *   5. 标记每个 chunk 的向量化状态为成功
 *
 * 为什么用 pgvector：
 *   - pgvector 是 PostgreSQL 的向量扩展，支持向量相似度搜索（余弦距离 <=>）
 *   - 相比独立的向量数据库（如 Milvus），pgvector 可以和业务数据在同一数据库中，减少架构复杂度
 *   - 本项目使用 1 - (embedding <=> query_vector) 计算余弦相似度
 *
 * 依赖组件：
 *   - EmbeddingModel：Spring AI 的向量模型抽象，负责将文本转为 float[] 向量
 *   - JdbcTemplate：通过 @Qualifier("documentManagePgVectorJdbcTemplate") 注入的 PG 数据源
 *   - ObjectMapper：用于将 metadata 序列化为 JSONB 存储
 */
@Slf4j
@Service
public class DefaultDocumentVectorGateway implements DocumentVectorGateway {

    public DefaultDocumentVectorGateway(
            @Qualifier("documentManagePgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectMapper objectMapper) {
        this.pgVectorJdbcTemplate = pgVectorJdbcTemplate;
        this.embeddingModelProvider = embeddingModelProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 每次调用 EmbeddingModel 的最大 chunk 数量。
     * 过大的批次可能导致 API 超时或内存溢出，10 是一个安全的默认值。
     */
    public static final int EMBEDDING_BATCH_SIZE_LIMIT = 10;

    /**
     * 向量写入的 UPSERT SQL 模板。
     *
     * 关键点：
     *   - 使用 INSERT ... ON CONFLICT (id) DO UPDATE 实现幂等写入（重复执行不会产生重复数据）
     *   - metadata_json 字段使用 CAST(? AS jsonb) 转为 PostgreSQL 的 JSONB 类型
     *   - embedding 字段使用 CAST(? AS vector) 转为 pgvector 的 VECTOR 类型
     *   - 表名通过 DocumentPgVectorConstants.EMBEDDING_TABLE_NAME 动态注入
     */
    private static final String UPSERT_SQL_TEMPLATE = """
        INSERT INTO %s
        (id, document_id, task_id, plan_id, parent_block_id, chunk_no, source_type, section_path, structure_node_id,
         structure_node_type, canonical_path, item_index, chunk_text, char_count, token_count, embedding_model,
         metadata_json, embedding, create_time, edit_time, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS vector), NOW(), NOW(), ?)
        ON CONFLICT (id) DO UPDATE SET
            document_id = EXCLUDED.document_id,
            task_id = EXCLUDED.task_id,
            plan_id = EXCLUDED.plan_id,
            parent_block_id = EXCLUDED.parent_block_id,
            chunk_no = EXCLUDED.chunk_no,
            source_type = EXCLUDED.source_type,
            section_path = EXCLUDED.section_path,
            structure_node_id = EXCLUDED.structure_node_id,
            structure_node_type = EXCLUDED.structure_node_type,
            canonical_path = EXCLUDED.canonical_path,
            item_index = EXCLUDED.item_index,
            chunk_text = EXCLUDED.chunk_text,
            char_count = EXCLUDED.char_count,
            token_count = EXCLUDED.token_count,
            embedding_model = EXCLUDED.embedding_model,
            metadata_json = EXCLUDED.metadata_json,
            embedding = EXCLUDED.embedding,
            edit_time = NOW(),
            status = EXCLUDED.status
        """;

    /**
     * 按文档ID删除向量数据的 SQL 模板。
     * 当文档被删除或需要重建索引时使用。
     */
    private static final String DELETE_BY_DOCUMENT_SQL_TEMPLATE = "DELETE FROM %s WHERE document_id = ?";

    /**
     * PGVector 专用的 JdbcTemplate。
     * 使用 @Qualifier 指定注入名称，与业务数据库的 JdbcTemplate 区分开。
     */
    @Qualifier("documentManagePgVectorJdbcTemplate")
    private final JdbcTemplate pgVectorJdbcTemplate;

    /**
     * 向量模型提供者（懒加载）。
     * 使用 ObjectProvider 是因为 EmbeddingModel 可能未配置（例如没有配置 API Key）。
     */
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    /**
     * Jackson 的 JSON 序列化工具，用于将 metadata Map 转为 JSON 字符串。
     */
    private final ObjectMapper objectMapper;

    /**
     * Embedding 模型名称，从配置文件读取。
     * 例如 "text-embedding-v3"，用于记录每个 chunk 使用的是哪个模型生成的向量。
     */
    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModelName;

    /**
     * 【核心方法】对 chunk 列表进行向量化处理
     *
     * 处理流程：
     *   1. 过滤空白 chunk
     *   2. 按 EMBEDDING_BATCH_SIZE_LIMIT 分批
     *   3. 每批调用 EmbeddingModel.embed() 获取向量
     *   4. 将向量 + chunk 元数据写入 pgvector 表
     *   5. 标记 chunk 向量化状态为成功
     *
     * @param chunkList 需要向量化的 chunk 列表（子块）
     */
    @Override
    public void vectorize(List<KnowHubDocumentChunk> chunkList) {

        // 空列表直接返回，避免不必要的处理
        if (CollUtil.isEmpty(chunkList)) {
            return;
        }

        // 获取 EmbeddingModel 实例，如果不存在则抛出异常
        EmbeddingModel embeddingModel = requireEmbeddingModel();

        // 过滤掉 null 和空白文本的 chunk
        List<KnowHubDocumentChunk> validChunkList = chunkList.stream()
            .filter(chunk -> chunk != null && StrUtil.isNotBlank(chunk.getChunkText()))
            .toList();
        if (validChunkList.isEmpty()) {
            return;
        }

        // 动态生成完整的 UPSERT SQL（替换表名占位符）
        String upsertSql = UPSERT_SQL_TEMPLATE.formatted(DocumentPgVectorConstants.EMBEDDING_TABLE_NAME);
        int batchSize = EMBEDDING_BATCH_SIZE_LIMIT;
        String currentEmbeddingModelName = resolveEmbeddingModelName();
        int totalBatchCount = (validChunkList.size() + batchSize - 1) / batchSize;

        log.info("开始执行文档向量化，chunkCount={}, batchSize={}, batchCount={}, embeddingModel={}",
            validChunkList.size(), batchSize, totalBatchCount, currentEmbeddingModelName);

        // 分批处理：每批最多 EMBEDDING_BATCH_SIZE_LIMIT 个 chunk
        for (int startIndex = 0; startIndex < validChunkList.size(); startIndex += batchSize) {
            int endIndex = Math.min(startIndex + batchSize, validChunkList.size());
            List<KnowHubDocumentChunk> currentBatch = validChunkList.subList(startIndex, endIndex);
            int currentBatchIndex = (startIndex / batchSize) + 1;

            log.info("开始处理 embedding 批次，batchIndex={}/{}, chunkRange=[{}, {}], currentBatchSize={}",
                currentBatchIndex, totalBatchCount, startIndex + 1, endIndex, currentBatch.size());

            // 提取每个 chunk 的文本，调用 EmbeddingModel 批量生成向量
            List<float[]> embeddingList = embeddingModel.embed(currentBatch.stream()
                .map(KnowHubDocumentChunk::getChunkText)
                .toList());
            // 校验返回的向量数量与 chunk 数量一致
            if (embeddingList.size() != currentBatch.size()) {
                throw new IllegalStateException("EmbeddingModel 返回的向量数量与 chunk 数量不一致。");
            }

            // 将向量和 chunk 数据批量写入 pgvector 表
            batchUpsert(upsertSql, currentBatch, embeddingList, currentEmbeddingModelName);
            // 标记这批 chunk 的向量化状态为成功
            markSuccess(currentBatch);

            log.info("embedding 批次处理完成，batchIndex={}/{}, currentBatchSize={}",
                currentBatchIndex, totalBatchCount, currentBatch.size());
        }

        log.info("文档向量化执行完成，chunkCount={}, batchSize={}, batchCount={}, embeddingModel={}",
            validChunkList.size(), batchSize, totalBatchCount, currentEmbeddingModelName);
    }

    /**
     * 删除指定文档的所有向量数据。
     * 当文档被删除或需要重建索引时调用。
     *
     * @param documentId 文档ID
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }

        try {
            String deleteSql = DELETE_BY_DOCUMENT_SQL_TEMPLATE.formatted(DocumentPgVectorConstants.EMBEDDING_TABLE_NAME);
            pgVectorJdbcTemplate.update(deleteSql, documentId);
        }
        catch (Exception exception) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_VECTOR_FAILED.getCode(),
                "删除 PGVector 数据失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 批量将 chunk 数据和对应的向量写入 pgvector 表。
     *
     * 使用 JdbcTemplate.batchUpdate 配合 BatchPreparedStatementSetter 实现高效的批量写入。
     * 每个 chunk 对应一行 PreparedStatement 的参数设置。
     *
     * @param upsertSql    UPSERT SQL 语句
     * @param chunkBatch   当前批次的 chunk 列表
     * @param embeddingBatch 当前批次的向量列表（与 chunk 一一对应）
     * @param embeddingModelName 使用的 embedding 模型名称
     */
    private void batchUpsert(String upsertSql,
                             List<KnowHubDocumentChunk> chunkBatch,
                             List<float[]> embeddingBatch,
                             String embeddingModelName) {
        pgVectorJdbcTemplate.batchUpdate(upsertSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                KnowHubDocumentChunk chunk = chunkBatch.get(index);
                float[] embedding = embeddingBatch.get(index);

                // 标记向量化状态为"正在向量化"
                chunk.setVectorStatus(DocumentVectorStatusEnum.VECTORIZING.getCode());
                // 构建 JSON 格式的 metadata
                String metadataJson = buildMetadataJson(chunk, embeddingModelName);

                // 依次设置 PreparedStatement 的 19 个参数
                ps.setLong(1, chunk.getId());
                ps.setLong(2, chunk.getDocumentId());
                ps.setLong(3, chunk.getTaskId());
                // 可空字段需要特殊处理：null 时设置 SQL NULL
                if (chunk.getPlanId() == null) {
                    ps.setNull(4, Types.BIGINT);
                }
                else {
                    ps.setLong(4, chunk.getPlanId());
                }
                if (chunk.getParentBlockId() == null) {
                    ps.setNull(5, Types.BIGINT);
                }
                else {
                    ps.setLong(5, chunk.getParentBlockId());
                }
                ps.setInt(6, chunk.getChunkNo());
                ps.setInt(7, defaultInteger(chunk.getSourceType()));
                ps.setString(8, chunk.getSectionPath());
                if (chunk.getStructureNodeId() == null) {
                    ps.setNull(9, Types.BIGINT);
                }
                else {
                    ps.setLong(9, chunk.getStructureNodeId());
                }
                ps.setInt(10, defaultInteger(chunk.getStructureNodeType()));
                ps.setString(11, chunk.getCanonicalPath());
                ps.setInt(12, defaultInteger(chunk.getItemIndex()));
                ps.setString(13, chunk.getChunkText());
                ps.setInt(14, defaultInteger(chunk.getCharCount()));
                ps.setInt(15, defaultInteger(chunk.getTokenCount()));
                ps.setString(16, embeddingModelName);
                ps.setString(17, metadataJson);

                // 将 float[] 向量转为 pgvector 的字符串格式 "[0.1,0.2,0.3,...]"
                ps.setString(18, toVectorLiteral(embedding));
                // status = 1 表示有效
                ps.setInt(19, 1);
            }

            @Override
            public int getBatchSize() {
                return chunkBatch.size();
            }
        });
    }

    /**
     * 标记 chunk 列表的向量化状态为成功。
     * 设置 vectorId（使用 chunk 自身的 ID）、向量存储类型和状态。
     *
     * @param chunkBatch 已完成向量化的 chunk 列表
     */
    private void markSuccess(List<KnowHubDocumentChunk> chunkBatch) {
        for (KnowHubDocumentChunk chunk : chunkBatch) {

            chunk.setVectorId(String.valueOf(chunk.getId()));
            chunk.setVectorStoreType(DocumentVectorStoreTypeEnum.PG_VECTOR.getCode());
            chunk.setVectorStatus(DocumentVectorStatusEnum.VECTOR_SUCCESS.getCode());
        }
    }

    /**
     * 构建 chunk 的 metadata JSON 字符串。
     * 包含文档ID、任务ID、章节路径、节点类型等元信息，方便后续检索时做过滤和展示。
     *
     * @param chunk            chunk 实体
     * @param embeddingModelName 使用的模型名称
     * @return JSON 格式的 metadata 字符串
     */
    private String buildMetadataJson(KnowHubDocumentChunk chunk, String embeddingModelName) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("taskId", chunk.getTaskId());
        metadata.put("planId", chunk.getPlanId());
        metadata.put("parentBlockId", chunk.getParentBlockId());
        metadata.put("chunkNo", chunk.getChunkNo());
        metadata.put("sourceType", chunk.getSourceType());
        metadata.put("sectionPath", chunk.getSectionPath());
        metadata.put("structureNodeId", chunk.getStructureNodeId());
        metadata.put("structureNodeType", chunk.getStructureNodeType());
        metadata.put("canonicalPath", chunk.getCanonicalPath());
        metadata.put("itemIndex", chunk.getItemIndex());
        metadata.put("charCount", chunk.getCharCount());
        metadata.put("tokenCount", chunk.getTokenCount());
        metadata.put("embeddingModel", embeddingModelName);
        try {
            return objectMapper.writeValueAsString(metadata);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 PGVector metadata 失败。", exception);
        }
    }

    /**
     * 将 float[] 向量转为 pgvector 要求的字符串格式。
     * 例如：[0.123, 0.456, 0.789] -> "[0.123,0.456,0.789]"
     *
     * @param embedding 浮点向量数组
     * @return pgvector 兼容的字符串表示
     */
    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("EmbeddingModel 返回了空向量。");
        }
        StringBuilder vectorBuilder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {

            if (index > 0) {
                vectorBuilder.append(",");
            }
            vectorBuilder.append(embedding[index]);
        }
        vectorBuilder.append("]");
        return vectorBuilder.toString();
    }

    /**
     * 获取 EmbeddingModel 实例，不存在则抛异常。
     * @return EmbeddingModel 实例
     */
    private EmbeddingModel requireEmbeddingModel() {

        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException("当前未找到可用的 EmbeddingModel，无法执行向量化。");
        }
        return embeddingModel;
    }

    /**
     * 解析 embedding 模型名称，配置为空时返回 "default"。
     * @return 模型名称
     */
    private String resolveEmbeddingModelName() {

        return StrUtil.isNotBlank(embeddingModelName)
            ? embeddingModelName
            : "default";
    }

    /**
     * 安全的 Integer 转 int 方法，null 时返回 0。
     * @param value 可能为 null 的 Integer
     * @return int 值
     */
    private int defaultInteger(Integer value) {

        return Objects.requireNonNullElse(value, 0);
    }
}
