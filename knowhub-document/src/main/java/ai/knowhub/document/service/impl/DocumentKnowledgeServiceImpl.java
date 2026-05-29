package ai.knowhub.document.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentParentBlock;
import ai.knowhub.document.mapper.KnowHubDocumentMapper;
import ai.knowhub.document.mapper.KnowHubDocumentParentBlockMapper;
import ai.knowhub.document.model.DocumentRetrieveFilters;
import ai.knowhub.document.model.DocumentRetrieveDto;
import ai.knowhub.document.model.KnowledgeDocumentDescriptor;
import ai.knowhub.document.service.DocumentKnowledgeService;
import ai.knowhub.document.service.keyword.DocumentKeywordSearchGateway;
import ai.knowhub.document.support.DocumentKnowledgeMetadataKeys;
import ai.knowhub.document.support.DocumentPgVectorConstants;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.enums.DocumentIndexStatusEnum;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 【文档知识检索服务实现】
 *
 * 这个类是 DocumentKnowledgeService 接口的核心实现，负责从已索引的文档中检索与用户问题相关的知识片段。
 * 它是 RAG（检索增强生成）流程中"检索"阶段的核心组件。
 *
 * 核心能力：
 *   1. 向量检索（vectorSearch）：将用户问题转为 embedding 向量，通过 pgvector 的余弦距离运算找到语义最相似的 chunk
 *   2. 关键词检索（keywordSearch）：优先走 Elasticsearch 全文搜索，未启用时用 SQL LIKE + 加权表达式兜底
 *   3. 父块提升（elevateToParentBlocks）：检索命中的是子块，但回答需要上下文，将同一父块下命中的子块提升为父块证据
 *
 * 检索流程（以向量检索为例）：
 *   1. 校验请求参数是否合法
 *   2. 用 EmbeddingModel 将查询文本转为向量
 *   3. 解析过滤条件（文档ID、任务ID、章节路径等）
 *   4. 构建 pgvector SQL，通过 1 - (embedding <=> query_vector) 计算余弦相似度
 *   5. 按相似度排序，取 Top-K 结果
 *   6. 将结果封装为 Spring AI 的 Document 对象（携带文本 + metadata）
 *
 * Parent-Child 设计：
 *   - 索引时将文档切成"父块"（大段落，提供上下文）和"子块"（小片段，精确匹配）
 *   - 检索时命中子块，但最终发给 LLM 的是父块（包含完整上下文）
 *   - 如果同一父块下有多个子块命中，父块得分会叠加，且标注为 "hybrid" 渠道
 *
 * 中文关键词提取：
 *   - 使用 n-gram 策略从中文文本中提取 2-4 字的关键词片段
 *   - 去除常见停用词（"请问"、"帮我"、"如何"等）
 *   - 限制最多提取 8 个关键词，避免 SQL 过长
 *
 * 设计模式：Gateway 模式（向量存储通过 JdbcTemplate 直接操作 pgvector），
 *           策略模式（关键词检索优先 ES，降级到 SQL LIKE）
 */
@Slf4j
@AllArgsConstructor
@Service
public class DocumentKnowledgeServiceImpl implements DocumentKnowledgeService {

    /**
     * 向量检索 SQL 模板。
     * 通过 pgvector 的 <=> 运算符计算余弦距离，1 - distance 即为余弦相似度。
     * %s 占位符分别替换为：表名、文档ID列表、任务ID列表。
     */
    private static final String VECTOR_RETRIEVE_SQL_TEMPLATE = """
        SELECT
            id,
            document_id,
            task_id,
            parent_block_id,
            chunk_no,
            section_path,
            structure_node_id,
            structure_node_type,
            canonical_path,
            item_index,
            chunk_text,
            1 - (embedding <=> CAST(? AS vector)) AS similarity_score
        FROM %s
        WHERE status = 1
          AND document_id IN (%s)
          AND task_id IN (%s)
        """;

    /**
     * 关键词检索 SQL 模板（SQL LIKE 兜底方案）。
     * %s 占位符分别替换为：加权评分表达式、表名、文档ID列表、任务ID列表、关键词过滤条件。
     */
    private static final String KEYWORD_RETRIEVE_SQL_TEMPLATE = """
        SELECT
            id,
            document_id,
            task_id,
            parent_block_id,
            chunk_no,
            section_path,
            structure_node_id,
            structure_node_type,
            canonical_path,
            item_index,
            chunk_text,
            (%s) AS keyword_score
        FROM %s
        WHERE status = 1
          AND document_id IN (%s)
          AND task_id IN (%s)
          AND (%s)
        """;

    /** 英文单词/数字 token 的正则模式（至少2个字符） */
    private static final Pattern ALNUM_TOKEN_PATTERN = Pattern.compile("[a-z0-9._-]{2,}");

    /** 中文字符 token 的正则模式（至少2个连续汉字） */
    private static final Pattern CHINESE_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}");

    /** 中文停用词列表，这些词在关键词提取时会被去除 */
    private static final List<String> CHINESE_NOISE_PHRASES = List.of(
        "请问", "帮我", "一下子", "一下", "如何", "怎么", "什么", "哪个", "这个", "那个", "是否", "关于", "可以", "需要", "想问", "看看"
    );

    /** 中文分词分隔符模式（"的"、"和"、"及"、"与"、"或"等连接词） */
    private static final Pattern CHINESE_SEGMENT_SPLIT_PATTERN = Pattern.compile("[的和及与或]");

    /** 最大关键词数量限制，避免 SQL 过长影响性能 */
    private static final int MAX_KEYWORD_TERMS = 8;

    /** MyBatis-Plus 文档 Mapper */
    private final KnowHubDocumentMapper documentMapper;

    /** MyBatis-Plus 父块 Mapper */
    private final KnowHubDocumentParentBlockMapper parentBlockMapper;

    /** PostgreSQL pgvector 的 JdbcTemplate（通过 @Qualifier 注入指定数据源） */
    @Qualifier("documentManagePgVectorJdbcTemplate")
    private final JdbcTemplate pgVectorJdbcTemplate;

    /** Embedding 模型的延迟提供者（可能未配置，需要时才获取） */
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    /** 关键词搜索网关的延迟提供者（Elasticsearch 实现，可能未启用） */
    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;

    /** 文档管理配置属性（包含 Elasticsearch 开关等配置） */
    private final DocumentManageProperties properties;

    /**
     * 列出所有可检索的文档描述信息。
     * 只有索引构建成功（BUILD_SUCCESS）且有 lastIndexTaskId 的文档才会被返回。
     *
     * @return 可检索文档的描述信息列表
     */
    @Override
    public List<KnowledgeDocumentDescriptor> listRetrievableDocuments() {

        // 只有索引构建成功且有 lastIndexTaskId 的文档，才允许进入聊天侧检索范围。
        List<KnowHubDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KnowHubDocument>()
            .eq(KnowHubDocument::getStatus, BusinessStatus.YES.getCode())
            .eq(KnowHubDocument::getIndexStatus, DocumentIndexStatusEnum.BUILD_SUCCESS.getCode())
            .isNotNull(KnowHubDocument::getLastIndexTaskId)
            .orderByDesc(KnowHubDocument::getEditTime)
            .orderByDesc(KnowHubDocument::getId));
        if (CollUtil.isEmpty(documents)) {
            return List.of();
        }

        return documents.stream()
            .map(document -> new KnowledgeDocumentDescriptor(
                document.getId(),
                document.getDocumentName(),
                document.getLastIndexTaskId(),
                document.getKnowledgeScopeCode(),
                document.getKnowledgeScopeName(),
                document.getBusinessCategory(),
                document.getDocumentTags()
            ))
            .toList();
    }

    /**
     * 向量检索：将查询文本转为 embedding，通过 pgvector 的余弦距离找到最相似的 chunk。
     *
     * @param request 检索请求，包含问题文本、文档ID列表、任务ID列表、TopK等参数
     * @return 检索到的 Document 列表（包含文本和 metadata）
     */
    @Override
    public List<Document> vectorSearch(DocumentRetrieveDto request) {
        if (!isSearchableRequest(request)) {
            return List.of();
        }

        // 向量检索先把查询文本转成 embedding，再用 pgvector 的距离运算找相似 chunk。
        EmbeddingModel embeddingModel = requireEmbeddingModel();

        // 将查询文本转为向量字面量格式 "[0.1, 0.2, ...]"
        String questionVector = toVectorLiteral(embeddingModel.embed(request.getRetrievalQuery().trim()));
        List<Long> documentIds = request.resolvedDocumentIds();
        List<Long> taskIds = request.resolvedTaskIds();

        // 获取文档描述信息，用于在结果中附带文档名称、知识范围等元数据
        Map<Long, KnowledgeDocumentDescriptor> descriptorMap = listDescriptorMap(documentIds);

        // 解析过滤条件（章节路径、结构节点ID、规范路径、条目索引等）
        ResolvedMetadataScope resolvedScope = resolveMetadataScope(request);
        if (resolvedScope.documentIds().isEmpty() || resolvedScope.taskIds().isEmpty()) {
            return List.of();
        }

        // 构建 SQL，使用 IN 子句限定文档ID和任务ID范围
        StringBuilder sqlBuilder = new StringBuilder(VECTOR_RETRIEVE_SQL_TEMPLATE.formatted(
            DocumentPgVectorConstants.EMBEDDING_TABLE_NAME,
            buildPlaceholders(resolvedScope.documentIds().size()),
            buildPlaceholders(resolvedScope.taskIds().size())
        ));
        // 追加章节路径和结构过滤条件
        appendSectionFilters(sqlBuilder, resolvedScope.filters());

        // 按向量距离排序，取 Top-K
        sqlBuilder.append("""

            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
            """);

        // 组装参数列表（注意顺序必须与 SQL 中的 ? 占位符一致）
        List<Object> params = new ArrayList<>();

        params.add(questionVector);                              // 第一个 ? 是查询向量（用于距离计算）
        params.addAll(resolvedScope.documentIds());               // IN 子句中的文档ID
        params.addAll(resolvedScope.taskIds());                   // IN 子句中的任务ID
        appendSectionFilterParams(params, resolvedScope.filters()); // 章节过滤参数
        params.add(questionVector);                              // ORDER BY 中的查询向量
        params.add(resolveTopK(request.getTopK()));              // LIMIT 数量

        // 执行查询并映射结果
        return pgVectorJdbcTemplate.query(sqlBuilder.toString(), params.toArray(), (resultSet, rowNum) -> {
            long chunkId = resultSet.getLong("id");
            long documentId = resultSet.getLong("document_id");
            double score = resultSet.getDouble("similarity_score");
            KnowledgeDocumentDescriptor descriptor = descriptorMap.get(documentId);
            return buildRetrievedDocument(
                chunkId,
                resultSet.getString("chunk_text"),
                resultSet.getLong("task_id"),
                resultSet.getLong("parent_block_id"),
                resultSet.getInt("chunk_no"),
                resultSet.getString("section_path"),
                getNullableLong(resultSet, "structure_node_id"),
                getNullableInteger(resultSet, "structure_node_type"),
                resultSet.getString("canonical_path"),
                getNullableInteger(resultSet, "item_index"),
                descriptor,
                "vector",
                score
            );
        });
    }

    /**
     * 关键词检索：优先走 Elasticsearch 全文搜索，未启用时用 SQL LIKE + 加权表达式兜底。
     *
     * @param request 检索请求
     * @return 检索到的 Document 列表
     */
    @Override
    public List<Document> keywordSearch(DocumentRetrieveDto request) {
        if (!isSearchableRequest(request)) {
            return List.of();
        }

        // 关键词检索优先走 Elasticsearch；没有启用时，用数据库 LIKE 和加权表达式兜底。
        List<Long> documentIds = request.resolvedDocumentIds();
        List<Long> taskIds = request.resolvedTaskIds();
        Map<Long, KnowledgeDocumentDescriptor> descriptorMap = listDescriptorMap(documentIds);
        ResolvedMetadataScope resolvedScope = resolveMetadataScope(request);
        if (resolvedScope.documentIds().isEmpty() || resolvedScope.taskIds().isEmpty()) {
            return List.of();
        }

        // 构建过滤后的请求对象
        DocumentRetrieveDto filteredRequest = new DocumentRetrieveDto(
            request.getQuestion(),
            request.getRetrievalQuery(),
            resolvedScope.documentIds().isEmpty() ? null : resolvedScope.documentIds().get(0),
            resolvedScope.taskIds().isEmpty() ? null : resolvedScope.taskIds().get(0),
            request.getTopK(),
            resolvedScope.filters(),
            request.getQueryContextHints()
        );
        filteredRequest.setDocumentIds(resolvedScope.documentIds());
        filteredRequest.setTaskIds(resolvedScope.taskIds());

        // 如果 Elasticsearch 启用且有可用的关键词搜索网关，走 ES 搜索
        DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
        if (Boolean.TRUE.equals(properties.getElasticsearch().getEnabled()) && keywordSearchGateway != null) {
            return keywordSearchGateway.search(filteredRequest);
        }

        // Elasticsearch 不可用时，使用 SQL LIKE 兜底
        // 从查询文本和上下文提示中提取关键词
        List<String> terms = new ArrayList<>(extractKeywordTerms(request.getRetrievalQuery()));
        terms.addAll(extractAuxiliaryKeywordTerms(request.getQueryContextHints()));
        terms = new ArrayList<>(new LinkedHashSet<>(terms)); // 去重
        if (terms.isEmpty()) {
            return List.of();
        }

        // 构建加权评分表达式：每个关键词在 chunk_text 和 section_path 中的匹配分别赋予不同权重
        String scoreExpression = buildKeywordScoreExpression(terms.size());
        String whereExpression = buildKeywordWhereExpression(terms.size());
        StringBuilder sqlBuilder = new StringBuilder(KEYWORD_RETRIEVE_SQL_TEMPLATE.formatted(
            scoreExpression,
            DocumentPgVectorConstants.EMBEDDING_TABLE_NAME,
            buildPlaceholders(resolvedScope.documentIds().size()),
            buildPlaceholders(resolvedScope.taskIds().size()),
            whereExpression
        ));
        appendSectionFilters(sqlBuilder, resolvedScope.filters());
        sqlBuilder.append("""

            ORDER BY keyword_score DESC, chunk_no ASC, id ASC
            LIMIT ?
            """);

        List<Object> params = new ArrayList<>();

        // 为每个关键词组装评分参数（chunk_text LIKE 权重 + section_path LIKE 权重）
        for (int index = 0; index < terms.size(); index++) {
            String pattern = likePattern(terms.get(index));

            params.add(pattern);                  // chunk_text LIKE 参数
            params.add(keywordWeight(index));     // chunk_text 匹配权重（越靠前的关键词权重越高）
            params.add(pattern);                  // section_path LIKE 参数
            params.add(sectionKeywordWeight(index)); // section_path 匹配权重（路径匹配权重更高）
        }

        params.addAll(resolvedScope.documentIds());
        params.addAll(resolvedScope.taskIds());

        // WHERE 子句中的 LIKE 参数
        for (String term : terms) {
            params.add(likePattern(term));
            params.add(likePattern(term));
        }
        appendSectionFilterParams(params, resolvedScope.filters());
        params.add(resolveTopK(request.getTopK()));

        // 执行查询并映射结果
        return pgVectorJdbcTemplate.query(sqlBuilder.toString(), params.toArray(), (resultSet, rowNum) -> {
            long chunkId = resultSet.getLong("id");
            long documentId = resultSet.getLong("document_id");
            double score = resultSet.getDouble("keyword_score");
            KnowledgeDocumentDescriptor descriptor = descriptorMap.get(documentId);
            return buildRetrievedDocument(
                chunkId,
                resultSet.getString("chunk_text"),
                resultSet.getLong("task_id"),
                resultSet.getLong("parent_block_id"),
                resultSet.getInt("chunk_no"),
                resultSet.getString("section_path"),
                getNullableLong(resultSet, "structure_node_id"),
                getNullableInteger(resultSet, "structure_node_type"),
                resultSet.getString("canonical_path"),
                getNullableInteger(resultSet, "item_index"),
                descriptor,
                "keyword",
                score
            );
        });
    }

    /**
     * 将子块检索结果提升为父块证据。
     *
     * 背景：检索命中的是小的子块（精确匹配），但 LLM 回答问题时需要更大的上下文。
     * 这个方法将同一父块下命中的多个子块合并，用父块的完整文本替代子块文本。
     *
     * 提升逻辑：
     *   1. 按 parentBlockId 分组子块
     *   2. 从数据库加载父块信息
     *   3. 对于每个父块，取最高分子块的 metadata 作为基础
     *   4. 计算聚合得分：bestScore * (1 + supportWeight + multiChannelWeight)
     *      - supportWeight：每多一个子块命中 +0.12，最多 +0.36
     *      - multiChannelWeight：如果命中了多个检索渠道（vector + keyword），额外 +0.10
     *   5. 渲染父块证据文本：[父块内容] + [命中子片段摘要]
     *
     * @param childDocuments 子块检索结果列表
     * @param maxChars       父块证据文本的最大字符数
     * @return 提升后的 Document 列表（文本为父块内容）
     */
    @Override
    public List<Document> elevateToParentBlocks(List<Document> childDocuments, int maxChars) {
        if (CollUtil.isEmpty(childDocuments)) {
            return List.of();
        }

        // 检索命中的是子块，但回答需要上下文；这里把同一父块下命中的子块提升为父块证据。
        Map<Long, List<Document>> childGroupsByParent = new LinkedHashMap<>();
        List<Document> fallbackDocuments = new ArrayList<>();
        for (Document childDocument : childDocuments) {
            if (childDocument == null) {
                continue;
            }
            Long parentBlockId = asLong(childDocument.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID));
            if (parentBlockId == null) {
                // 没有父块ID的子块直接保留
                fallbackDocuments.add(childDocument);
                continue;
            }
            childGroupsByParent.computeIfAbsent(parentBlockId, ignored -> new ArrayList<>()).add(childDocument);
        }

        if (childGroupsByParent.isEmpty()) {
            return fallbackDocuments;
        }

        // 从数据库批量加载父块信息
        List<Long> parentBlockIds = new ArrayList<>(childGroupsByParent.keySet());
        Map<Long, KnowHubDocumentParentBlock> parentBlockMap = parentBlockMapper.selectList(
                new LambdaQueryWrapper<KnowHubDocumentParentBlock>()
                    .in(KnowHubDocumentParentBlock::getId, parentBlockIds)
                    .eq(KnowHubDocumentParentBlock::getStatus, BusinessStatus.YES.getCode())
                    .orderByAsc(KnowHubDocumentParentBlock::getParentNo)
            ).stream()
            .collect(Collectors.toMap(
                KnowHubDocumentParentBlock::getId,
                parent -> parent,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        // 为每个父块构建提升后的证据文档
        List<Document> elevatedDocuments = new ArrayList<>(childGroupsByParent.size() + fallbackDocuments.size());
        for (Map.Entry<Long, List<Document>> entry : childGroupsByParent.entrySet()) {
            KnowHubDocumentParentBlock parentBlock = parentBlockMap.get(entry.getKey());
            if (parentBlock == null) {
                // 父块不存在时，保留原始子块
                elevatedDocuments.addAll(entry.getValue());
                continue;
            }
            elevatedDocuments.add(buildParentEvidenceDocument(parentBlock, entry.getValue(), maxChars));
        }
        elevatedDocuments.addAll(fallbackDocuments);
        // 按得分降序、父块编号升序、chunk编号升序排序
        elevatedDocuments.sort(this::compareEvidenceDocument);
        return elevatedDocuments;
    }

    /**
     * 构建检索结果的 Document 对象。
     * Spring AI 的 Document 同时携带文本和 metadata，后续引用编号、过滤、排序都依赖 metadata。
     *
     * @param chunkId    chunk 的数据库ID
     * @param chunkText  chunk 的文本内容
     * @param taskId     索引构建任务ID
     * @param parentBlockId 所属父块ID
     * @param chunkNo    chunk 编号
     * @param sectionPath 章节路径
     * @param structureNodeId 结构节点ID（可空）
     * @param structureNodeType 结构节点类型（可空）
     * @param canonicalPath 规范路径
     * @param itemIndex  条目索引（可空）
     * @param descriptor 文档描述信息
     * @param channel    检索渠道（"vector" 或 "keyword"）
     * @param score      检索得分
     * @return 封装好的 Document 对象
     */
    private Document buildRetrievedDocument(long chunkId,
                                            String chunkText,
                                            long taskId,
                                            long parentBlockId,
                                            int chunkNo,
                                            String sectionPath,
                                            Long structureNodeId,
                                            Integer structureNodeType,
                                            String canonicalPath,
                                            Integer itemIndex,
                                            KnowledgeDocumentDescriptor descriptor,
                                            String channel,
                                            double score) {
        // Spring AI 的 Document 同时携带文本和 metadata，后续引用编号、过滤、排序都依赖 metadata。
        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put(DocumentKnowledgeMetadataKeys.SOURCE_TYPE, "DOCUMENT");
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL, channel);
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, score);
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_ID, chunkId);
        metadata.put(DocumentKnowledgeMetadataKeys.TASK_ID, taskId);
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID, parentBlockId);
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_NO, chunkNo);
        metadata.put(DocumentKnowledgeMetadataKeys.SECTION_PATH, safeText(sectionPath));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID, structureNodeId);
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_TYPE, structureNodeType);
        metadata.put(DocumentKnowledgeMetadataKeys.CANONICAL_PATH, safeText(canonicalPath));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.ITEM_INDEX, itemIndex);
        metadata.put(DocumentKnowledgeMetadataKeys.ORIGINAL_SNIPPET, chunkText);
        if (descriptor != null) {

            metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_ID, descriptor.getDocumentId());
            metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME, safeText(descriptor.getDocumentName()));
            metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_CODE, safeText(descriptor.getKnowledgeScopeCode()));
            metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_NAME, safeText(descriptor.getKnowledgeScopeName()));
            metadata.put(DocumentKnowledgeMetadataKeys.BUSINESS_CATEGORY, safeText(descriptor.getBusinessCategory()));
            metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_TAGS, safeText(descriptor.getDocumentTags()));
        }

        return Document.builder()
            .id(String.valueOf(chunkId))
            .text(chunkText)
            .metadata(metadata)
            .score(score)
            .build();
    }

    /**
     * 校验检索请求是否合法。
     * 要求：question 和 retrievalQuery 非空，且文档ID和任务ID列表不为空。
     */
    private boolean isSearchableRequest(DocumentRetrieveDto request) {

        if (request == null || StrUtil.isBlank(request.getQuestion()) || StrUtil.isBlank(request.getRetrievalQuery())) {
            return false;
        }
        return !request.resolvedDocumentIds().isEmpty() && !request.resolvedTaskIds().isEmpty();
    }

    /**
     * 构建文档ID到文档描述信息的映射。
     * 用于在检索结果中附带文档名称、知识范围等元数据。
     */
    private Map<Long, KnowledgeDocumentDescriptor> listDescriptorMap(List<Long> requestedDocumentIds) {
        List<KnowledgeDocumentDescriptor> descriptors = listRetrievableDocuments();
        if (descriptors.isEmpty()) {
            return Map.of();
        }

        return descriptors.stream()
            .filter(descriptor -> requestedDocumentIds.contains(descriptor.getDocumentId()))
            .collect(Collectors.toMap(
                KnowledgeDocumentDescriptor::getDocumentId,
                descriptor -> descriptor,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    /**
     * 解析元数据作用域，提取文档ID、任务ID和过滤条件。
     */
    private ResolvedMetadataScope resolveMetadataScope(DocumentRetrieveDto request) {
        List<Long> baseDocumentIds = request.resolvedDocumentIds();
        List<Long> baseTaskIds = request.resolvedTaskIds();
        return new ResolvedMetadataScope(baseDocumentIds, baseTaskIds, request.getFilters());
    }

    /**
     * 在 SQL 中追加章节路径过滤条件。
     * 支持多个章节路径提示（OR 关系），使用 LIKE 模糊匹配。
     */
    private void appendSectionFilters(StringBuilder sqlBuilder, DocumentRetrieveFilters filters) {
        boolean hasSectionHints = filters != null && CollUtil.isNotEmpty(filters.getSectionPathHints());
        if (!hasSectionHints) {
            appendStructureFilters(sqlBuilder, filters);
            return;
        }

        sqlBuilder.append("\n  AND (");
        for (int index = 0; index < filters.getSectionPathHints().size(); index++) {
            if (index > 0) {
                sqlBuilder.append(" OR ");
            }
            sqlBuilder.append("LOWER(COALESCE(section_path, '')) LIKE ?");
        }
        sqlBuilder.append(")");
        appendStructureFilters(sqlBuilder, filters);
    }

    /**
     * 追加章节路径过滤的参数。
     */
    private void appendSectionFilterParams(List<Object> params, DocumentRetrieveFilters filters) {
        if (filters != null && CollUtil.isNotEmpty(filters.getSectionPathHints())) {
            for (String sectionHint : filters.getSectionPathHints()) {
                params.add("%" + sectionHint.toLowerCase(Locale.ROOT) + "%");
            }
        }
        appendStructureFilterParams(params, filters);
    }

    /**
     * 在 SQL 中追加结构过滤条件（结构节点ID、规范路径、条目索引）。
     */
    private void appendStructureFilters(StringBuilder sqlBuilder, DocumentRetrieveFilters filters) {
        boolean hasStructureNodeIds = filters != null && CollUtil.isNotEmpty(filters.getStructureNodeIdHints());
        boolean hasCanonicalPathHints = filters != null && CollUtil.isNotEmpty(filters.getCanonicalPathHints());
        boolean hasItemIndexes = filters != null && CollUtil.isNotEmpty(filters.getItemIndexHints());
        if (!hasStructureNodeIds && !hasCanonicalPathHints && !hasItemIndexes) {
            return;
        }
        if (hasStructureNodeIds) {
            sqlBuilder.append("\n  AND structure_node_id IN (")
                .append(buildPlaceholders(filters.getStructureNodeIdHints().size()))
                .append(")");
        }
        if (hasCanonicalPathHints) {
            sqlBuilder.append("\n  AND (");
            for (int index = 0; index < filters.getCanonicalPathHints().size(); index++) {
                if (index > 0) {
                    sqlBuilder.append(" OR ");
                }
                sqlBuilder.append("LOWER(COALESCE(canonical_path, '')) LIKE ?");
            }
            sqlBuilder.append(")");
        }
        if (hasItemIndexes) {
            sqlBuilder.append("\n  AND item_index IN (")
                .append(buildPlaceholders(filters.getItemIndexHints().size()))
                .append(")");
        }
    }

    /**
     * 追加结构过滤条件的参数。
     */
    private void appendStructureFilterParams(List<Object> params, DocumentRetrieveFilters filters) {
        if (filters == null) {
            return;
        }
        if (CollUtil.isNotEmpty(filters.getStructureNodeIdHints())) {
            params.addAll(filters.getStructureNodeIdHints());
        }
        if (CollUtil.isNotEmpty(filters.getCanonicalPathHints())) {
            for (String canonicalPathHint : filters.getCanonicalPathHints()) {
                params.add(canonicalPathHint.toLowerCase(Locale.ROOT) + "%");
            }
        }
        if (CollUtil.isNotEmpty(filters.getItemIndexHints())) {
            params.addAll(filters.getItemIndexHints());
        }
    }

    /**
     * 构建父块证据文档。
     * 取最高分子块的 metadata 作为基础，用父块的完整文本替换子块文本。
     *
     * @param parentBlock   父块数据库记录
     * @param childDocuments 同一父块下命中的子块列表
     * @param maxChars      最大文本长度
     * @return 父块证据 Document
     */
    private Document buildParentEvidenceDocument(KnowHubDocumentParentBlock parentBlock,
                                                 List<Document> childDocuments,
                                                 int maxChars) {
        // 取得分最高的子块作为基础
        Document bestChild = childDocuments.stream()
            .max(Comparator.comparingDouble(document -> {
                Double score = resolveScore(document);
                return score == null ? 0D : score;
            }))
            .orElseThrow(IllegalStateException::new);

        // 聚合父块得分
        double parentScore = aggregateParentScore(childDocuments);
        Map<String, Object> metadata = new LinkedHashMap<>(bestChild.getMetadata());
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID, parentBlock.getId());
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO, parentBlock.getParentNo());
        metadata.put(DocumentKnowledgeMetadataKeys.SECTION_PATH, safeText(parentBlock.getSectionPath()));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID, parentBlock.getStructureNodeId());
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_TYPE, parentBlock.getStructureNodeType());
        metadata.put(DocumentKnowledgeMetadataKeys.CANONICAL_PATH, safeText(parentBlock.getCanonicalPath()));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.ITEM_INDEX, parentBlock.getItemIndex());
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, parentScore);
        metadata.put(DocumentKnowledgeMetadataKeys.ORIGINAL_SNIPPET, safeText(parentBlock.getParentText()));

        // 判断检索渠道：如果子块来自不同渠道则标注为 "hybrid"
        LinkedHashSet<String> channels = childDocuments.stream()
            .map(document -> asText(document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHANNEL)))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL,
            channels.size() > 1 ? "hybrid" : channels.stream().findFirst().orElse("vector"));

        return Document.builder()
            .id("parent-" + parentBlock.getId())
            .text(renderParentEvidenceText(parentBlock, childDocuments, maxChars))
            .metadata(metadata)
            .score(parentScore)
            .build();
    }

    /**
     * 聚合父块得分。
     * 公式：bestChildScore * (1 + supportWeight + multiChannelWeight)
     *   - supportWeight：每多一个子块命中 +0.12，最多 +0.36
     *   - multiChannelWeight：如果命中了多个检索渠道，额外 +0.10
     */
    private double aggregateParentScore(List<Document> childDocuments) {
        double bestChildScore = childDocuments.stream()
            .map(this::resolveScore)
            .filter(Objects::nonNull)
            .max(Double::compareTo)
            .orElse(0D);
        int supportCount = Math.max(0, childDocuments.size() - 1);
        LinkedHashSet<String> channels = childDocuments.stream()
            .map(document -> asText(document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHANNEL)))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        double supportWeight = Math.min(0.36D, supportCount * 0.12D);
        double multiChannelWeight = channels.size() > 1 ? 0.10D : 0D;
        return bestChildScore * (1D + supportWeight + multiChannelWeight);
    }

    /** 向 metadata 中写入非 null 的值 */
    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    /**
     * 比较两个证据文档的排序优先级。
     * 排序规则：得分降序 > 父块编号升序 > chunk编号升序
     */
    private int compareEvidenceDocument(Document left, Document right) {
        int scoreCompare = Double.compare(resolveScoreOrZero(right), resolveScoreOrZero(left));
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        Integer leftParentNo = asInteger(left == null ? null : left.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO));
        Integer rightParentNo = asInteger(right == null ? null : right.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO));
        int parentNoCompare = compareNullableInteger(leftParentNo, rightParentNo);
        if (parentNoCompare != 0) {
            return parentNoCompare;
        }
        Integer leftChunkNo = asInteger(left == null ? null : left.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO));
        Integer rightChunkNo = asInteger(right == null ? null : right.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO));
        return compareNullableInteger(leftChunkNo, rightChunkNo);
    }

    /** 获取文档得分，如果为 null 则返回 0 */
    private double resolveScoreOrZero(Document document) {
        Double score = resolveScore(document);
        return score == null ? 0D : score;
    }

    /** 比较两个可空 Integer，null 视为最大值（排在后面） */
    private int compareNullableInteger(Integer left, Integer right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Integer.compare(left, right);
    }

    /**
     * 渲染父块证据文本。
     * 格式：
     *   [父块内容]
     *   {父块完整文本}
     *
     *   [命中子片段]
     *   - child#1：{子块文本摘要}
     *   - child#2：{子块文本摘要}
     */
    private String renderParentEvidenceText(KnowHubDocumentParentBlock parentBlock,
                                            List<Document> childDocuments,
                                            int maxChars) {
        String parentText = safeText(parentBlock.getParentText());
        if (StrUtil.isBlank(parentText)) {
            return childDocuments.isEmpty() ? "" : StrUtil.blankToDefault(childDocuments.get(0).getText(), "");
        }

        // 构建命中子片段摘要
        StringBuilder hitSummaryBuilder = new StringBuilder();
        for (Document childDocument : childDocuments) {
            if (childDocument == null) {
                continue;
            }
            if (!hitSummaryBuilder.isEmpty()) {
                hitSummaryBuilder.append('\n');
            }
            hitSummaryBuilder.append("- child#")
                .append(asInteger(childDocument.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO)))
                .append("：")
                .append(trimText(safeText(childDocument.getText()), 140));
        }

        String composed = joinSections(
            "[父块内容]\n" + parentText,
            hitSummaryBuilder.isEmpty() ? "" : "[命中子片段]\n" + hitSummaryBuilder
        );
        return trimText(composed, Math.max(1, maxChars));
    }

    /**
     * 从 Document 的 metadata 或 score 字段中提取得分。
     * 优先从 metadata 中取，因为父块提升后会覆盖 metadata 中的 score。
     */
    private Double resolveScore(Document document) {
        if (document == null) {
            return null;
        }
        Object metadataScore = document.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
        if (metadataScore instanceof Number number) {
            return number.doubleValue();
        }
        return document.getScore();
    }

    /** 拼接非空的文本段落，用双换行分隔 */
    private String joinSections(String... sections) {
        List<String> parts = new ArrayList<>();
        for (String section : sections) {
            if (StrUtil.isNotBlank(section)) {
                parts.add(section.trim());
            }
        }
        return String.join("\n\n", parts);
    }

    /** 从 ResultSet 中安全读取 Long 值（处理 SQL NULL） */
    private Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    /** 从 ResultSet 中安全读取 Integer 值（处理 SQL NULL） */
    private Integer getNullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    /** 截断文本到指定长度，超出部分用省略号替代 */
    private String trimText(String text, int maxChars) {
        if (StrUtil.isBlank(text) || text.length() <= maxChars) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    /**
     * 从问题文本中提取关键词。
     * 提取策略：
     *   1. 英文/数字 token：直接用正则提取连续的字母数字串（至少2字符）
     *   2. 中文 token：先提取连续汉字（至少2字），再去停用词、按连接词分割，
     *      最后用 n-gram 策略生成 2-4 字的关键词片段
     *   3. 最多返回 MAX_KEYWORD_TERMS（8）个关键词
     *
     * @param question 用户问题文本
     * @return 提取的关键词列表
     */
    private List<String> extractKeywordTerms(String question) {
        String normalized = normalizeQuestion(question);
        if (StrUtil.isBlank(normalized)) {
            return List.of();
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();

        // 提取英文/数字 token
        Matcher alnumMatcher = ALNUM_TOKEN_PATTERN.matcher(normalized);
        while (alnumMatcher.find()) {
            terms.add(alnumMatcher.group());
        }

        // 提取中文 token 并用 n-gram 策略展开
        Matcher chineseMatcher = CHINESE_TOKEN_PATTERN.matcher(normalized);
        while (chineseMatcher.find()) {
            for (String segment : splitChineseSegments(chineseMatcher.group())) {
                addChineseSegmentTerms(segment, terms);
                if (terms.size() >= MAX_KEYWORD_TERMS * 2) {
                    break;
                }
            }
            if (terms.size() >= MAX_KEYWORD_TERMS * 2) {
                break;
            }
        }

        return terms.stream()
            .filter(term -> term.length() >= 2)

            .limit(MAX_KEYWORD_TERMS)
            .toList();
    }

    /**
     * 将中文片段按连接词（的、和、及、与、或）分割。
     * 例如："用户管理的权限配置" -> ["用户管理的权限配置", "用户管理", "权限配置"]
     */
    private List<String> splitChineseSegments(String chineseToken) {
        String cleanedToken = removeChineseNoisePhrases(chineseToken);
        if (cleanedToken.length() < 2) {
            return List.of();
        }
        LinkedHashSet<String> segments = new LinkedHashSet<>();
        segments.add(cleanedToken);
        for (String segment : CHINESE_SEGMENT_SPLIT_PATTERN.split(cleanedToken)) {
            String normalizedSegment = segment == null ? "" : segment.trim();
            if (normalizedSegment.length() >= 2) {
                segments.add(normalizedSegment);
            }
        }
        return new ArrayList<>(segments);
    }

    /**
     * 从辅助提示（queryContextHints）中提取额外关键词。
     * 用于在查询上下文提示中找到补充的关键词信息。
     */
    private List<String> extractAuxiliaryKeywordTerms(List<String> hints) {
        if (CollUtil.isEmpty(hints)) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String hint : hints) {
            if (StrUtil.isBlank(hint)) {
                continue;
            }
            terms.addAll(extractKeywordTerms(hint));
            if (terms.size() >= MAX_KEYWORD_TERMS) {
                break;
            }
        }
        return new ArrayList<>(terms);
    }

    /**
     * 为中文片段生成 n-gram 关键词。
     * 包括：完整片段、尾部 n-gram、头部 n-gram、滑动窗口 n-gram（2-4字）。
     */
    private void addChineseSegmentTerms(String segment, LinkedHashSet<String> terms) {
        if (StrUtil.isBlank(segment) || segment.length() < 2) {
            return;
        }

        if (segment.length() <= 12) {
            terms.add(segment);
        }
        addTailNgrams(segment, terms);
        addHeadNgrams(segment, terms);
        addSlidingNgrams(segment, terms);
    }

    /**
     * 构建关键词评分 SQL 表达式。
     * 每个关键词的评分 = chunk_text 匹配权重 + section_path 匹配权重。
     * chunk_text 权重随关键词顺序递减（第1个关键词权重6，第2个5，以此类推）。
     * section_path 权重比 chunk_text 高2（路径匹配更精准）。
     */
    private String buildKeywordScoreExpression(int termCount) {
        return IntStream.range(0, termCount)

            .mapToObj(index -> "("
                + "CASE WHEN LOWER(chunk_text) LIKE ? THEN ? ELSE 0 END + "
                + "CASE WHEN LOWER(COALESCE(section_path, '')) LIKE ? THEN ? ELSE 0 END"
                + ")")
            .collect(Collectors.joining(" + "));
    }

    /**
     * 构建关键词过滤 WHERE 表达式。
     * 只要 chunk_text 或 section_path 中有一个包含关键词就匹配。
     */
    private String buildKeywordWhereExpression(int termCount) {
        return IntStream.range(0, termCount)
            .mapToObj(index -> "(LOWER(chunk_text) LIKE ? OR LOWER(COALESCE(section_path, '')) LIKE ?)")
            .collect(Collectors.joining(" OR "));
    }

    /**
     * 计算关键词在 chunk_text 中的匹配权重。
     * 越靠前的关键词权重越高（第1个6，第2个5，...，最少1）。
     */
    private int keywordWeight(int index) {

        return Math.max(1, 6 - index);
    }

    /**
     * 计算关键词在 section_path 中的匹配权重。
     * 路径匹配比内容匹配更精准，所以权重额外 +2。
     */
    private int sectionKeywordWeight(int index) {
        return keywordWeight(index) + 2;
    }

    /** 构建 SQL LIKE 模式（转小写后加 % 前后缀） */
    private String likePattern(String term) {
        return "%" + term.toLowerCase(Locale.ROOT) + "%";
    }

    /** 归一化问题文本：转小写、统一空白字符 */
    private String normalizeQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return "";
        }

        return question.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\r\\n\\t]+", " ")
            .replaceAll("\\s+", " ");
    }

    /** 去除中文停用词（"请问"、"帮我"、"如何"等） */
    private String removeChineseNoisePhrases(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }

        String normalized = text.trim();
        for (String phrase : CHINESE_NOISE_PHRASES) {
            normalized = normalized.replace(phrase, "");
        }
        return normalized.trim();
    }

    /** 生成尾部 n-gram（从末尾取2-4个字符） */
    private void addTailNgrams(String segment, LinkedHashSet<String> terms) {
        int maxGram = Math.min(4, segment.length());
        for (int size = maxGram; size >= 2 && terms.size() < MAX_KEYWORD_TERMS * 2; size--) {
            terms.add(segment.substring(segment.length() - size));
        }
    }

    /** 生成头部 n-gram（从开头取2-4个字符） */
    private void addHeadNgrams(String segment, LinkedHashSet<String> terms) {
        int maxGram = Math.min(4, segment.length());
        for (int size = maxGram; size >= 2 && terms.size() < MAX_KEYWORD_TERMS * 2; size--) {
            terms.add(segment.substring(0, size));
        }
    }

    /** 生成滑动窗口 n-gram（2-4字的连续子串） */
    private void addSlidingNgrams(String segment, LinkedHashSet<String> terms) {
        int maxGram = Math.min(4, segment.length());
        for (int size = maxGram; size >= 2 && terms.size() < MAX_KEYWORD_TERMS * 2; size--) {
            for (int index = 0; index <= segment.length() - size && terms.size() < MAX_KEYWORD_TERMS * 2; index++) {
                terms.add(segment.substring(index, index + size));
            }
        }
    }

    /** 安全文本处理，null 转为空字符串 */
    private String safeText(String text) {
        return text == null ? "" : text;
    }

    /** 将 Object 安全转为 Long */
    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    /** 将 Object 安全转为 Integer */
    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** 将 Object 安全转为 String */
    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 解析 TopK 参数。
     * 默认10，最大50，最小1。
     */
    private int resolveTopK(int topK) {

        return topK <= 0 ? 10 : Math.min(topK, 50);
    }

    /**
     * 获取 EmbeddingModel 实例。
     * 如果未配置则抛出异常，因为向量检索必须有 embedding 模型。
     */
    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {

            throw new IllegalStateException("当前未找到可用的 EmbeddingModel，无法执行向量检索。");
        }
        return embeddingModel;
    }

    /**
     * 将 float 数组转为 pgvector 的向量字面量格式。
     * 例如：[0.1, 0.2, 0.3]
     */
    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("问题向量生成失败，无法执行检索。");
        }
        StringBuilder vectorBuilder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {

            if (index > 0) {
                vectorBuilder.append(',');
            }
            vectorBuilder.append(embedding[index]);
        }
        vectorBuilder.append(']');
        return vectorBuilder.toString();
    }

    /** 构建 SQL IN 子句的占位符（"?,?,?"） */
    private String buildPlaceholders(int size) {
        return IntStream.range(0, size)
            .mapToObj(index -> "?")
            .collect(Collectors.joining(","));
    }

    /** Integer 安全转 int，null 时返回 0 */
    private int defaultInteger(Integer value) {
        return Objects.requireNonNullElse(value, 0);
    }

    /**
     * 解析后的元数据作用域记录。
     * 封装了文档ID列表、任务ID列表和过滤条件。
     */
    private record ResolvedMetadataScope(
        List<Long> documentIds,
        List<Long> taskIds,
        DocumentRetrieveFilters filters
    ) {
    }
}
