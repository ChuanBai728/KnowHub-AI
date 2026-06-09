package ai.knowhub.document.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentProfile;
import ai.knowhub.document.data.KnowHubKnowledgeScopeNode;
import ai.knowhub.document.data.KnowHubKnowledgeTopicNode;
import ai.knowhub.document.data.KnowHubTopicDocumentRelation;
import ai.knowhub.document.mapper.KnowHubDocumentMapper;
import ai.knowhub.document.mapper.KnowHubDocumentProfileMapper;
import ai.knowhub.document.mapper.KnowHubKnowledgeScopeNodeMapper;
import ai.knowhub.document.mapper.KnowHubKnowledgeTopicNodeMapper;
import ai.knowhub.document.mapper.KnowHubTopicDocumentRelationMapper;
import ai.knowhub.document.model.es.KnowledgeRouteIndexRecord;
import ai.knowhub.document.service.KnowledgeRouteIndexService;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.enums.DocumentIndexStatusEnum;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 【知识路由索引服务 - Elasticsearch 实现】
 *
 * 这个类是 KnowledgeRouteIndexService 接口的 Elasticsearch 实现，
 * 负责将知识范围（Scope）、主题（Topic）、文档（Document）的路由信息同步到 ES 索引中，
 * 以便在用户提问时快速进行词法匹配（lexical search），辅助知识路由决策。
 *
 * 核心能力：
 *   1. 全量刷新（refreshAll）：清空 ES 索引，从数据库加载所有活跃的 Scope/Topic/Document，
 *      构建索引记录后批量写入 ES
 *   2. 增量刷新控制（refreshIfNeeded）：通过 AtomicLong 时间戳节流，
 *      两次刷新间隔至少 5 秒，避免频繁刷新影响性能
 *   3. 词法搜索（search）：在 ES 中按 entityType 过滤，使用多字段加权匹配
 *      （displayName boost 10、aliasesText boost 8、examplesText boost 6 等）
 *   4. 文档路由删除（deleteDocumentRoute）：删除文档时同步清理 ES 中的路由快照
 *
 * 索引记录结构（KnowledgeRouteIndexRecord）：
 *   - routeId：路由ID（格式："scope:code"、"topic:code"、"document:id"）
 *   - entityType：实体类型（"scope"、"topic"、"document"）
 *   - entityCode：实体编码
 *   - displayName：显示名称
 *   - routeText：拼接的路由文本（用于全文搜索）
 *   - entityTerms：实体关键词列表（用于精确匹配）
 *   - tags：标签列表
 *
 * 条件启用：
 *   通过 @ConditionalOnProperty(prefix = "app.manage.elasticsearch", name = "enabled") 控制，
 *   只有当 Elasticsearch 启用时才注册本服务。
 *
 * 设计模式：Refresh-on-read 模式（在搜索时检查是否需要刷新索引），
 *           节流模式（通过 AtomicLong 限制刷新频率）
 */
@Slf4j
@AllArgsConstructor
@Service
@ConditionalOnProperty(prefix = "app.manage.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchKnowledgeRouteIndexService implements KnowledgeRouteIndexService {

    /** 两次刷新之间的最小间隔（5秒） */
    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(5);

    /** 上次刷新时间戳（用于节流控制），使用 AtomicLong 保证线程安全 */
    private static final AtomicLong LAST_REFRESH_TIME = new AtomicLong(0L);

    /** Elasticsearch 客户端（通过 @Qualifier 注入指定 Bean） */
    @Qualifier("documentManageElasticsearchClient")
    private final ElasticsearchClient elasticsearchClient;

    /** 文档管理配置属性（包含 ES 索引名称等配置） */
    private final DocumentManageProperties properties;

    /** 知识范围 Mapper */
    private final KnowHubKnowledgeScopeNodeMapper scopeNodeMapper;

    /** 知识主题 Mapper */
    private final KnowHubKnowledgeTopicNodeMapper topicNodeMapper;

    /** 文档 Mapper */
    private final KnowHubDocumentMapper documentMapper;

    /** 文档画像 Mapper */
    private final KnowHubDocumentProfileMapper documentProfileMapper;

    /** 主题-文档关联 Mapper */
    private final KnowHubTopicDocumentRelationMapper topicDocumentRelationMapper;

    /**
     * 按需刷新索引。
     * 通过 AtomicLong 实现时间戳节流：如果距离上次刷新不足 REFRESH_INTERVAL，
     * 直接跳过。使用 CAS（compareAndSet）确保并发安全。
     */
    @Override
    public void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        long last = LAST_REFRESH_TIME.get();
        if (now - last < REFRESH_INTERVAL.toMillis()) {
            return;
        }
        // CAS 操作：只有一个线程能成功更新时间戳并执行刷新
        if (!LAST_REFRESH_TIME.compareAndSet(last, now)) {
            return;
        }
        try {
            refreshAll();
        }
        catch (Exception exception) {
            // 刷新失败时重置时间戳，下次查询时会重试
            LAST_REFRESH_TIME.set(0L);
            log.warn("刷新知识路由索引失败，将在下次查询时重试。", exception);
        }
    }

    /**
     * 在 ES 中搜索路由候选。
     *
     * 搜索策略：
     *   1. 必须匹配 entityType（scope/topic/document）
     *   2. 使用 multi_match 在多个字段中搜索（加权）
     *   3. 额外对 displayName 做 matchPhrase 搜索（短语匹配权重更高）
     *   4. 对 entityTerms 做 term 精确匹配（实体关键词辅助）
     *   5. minimumShouldMatch("1")：至少命中一个 should 条件
     *
     * @param routingText 路由文本（用户问题 + 改写后的问题）
     * @param entityType  实体类型（"scope"、"topic"、"document"）
     * @param size        返回结果数量上限
     * @return 命中的路由候选列表
     */
    @Override
    public List<RouteLexicalHit> search(String routingText, String entityType, int size) {
        if (StrUtil.isBlank(routingText) || StrUtil.isBlank(entityType)) {
            return List.of();
        }
        // 搜索前检查是否需要刷新索引
        refreshIfNeeded();
        // 从路由文本中提取实体关键词
        List<String> entityTerms = extractEntityTerms(routingText);
        try {
            SearchResponse<KnowledgeRouteIndexRecord> response = elasticsearchClient.search(search -> search
                    .index(properties.getElasticsearch().getRouteIndexName())
                    .size(Math.max(1, Math.min(size, 50)))
                    .query(query -> query.bool(bool -> {
                        // 必须匹配实体类型
                        bool.filter(filter -> filter.term(term -> term.field("entityType").value(entityType)));
                        // 短语匹配（权重最高）
                        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
                            .field("displayName")
                            .query(routingText)
                            .boost(12.0f)
                        ));
                        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
                            .field("displayName.raw")
                            .query(routingText)
                            .boost(16.0f)
                        ));
                        // 多字段加权匹配
                        bool.should(should -> should.multiMatch(multiMatch -> multiMatch
                            .query(routingText)
                            .fields(
                                "displayName^10",
                                "displayName.raw^16",
                                "displayName.standard^12",
                                "displayName.english^12",
                                "documentName^8",
                                "documentName.raw^14",
                                "documentName.standard^10",
                                "documentName.english^10",
                                "scopeName^6",
                                "scopeName.raw^9",
                                "scopeName.standard^7",
                                "scopeName.english^7",
                                "topicName^6",
                                "topicName.raw^9",
                                "topicName.standard^7",
                                "topicName.english^7",
                                "aliasesText^8",
                                "aliasesText.standard^9",
                                "aliasesText.english^9",
                                "examplesText^6",
                                "examplesText.standard^7",
                                "examplesText.english^7",
                                "summaryText^5",
                                "summaryText.standard^6",
                                "summaryText.english^6",
                                "routeText^4",
                                "routeText.standard^5",
                                "routeText.english^5",
                                "descriptionText^3",
                                "descriptionText.standard^4",
                                "descriptionText.english^4")
                            .type(TextQueryType.BestFields)
                        ));
                        // 实体关键词精确匹配
                        for (String entityTerm : entityTerms) {
                            bool.should(should -> should.term(term -> term
                                .field("entityTerms")
                                .value(entityTerm)
                                .boost(9.0f)
                            ));
                        }
                        bool.minimumShouldMatch("1");
                        return bool;
                    })),
                KnowledgeRouteIndexRecord.class);
            List<RouteLexicalHit> hits = new ArrayList<>();
            for (Hit<KnowledgeRouteIndexRecord> hit : response.hits().hits()) {
                KnowledgeRouteIndexRecord source = hit.source();
                if (source == null) {
                    continue;
                }
                hits.add(new RouteLexicalHit(
                    source.getRouteId(),
                    source.getEntityCode(),
                    source.getEntityType(),
                    source.getDocumentId(),
                    source.getScopeCode(),
                    source.getTopicCode(),
                    source.getDocumentName(),
                    hit.score() == null ? 0D : hit.score()
                ));
            }
            return hits;
        }
        catch (IOException exception) {
            log.warn("知识路由 ES lexical 检索失败，退回语义匹配: entityType={}, query='{}'", entityType, StrUtil.maxLength(routingText, 120), exception);
            return List.of();
        }
    }

    /**
     * 删除指定文档在 ES 中的路由快照。
     * 使用 deleteByQuery 按 entityType="document" 和 documentId 过滤删除。
     *
     * @param documentId 文档ID
     */
    @Override
    public void deleteDocumentRoute(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            elasticsearchClient.deleteByQuery(delete -> delete
                .index(properties.getElasticsearch().getRouteIndexName())
                .refresh(true)
                .query(query -> query.bool(bool -> bool
                    .filter(filter -> filter.term(term -> term
                        .field("entityType")
                        .value("document")
                    ))
                    .filter(filter -> filter.term(term -> term
                        .field("documentId")
                        .value(documentId)
                    ))
                ))
            );
            log.info("知识路由索引中的文档路由快照已删除: documentId={}, index={}",
                documentId, properties.getElasticsearch().getRouteIndexName());
        }
        catch (IOException exception) {
            throw new IllegalStateException("删除知识路由索引中的文档路由快照失败", exception);
        }
    }

    /**
     * 全量刷新索引。
     * 流程：
     *   1. 从数据库加载所有活跃的 Scope、Topic、Document 和画像数据
     *   2. 构建索引记录列表
     *   3. 清空 ES 索引（deleteByQuery matchAll）
     *   4. 批量写入新记录（BulkRequest）
     */
    private void refreshAll() throws IOException {
        List<KnowledgeRouteIndexRecord> records = buildIndexRecords();
        String indexName = properties.getElasticsearch().getRouteIndexName();
        // 清空现有索引数据
        elasticsearchClient.deleteByQuery(delete -> delete
            .index(indexName)
            .refresh(true)
            .query(query -> query.matchAll(matchAll -> matchAll))
        );
        if (records.isEmpty()) {
            log.info("知识路由索引刷新完成，但当前没有可写入的路由快照。");
            return;
        }
        // 批量写入新记录
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder()
            .index(indexName)
            .refresh(Refresh.WaitFor);
        for (KnowledgeRouteIndexRecord record : records) {
            bulkBuilder.operations(operation -> operation.index(index -> index
                .id(record.getRouteId())
                .document(record)
            ));
        }
        BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
        if (response.errors()) {
            String errorMessage = response.items().stream()
                .filter(item -> item.error() != null)
                .map(item -> item.id() + ":" + item.error().reason())
                .collect(Collectors.joining("; "));
            throw new IllegalStateException("批量写入知识路由索引失败: " + errorMessage);
        }
        log.info("知识路由索引刷新完成: recordCount={}, index={}", records.size(), indexName);
    }

    /**
     * 从数据库构建所有索引记录。
     *
     * 构建三种类型的记录：
     *   1. Scope 记录：知识范围的名称、描述、别名、示例
     *   2. Topic 记录：主题的名称、描述、别名、示例、回答形态、执行偏好
     *   3. Document 记录：文档的名称、摘要、核心主题、示例问题、标签
     *
     * 每条记录都包含 routeText（拼接的全文搜索文本）和 entityTerms（实体关键词）。
     */
    private List<KnowledgeRouteIndexRecord> buildIndexRecords() {
        List<KnowledgeRouteIndexRecord> records = new ArrayList<>();
        // 加载所有活跃的知识范围
        List<KnowHubKnowledgeScopeNode> scopes = scopeNodeMapper.selectList(new LambdaQueryWrapper<KnowHubKnowledgeScopeNode>()
            .eq(KnowHubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode()));
        // 加载所有活跃的知识主题
        List<KnowHubKnowledgeTopicNode> topics = topicNodeMapper.selectList(new LambdaQueryWrapper<KnowHubKnowledgeTopicNode>()
            .eq(KnowHubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode()));
        // 加载所有索引构建成功的文档
        List<KnowHubDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KnowHubDocument>()
            .eq(KnowHubDocument::getStatus, BusinessStatus.YES.getCode())
            .eq(KnowHubDocument::getIndexStatus, DocumentIndexStatusEnum.BUILD_SUCCESS.getCode())
            .isNotNull(KnowHubDocument::getLastIndexTaskId));
        // 加载所有已完成的文档画像
        Map<Long, KnowHubDocumentProfile> profileMap = documentProfileMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentProfile>()
                .eq(KnowHubDocumentProfile::getStatus, BusinessStatus.YES.getCode())
                .eq(KnowHubDocumentProfile::getProfileStatus, 2))
            .stream()
            .collect(Collectors.toMap(KnowHubDocumentProfile::getDocumentId, item -> item, (left, right) -> right));
        // 按 scopeCode 分组主题
        Map<String, List<KnowHubKnowledgeTopicNode>> topicByScope = topics.stream()
            .collect(Collectors.groupingBy(KnowHubKnowledgeTopicNode::getScopeCode));
        // 按 topicCode 分组主题-文档关联
        Map<String, List<KnowHubTopicDocumentRelation>> relationByTopic = topicDocumentRelationMapper.selectList(
                new LambdaQueryWrapper<KnowHubTopicDocumentRelation>()
                    .eq(KnowHubTopicDocumentRelation::getStatus, BusinessStatus.YES.getCode()))
            .stream()
            .collect(Collectors.groupingBy(KnowHubTopicDocumentRelation::getTopicCode));

        // 构建 Scope 索引记录
        for (KnowHubKnowledgeScopeNode scope : scopes) {
            List<String> scopeTags = new ArrayList<>();
            topicByScope.getOrDefault(scope.getScopeCode(), List.of()).forEach(topic -> {
                addUnique(scopeTags, topic.getTopicName());
                parseCommaText(topic.getAliases()).forEach(item -> addUnique(scopeTags, item));
            });
            records.add(KnowledgeRouteIndexRecord.builder()
                .routeId("scope:" + scope.getScopeCode())
                .entityType("scope")
                .entityCode(scope.getScopeCode())
                .scopeCode(scope.getScopeCode())
                .scopeName(scope.getScopeName())
                .displayName(safeText(scope.getScopeName()))
                .descriptionText(safeText(scope.getDescription()))
                .aliasesText(safeText(scope.getAliases()))
                .examplesText(safeText(scope.getExamples()))
                .summaryText(safeText(scope.getDescription()))
                .routeText(join(scope.getScopeName(), scope.getDescription(), scope.getAliases(), scope.getExamples()))
                .entityTerms(extractEntityTerms(join(scope.getScopeCode(), scope.getScopeName(), scope.getAliases())))
                .tags(scopeTags)
                .build());
        }

        // 构建 Topic 索引记录
        for (KnowHubKnowledgeTopicNode topic : topics) {
            List<String> tags = new ArrayList<>();
            parseJsonArray(topic.getExamples()).forEach(item -> addUnique(tags, item));
            parseCommaText(topic.getAliases()).forEach(item -> addUnique(tags, item));
            records.add(KnowledgeRouteIndexRecord.builder()
                .routeId("topic:" + topic.getTopicCode())
                .entityType("topic")
                .entityCode(topic.getTopicCode())
                .scopeCode(topic.getScopeCode())
                .topicCode(topic.getTopicCode())
                .topicName(topic.getTopicName())
                .displayName(safeText(topic.getTopicName()))
                .descriptionText(safeText(topic.getDescription()))
                .aliasesText(safeText(topic.getAliases()))
                .examplesText(safeText(topic.getExamples()))
                .summaryText(join(topic.getAnswerShape(), topic.getExecutionPreference()))
                .routeText(join(
                    topic.getTopicCode(),
                    topic.getTopicName(),
                    topic.getTopicName(),
                    topic.getDescription(),
                    topic.getAliases(),
                    topic.getExamples(),
                    topic.getAnswerShape(),
                    topic.getExecutionPreference()))
                .entityTerms(extractEntityTerms(join(topic.getTopicCode(), topic.getTopicName(), topic.getAliases())))
                .tags(tags)
                .build());
        }

        // 构建文档到主题的映射（用于给文档记录附加主题标签）
        Map<Long, KnowHubKnowledgeTopicNode> topicDocumentMap = new LinkedHashMap<>();
        for (KnowHubKnowledgeTopicNode topic : topics) {
            for (KnowHubTopicDocumentRelation relation : relationByTopic.getOrDefault(topic.getTopicCode(), List.of())) {
                topicDocumentMap.put(relation.getDocumentId(), topic);
            }
        }

        // 构建 Document 索引记录
        for (KnowHubDocument document : documents) {
            KnowHubDocumentProfile profile = profileMap.get(document.getId());
            List<String> tags = new ArrayList<>();
            parseCommaText(document.getDocumentTags()).forEach(item -> addUnique(tags, item));
            if (profile != null) {
                parseJsonArray(profile.getCoreTopics()).forEach(item -> addUnique(tags, item));
                parseJsonArray(profile.getExampleQuestions()).forEach(item -> addUnique(tags, item));
            }
            // 将关联的主题名称和别名也加入标签
            relationByTopic.forEach((topicCode, relations) -> relations.stream()
                .filter(relation -> document.getId().equals(relation.getDocumentId()))
                .findFirst()
                .ifPresent(relation -> {
                    KnowHubKnowledgeTopicNode topic = topics.stream()
                        .filter(item -> topicCode.equals(item.getTopicCode()))
                        .findFirst()
                        .orElse(null);
                    if (topic != null) {
                        addUnique(tags, topic.getTopicName());
                        parseCommaText(topic.getAliases()).forEach(item -> addUnique(tags, item));
                    }
                }));
            records.add(KnowledgeRouteIndexRecord.builder()
                .routeId("document:" + document.getId())
                .entityType("document")
                .entityCode(String.valueOf(document.getId()))
                .documentId(document.getId())
                .scopeCode(safeText(document.getKnowledgeScopeCode()))
                .scopeName(safeText(document.getKnowledgeScopeName()))
                .documentName(safeText(document.getDocumentName()))
                .businessCategory(safeText(document.getBusinessCategory()))
                .displayName(safeText(document.getDocumentName()))
                .descriptionText(profile == null ? "" : safeText(profile.getDocumentType()))
                .aliasesText("")
                .examplesText(profile == null ? "" : joinJsonLike(parseJsonArray(profile.getExampleQuestions())))
                .summaryText(profile == null ? "" : safeText(profile.getDocumentSummary()))
                .routeText(join(
                    document.getDocumentName(),
                    document.getKnowledgeScopeCode(),
                    document.getKnowledgeScopeName(),
                    document.getBusinessCategory(),
                    document.getDocumentTags(),
                    profile == null ? "" : profile.getDocumentSummary(),
                    profile == null ? "" : profile.getCoreTopics(),
                    profile == null ? "" : profile.getExampleQuestions(),
                    profile == null ? "" : profile.getDocumentType()
                ))
                .entityTerms(extractEntityTerms(join(document.getDocumentName(), document.getDocumentTags(), document.getKnowledgeScopeName())))
                .tags(tags)
                .build());
        }
        return records;
    }

    /**
     * 从文本中提取实体关键词。
     * 按标点和空白分割，保留包含字母数字或数字的 token（至少2字符）。
     * 同时生成原始形式、大写形式和小写形式（最多20个）。
     */
    private List<String> extractEntityTerms(String text) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = text.trim();
        for (String part : normalized.split("[\\s、，,；;：:（）()]+")) {
            String trimmed = part.trim();
            if (trimmed.length() < 2) {
                continue;
            }
            if (trimmed.matches(".*[A-Za-z].*") || trimmed.matches(".*\\d.*")) {
                terms.add(trimmed);
                terms.add(trimmed.toUpperCase(Locale.ROOT));
                terms.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(terms).stream().limit(20).toList();
    }

    /**
     * 解析 JSON 数组字符串。
     * 简单实现：去除方括号后按逗号分割，去除引号和空白。
     */
    private List<String> parseJsonArray(String raw) {
        String normalized = StrUtil.blankToDefault(raw, "").trim();
        if (normalized.isBlank() || "[]".equals(normalized)) {
            return List.of();
        }
        String body = normalized.replace("[", "").replace("]", "");
        if (body.isBlank()) {
            return List.of();
        }
        return List.of(body.split(",")).stream()
            .map(item -> item.replace("\"", "").trim())
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    /** 按逗号分割文本并去除空白 */
    private List<String> parseCommaText(String raw) {
        String normalized = StrUtil.blankToDefault(raw, "").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split(",")).stream()
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    /** 将字符串列表用空格拼接 */
    private String joinJsonLike(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(" ", values);
    }

    /** 将多个字符串用空格拼接（过滤空值） */
    private String join(String... values) {
        return Arrays.stream(values)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.joining(" "));
    }

    /** 向列表中添加去重的非空值 */
    private void addUnique(List<String> values, String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        if (!values.contains(value.trim())) {
            values.add(value.trim());
        }
    }

    /** 安全文本处理，null 转为空字符串 */
    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
