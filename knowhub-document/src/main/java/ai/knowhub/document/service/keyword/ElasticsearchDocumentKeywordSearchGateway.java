package ai.knowhub.document.service.keyword;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentChunk;
import ai.knowhub.document.mapper.KnowHubDocumentMapper;
import ai.knowhub.document.model.DocumentRetrieveFilters;
import ai.knowhub.document.model.DocumentRetrieveDto;
import ai.knowhub.document.model.es.DocumentKeywordIndexRecord;
import ai.knowhub.document.support.DocumentKnowledgeMetadataKeys;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 【Elasticsearch 文档关键词搜索网关实现】
 *
 * 作用：基于 Elasticsearch 实现 DocumentKeywordSearchGateway 接口，
 * 提供高性能的全文检索能力。是关键词检索通道的具体实现。
 *
 * 架构角色：
 *   - 属于 RAG 流水线的"关键词检索"通道的实现层
 *   - 使用 Elasticsearch Java Client（co.elastic.clients）与 ES 集群交互
 *   - 支持批量索引写入、复杂查询和按文档ID删除
 *
 * 核心功能：
 *   1. 索引写入：将文档分块批量写入 ES 索引
 *   2. 多字段检索：支持 sectionPath、chunkText、documentName 等多字段加权匹配
 *   3. 过滤查询：支持按文档ID、任务ID、章节路径、结构节点等条件过滤
 *   4. 短语匹配：支持精确的短语匹配，权重高于普通匹配
 *
 * 关键注解说明：
 *   - @Service: Spring 的服务组件注解，标记为 Spring Bean
 *   - @ConditionalOnProperty: 条件装配注解，只有当配置项
 *     app.manage.elasticsearch.enabled=true 时才创建此 Bean
 *   - @Qualifier: 指定注入的 Bean 名称，避免多个同类型 Bean 的冲突
 *   - @Slf4j: Lombok 注解，自动生成日志对象 log
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.manage.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchDocumentKeywordSearchGateway implements DocumentKeywordSearchGateway {

    /** Elasticsearch 客户端，用于与 ES 集群通信 */
    private final ElasticsearchClient elasticsearchClient;

    /** 文档数据库 Mapper，用于查询文档元数据 */
    private final KnowHubDocumentMapper documentMapper;

    /** 文档管理配置属性，包含 ES 索引名称等配置 */
    private final DocumentManageProperties properties;

    /**
     * 构造器注入依赖
     *
     * @param elasticsearchClient ES 客户端，通过 @Qualifier 指定使用 "documentManageElasticsearchClient" Bean
     * @param documentMapper      文档 Mapper
     * @param properties          配置属性
     */
    public ElasticsearchDocumentKeywordSearchGateway(
        @Qualifier("documentManageElasticsearchClient") ElasticsearchClient elasticsearchClient,
        KnowHubDocumentMapper documentMapper,
        DocumentManageProperties properties) {
        this.elasticsearchClient = elasticsearchClient;
        this.documentMapper = documentMapper;
        this.properties = properties;
    }

    /**
     * 批量索引文档分块到 Elasticsearch
     *
     * 功能说明：
     *   - 将文档分块列表转换为 ES 索引记录
     *   - 使用 Bulk API 批量写入，提高写入效率
     *   - 设置 Refresh.WaitFor 确保写入后立即可检索
     *   - 检查批量响应中的错误并抛出异常
     *
     * @param chunkList 文档分块列表
     */
    @Override
    public void indexChunks(List<KnowHubDocumentChunk> chunkList) {
        // 空列表直接返回，避免无意义的 ES 调用
        if (CollUtil.isEmpty(chunkList)) {
            return;
        }

        // 加载分块关联的文档信息，用于填充索引记录中的文档元数据
        Map<Long, KnowHubDocument> documentMap = loadDocumentMap(chunkList);
        // 构建批量请求
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder()
            .index(properties.getElasticsearch().getIndexName())
            .refresh(Refresh.WaitFor);

        // 将每个分块转换为索引记录并添加到批量请求中
        for (KnowHubDocumentChunk chunk : chunkList) {
            KnowHubDocument document = documentMap.get(chunk.getDocumentId());
            DocumentKeywordIndexRecord indexRecord = toIndexRecord(chunk, document);
            bulkBuilder.operations(operation -> operation
                .index(index -> index
                    .id(indexRecord.getChunkId())  // 使用分块ID作为 ES 文档ID
                    .document(indexRecord)           // 索引记录内容
                )
            );
        }

        try {
            // 执行批量写入
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
            // 检查是否有写入错误
            if (response.errors()) {
                String errorMessage = response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> item.id() + ":" + item.error().reason())
                    .collect(Collectors.joining("; "));
                throw new IllegalStateException("批量写入 Elasticsearch 失败: " + errorMessage);
            }
            log.info("文档 chunk 已同步写入 Elasticsearch: chunkCount={}, index={}",
                chunkList.size(), properties.getElasticsearch().getIndexName());
        }
        catch (IOException exception) {
            throw new IllegalStateException("写入 Elasticsearch 失败", exception);
        }
    }

    /**
     * 执行关键词检索
     *
     * 功能说明：
     *   - 构建复杂的 Elasticsearch bool 查询
     *   - filter 子句：必须匹配（文档ID、任务ID、章节路径等过滤条件）
     *   - should 子句：加分匹配（多字段加权、短语匹配等）
     *   - minimumShouldMatch("1")：至少命中一个 should 子句
     *
     * 查询策略：
     *   1. 短语匹配（matchPhrase）：权重最高，精确匹配查询文本
     *   2. 多字段匹配（multiMatch）：跨多个字段搜索，使用 BestFields 策略
     *   3. 业务分类/标签/文档名/章节路径等维度的额外加分
     *
     * @param request 检索请求
     * @return 匹配的文档列表
     */
    @Override
    public List<Document> search(DocumentRetrieveDto request) {
        // 校验请求是否可搜索
        if (!isSearchableRequest(request)) {
            return List.of();
        }

        // 将文档ID和任务ID转换为 ES 的 FieldValue 类型
        List<FieldValue> documentFieldValues = request.resolvedDocumentIds().stream()
            .map(FieldValue::of)
            .toList();
        List<FieldValue> taskFieldValues = request.resolvedTaskIds().stream()
            .map(FieldValue::of)
            .toList();

        String retrievalQuery = request.getRetrievalQuery().trim();
        DocumentRetrieveFilters filters = request.getFilters();
        List<String> queryContextHints = request.getQueryContextHints() == null ? List.of() : request.getQueryContextHints();

        try {
            SearchResponse<DocumentKeywordIndexRecord> response = elasticsearchClient.search(search -> search
                    .index(properties.getElasticsearch().getIndexName())
                    .size(resolveTopK(request.getTopK()))
                    .query(query -> query.bool(bool -> {

                        // ===== filter 子句：必须匹配的过滤条件 =====

                        // 按文档ID过滤：只检索指定文档的内容
                        bool.filter(filter -> filter.terms(terms -> terms
                            .field("documentId")
                            .terms(values -> values.value(documentFieldValues))
                        ));
                        // 按任务ID过滤：只检索指定任务的索引数据
                        bool.filter(filter -> filter.terms(terms -> terms
                            .field("taskId")
                            .terms(values -> values.value(taskFieldValues))
                        ));
                        // 按章节路径提示过滤（通配符匹配）
                        if (filters != null && CollUtil.isNotEmpty(filters.getSectionPathHints())) {
                            bool.filter(filter -> filter.bool(sectionBool -> {
                                for (String sectionHint : filters.getSectionPathHints()) {
                                    sectionBool.should(should -> should.wildcard(wildcard -> wildcard
                                        .field("sectionPath")
                                        .value("*" + sectionHint.toLowerCase(Locale.ROOT) + "*")
                                    ));
                                }
                                sectionBool.minimumShouldMatch("1");
                                return sectionBool;
                            }));
                        }
                        // 按规范路径提示过滤（前缀匹配）
                        if (filters != null && CollUtil.isNotEmpty(filters.getCanonicalPathHints())) {
                            bool.filter(filter -> filter.bool(pathBool -> {
                                for (String pathHint : filters.getCanonicalPathHints()) {
                                    pathBool.should(should -> should.wildcard(wildcard -> wildcard
                                        .field("canonicalPath")
                                        .value(pathHint + "*")
                                    ));
                                }
                                pathBool.minimumShouldMatch("1");
                                return pathBool;
                            }));
                        }
                        // 按结构节点ID过滤
                        if (filters != null && CollUtil.isNotEmpty(filters.getStructureNodeIdHints())) {
                            List<FieldValue> structureNodeValues = filters.getStructureNodeIdHints().stream()
                                .map(FieldValue::of)
                                .toList();
                            bool.filter(filter -> filter.terms(terms -> terms
                                .field("structureNodeId")
                                .terms(values -> values.value(structureNodeValues))
                            ));
                        }
                        // 按内容项索引过滤
                        if (filters != null && CollUtil.isNotEmpty(filters.getItemIndexHints())) {
                            List<FieldValue> itemIndexValues = filters.getItemIndexHints().stream()
                                .map(FieldValue::of)
                                .toList();
                            bool.filter(filter -> filter.terms(terms -> terms
                                .field("itemIndex")
                                .terms(values -> values.value(itemIndexValues))
                            ));
                        }

                        // ===== should 子句：加分匹配条件 =====

                        // 章节路径短语匹配（权重 8.0）
                        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
                            .field("sectionPath")
                            .query(retrievalQuery)
                            .boost(8.0f)
                        ));
                        // 文本内容短语匹配（权重 5.0）
                        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
                            .field("chunkText")
                            .query(retrievalQuery)
                            .boost(5.0f)
                        ));
                        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
                            .field("chunkText.english")
                            .query(retrievalQuery)
                            .boost(5.5f)
                        ));
                        // 文档名短语匹配（权重 4.0）
                        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
                            .field("documentName")
                            .query(retrievalQuery)
                            .boost(4.0f)
                        ));
                        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
                            .field("documentName.raw")
                            .query(retrievalQuery)
                            .boost(9.0f)
                        ));
                        // 多字段最佳匹配（sectionPath^6, documentName^4, knowledgeScopeName^3, chunkText）
                        bool.should(should -> should.multiMatch(multiMatch -> multiMatch
                            .query(retrievalQuery)
                            .fields(
                                "sectionPath^6",
                                "sectionPath.standard^7",
                                "sectionPath.english^7",
                                "sectionPath.raw^8",
                                "documentName^4",
                                "documentName.standard^6",
                                "documentName.english^6",
                                "documentName.raw^9",
                                "knowledgeScopeName^3",
                                "knowledgeScopeName.standard^4",
                                "knowledgeScopeName.english^4",
                                "chunkText",
                                "chunkText.standard^2",
                                "chunkText.english^3")
                            .type(TextQueryType.BestFields)
                        ));
                        // 业务分类匹配（如果提供了业务分类提示）
                        if (filters != null && CollUtil.isNotEmpty(filters.getBusinessCategoryHints())) {
                            bool.should(should -> should.multiMatch(multiMatch -> multiMatch
                                .query(String.join(" ", filters.getBusinessCategoryHints()))
                                .fields("businessCategory^5", "knowledgeScopeName^2", "knowledgeScopeName.standard^3", "knowledgeScopeName.english^3")
                                .type(TextQueryType.BestFields)
                            ));
                        }
                        // 文档标签匹配
                        if (filters != null && CollUtil.isNotEmpty(filters.getDocumentTagHints())) {
                            bool.should(should -> should.multiMatch(multiMatch -> multiMatch
                                .query(String.join(" ", filters.getDocumentTagHints()))
                                .fields("documentTags^4", "documentName^2", "documentName.standard^3", "documentName.english^3", "chunkText", "chunkText.english^2")
                                .type(TextQueryType.BestFields)
                            ));
                        }
                        // 文档名匹配
                        if (filters != null && CollUtil.isNotEmpty(filters.getDocumentNameHints())) {
                            bool.should(should -> should.multiMatch(multiMatch -> multiMatch
                                .query(String.join(" ", filters.getDocumentNameHints()))
                                .fields("documentName^6", "documentName.standard^7", "documentName.english^7", "documentName.raw^10", "sectionPath^2", "sectionPath.raw^4", "chunkText")
                                .type(TextQueryType.BestFields)
                            ));
                        }
                        // 章节路径匹配
                        if (filters != null && CollUtil.isNotEmpty(filters.getSectionPathHints())) {
                            bool.should(should -> should.multiMatch(multiMatch -> multiMatch
                                .query(String.join(" ", filters.getSectionPathHints()))
                                .fields("sectionPath^7", "sectionPath.standard^8", "sectionPath.english^8", "sectionPath.raw^10", "chunkText", "chunkText.english^2")
                                .type(TextQueryType.BestFields)
                            ));
                        }
                        // 查询上下文提示匹配
                        if (CollUtil.isNotEmpty(queryContextHints)) {
                            bool.should(should -> should.multiMatch(multiMatch -> multiMatch
                                .query(String.join(" ", queryContextHints))
                                .fields("documentName^2", "documentName.english^3", "knowledgeScopeName^2", "knowledgeScopeName.english^3", "sectionPath^2", "sectionPath.english^3", "chunkText", "chunkText.english^2")
                                .type(TextQueryType.BestFields)
                            ));
                        }
                        // 至少命中一个 should 子句
                        bool.minimumShouldMatch("1");
                        return bool;
                    })),
                DocumentKeywordIndexRecord.class);

            // 将 ES 搜索结果转换为 Spring AI Document 对象
            List<Document> result = new ArrayList<>();
            for (Hit<DocumentKeywordIndexRecord> hit : response.hits().hits()) {
                DocumentKeywordIndexRecord source = hit.source();
                if (source == null) {
                    continue;
                }
                result.add(toSpringDocument(source, hit.score()));
            }
            return result;
        }
        catch (IOException exception) {
            log.error("Elasticsearch 关键词检索失败, retrievalQuery={}", retrievalQuery, exception);
            return List.of();
        }
    }

    /**
     * 删除指定文档在 ES 中的所有索引数据
     *
     * @param documentId 文档ID
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            // 使用 deleteByQuery 按文档ID批量删除
            elasticsearchClient.deleteByQuery(delete -> delete
                .index(properties.getElasticsearch().getIndexName())
                .refresh(true)
                .query(query -> query.term(term -> term
                    .field("documentId")
                    .value(documentId)
                ))
            );
        }
        catch (IOException exception) {
            throw new IllegalStateException("删除 Elasticsearch 文档失败", exception);
        }
    }

    /**
     * 加载分块关联的文档信息
     *
     * @param chunkList 分块列表
     * @return 文档ID到文档对象的映射
     */
    private Map<Long, KnowHubDocument> loadDocumentMap(List<KnowHubDocumentChunk> chunkList) {
        List<Long> documentIds = chunkList.stream()
            .map(KnowHubDocumentChunk::getDocumentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        // 批量查询文档信息
        List<KnowHubDocument> documents = documentMapper.selectBatchIds(documentIds);
        Map<Long, KnowHubDocument> documentMap = new LinkedHashMap<>();
        for (KnowHubDocument document : documents) {
            documentMap.put(document.getId(), document);
        }
        return documentMap;
    }

    /**
     * 将分块和文档信息转换为 ES 索引记录
     *
     * @param chunk    文档分块
     * @param document 关联的文档
     * @return ES 索引记录
     */
    private DocumentKeywordIndexRecord toIndexRecord(KnowHubDocumentChunk chunk, KnowHubDocument document) {
        return DocumentKeywordIndexRecord.builder()
            .chunkId(String.valueOf(chunk.getId()))
            .documentId(chunk.getDocumentId())
            .taskId(chunk.getTaskId())
            .parentBlockId(chunk.getParentBlockId())
            .chunkNo(chunk.getChunkNo())
            .documentName(document == null ? "" : safeText(document.getDocumentName()))
            .sectionPath(safeText(chunk.getSectionPath()))
            .structureNodeId(chunk.getStructureNodeId())
            .structureNodeType(chunk.getStructureNodeType())
            .canonicalPath(safeText(chunk.getCanonicalPath()))
            .itemIndex(chunk.getItemIndex())
            .knowledgeScopeCode(document == null ? "" : safeText(document.getKnowledgeScopeCode()))
            .knowledgeScopeName(document == null ? "" : safeText(document.getKnowledgeScopeName()))
            .businessCategory(document == null ? "" : safeText(document.getBusinessCategory()))
            .documentTags(splitTags(document == null ? "" : document.getDocumentTags()))
            .chunkText(safeText(chunk.getChunkText()))
            .build();
    }

    /**
     * 将 ES 索引记录转换为 Spring AI Document 对象
     *
     * 功能说明：
     *   - 构建 Document 的 metadata 映射，包含所有元数据字段
     *   - 设置检索得分（score），用于结果排序
     *
     * @param source ES 索引记录
     * @param score  检索得分
     * @return Spring AI Document 对象
     */
    private Document toSpringDocument(DocumentKeywordIndexRecord source, Double score) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DocumentKnowledgeMetadataKeys.SOURCE_TYPE, "DOCUMENT");
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL, "keyword");
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, score == null ? 0D : score.doubleValue());
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_ID, parseLong(source.getChunkId()));
        metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_ID, source.getDocumentId());
        metadata.put(DocumentKnowledgeMetadataKeys.TASK_ID, source.getTaskId());
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID, source.getParentBlockId());
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_NO, source.getChunkNo());
        metadata.put(DocumentKnowledgeMetadataKeys.SECTION_PATH, safeText(source.getSectionPath()));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID, source.getStructureNodeId());
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_TYPE, source.getStructureNodeType());
        metadata.put(DocumentKnowledgeMetadataKeys.CANONICAL_PATH, safeText(source.getCanonicalPath()));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.ITEM_INDEX, source.getItemIndex());
        metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME, safeText(source.getDocumentName()));
        metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_CODE, safeText(source.getKnowledgeScopeCode()));
        metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_NAME, safeText(source.getKnowledgeScopeName()));
        metadata.put(DocumentKnowledgeMetadataKeys.BUSINESS_CATEGORY, safeText(source.getBusinessCategory()));
        metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_TAGS, String.join(",", source.getDocumentTags()));

        return Document.builder()
            .id(source.getChunkId())
            .text(source.getChunkText())
            .metadata(metadata)
            .score(score == null ? 0D : score.doubleValue())
            .build();
    }

    /**
     * 校验检索请求是否有效
     *
     * @param request 检索请求
     * @return true 表示请求有效，可以执行检索
     */
    private boolean isSearchableRequest(DocumentRetrieveDto request) {
        return request != null
            && StrUtil.isNotBlank(request.getQuestion())
            && StrUtil.isNotBlank(request.getRetrievalQuery())
            && !request.resolvedDocumentIds().isEmpty()
            && !request.resolvedTaskIds().isEmpty();
    }

    /**
     * 向 metadata 中添加非空值
     */
    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    /**
     * 解析返回数量限制，默认 10，最大 50
     */
    private int resolveTopK(int topK) {
        return topK <= 0 ? 10 : Math.min(topK, 50);
    }

    /**
     * 将逗号分隔的标签字符串拆分为列表
     */
    private List<String> splitTags(String documentTags) {
        if (StrUtil.isBlank(documentTags)) {
            return List.of();
        }
        return Arrays.stream(documentTags.split(","))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .toList();
    }

    /**
     * 安全地将字符串解析为 Long 类型
     */
    private Long parseLong(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 空值安全的文本处理，null 转为空字符串
     */
    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
