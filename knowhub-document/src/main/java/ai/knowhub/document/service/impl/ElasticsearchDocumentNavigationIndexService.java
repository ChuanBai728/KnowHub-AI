package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.model.es.DocumentNavigationIndexRecord;
import ai.knowhub.document.service.DocumentNavigationIndexService;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 【Elasticsearch 文档导航索引服务实现】
 *
 * 设计模式：索引同步模式（Index Sync Pattern）
 *
 * 这个类负责将文档的结构节点（标题、章节等）同步到 Elasticsearch，
 * 以支持基于章节路径和标题的快速导航搜索。
 *
 * 使用场景：
 *   当 RAG 检索命中某个 chunk 后，需要进一步在文档内部定位到具体章节。
 *   通过 ES 的全文搜索能力，可以根据用户的 topic、question 等信息
 *   在文档的结构节点中找到最相关的章节。
 *
 * 索引策略：
 *   - 采用"先删后写"的全量替换策略（reindexDocumentNodes 方法）
 *   - 每次文档解析完成后，都会重建该文档的导航索引
 *   - 使用 ES 的 Bulk API 批量写入，提高效率
 *
 * 搜索策略：
 *   - 使用 Bool Query 组合多个条件
 *   - 必须匹配 documentId（filter）
 *   - 必须是 SECTION 类型节点（filter）
 *   - 对 title、sectionPath、anchorText、contentText 进行全文搜索（should）
 *   - title 精确短语匹配权重最高（boost 20）
 *
 * 条件启用：
 *   通过 @ConditionalOnProperty 控制，只有配置 app.manage.elasticsearch.enabled=true 时才启用。
 *   默认启用（matchIfMissing = true）。
 */
@Slf4j
@AllArgsConstructor
@Service
@ConditionalOnProperty(prefix = "app.manage.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchDocumentNavigationIndexService implements DocumentNavigationIndexService {

    /** 默认搜索返回数量 */
    private static final int DEFAULT_SEARCH_SIZE = 8;

    /**
     * Elasticsearch 客户端。
     * 使用 @Qualifier 指定注入名称，与其他 ES 客户端区分。
     */
    @Qualifier("documentManageElasticsearchClient")
    private final ElasticsearchClient elasticsearchClient;

    /** 文档管理配置属性（包含 ES 索引名称等） */
    private final DocumentManageProperties properties;

    /**
     * 重建文档的导航索引（全量替换）
     *
     * @param documentId   文档ID
     * @param parseTaskId  解析任务ID
     * @param nodes        文档的结构节点列表
     */
    @Override
    public void reindexDocumentNodes(Long documentId, Long parseTaskId, List<KnowHubDocumentStructureNode> nodes) {
        if (documentId == null) {
            return;
        }
        log.info("开始重建导航索引: documentId={}, parseTaskId={}, nodeCount={}, index={}",
            documentId,
            parseTaskId,
            nodes == null ? 0 : nodes.size(),
            properties.getElasticsearch().getNavigationIndexName());
        // 先删除该文档的旧索引数据
        deleteByDocumentId(documentId);
        if (CollUtil.isEmpty(nodes)) {
            log.info("导航索引重建跳过写入，因为结构节点为空: documentId={}, parseTaskId={}", documentId, parseTaskId);
            return;
        }
        // 使用 ES Bulk API 批量写入
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder()
            .index(properties.getElasticsearch().getNavigationIndexName())
            .refresh(Refresh.WaitFor);
        for (KnowHubDocumentStructureNode node : nodes) {
            if (node == null || node.getId() == null) {
                continue;
            }
            // 将数据库实体转为 ES 索引记录
            DocumentNavigationIndexRecord record = toIndexRecord(node, parseTaskId);
            bulkBuilder.operations(operation -> operation
                .index(index -> index
                    .id(String.valueOf(record.getNodeId()))
                    .document(record)
                )
            );
        }
        try {
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
            if (response.errors()) {
                String errorMessage = response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> item.id() + ":" + item.error().reason())
                    .collect(Collectors.joining("; "));
                throw new IllegalStateException("批量写入导航索引失败: " + errorMessage);
            }
            log.info("文档结构节点已同步写入导航索引: documentId={}, parseTaskId={}, nodeCount={}, index={}",
                documentId, parseTaskId, nodes.size(), properties.getElasticsearch().getNavigationIndexName());
        }
        catch (IOException exception) {
            throw new IllegalStateException("写入导航索引失败", exception);
        }
    }

    /**
     * 删除文档的导航索引数据
     * @param documentId 文档ID
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            log.info("删除导航索引文档数据: documentId={}, index={}", documentId, properties.getElasticsearch().getNavigationIndexName());
            elasticsearchClient.deleteByQuery(delete -> delete
                .index(properties.getElasticsearch().getNavigationIndexName())
                .refresh(true)
                .query(query -> query.term(term -> term
                    .field("documentId")
                    .value(documentId)
                ))
            );
        }
        catch (IOException exception) {
            throw new IllegalStateException("删除导航索引失败", exception);
        }
    }

    /**
     * 在文档的章节索引中搜索最相关的章节
     *
     * 搜索逻辑：
     *   - 构建多个查询文本（topic、facet、informationNeed、question）
     *   - 对每个查询文本，添加 title 短语匹配（权重 20）、sectionPath 短语匹配（权重 15）、
     *     multi_match 全文搜索（title 10、sectionPath 8、anchorText 5、contentText 1）
     *   - 至少一个 should 条件必须命中（minimumShouldMatch = 1）
     *
     * @param documentId      文档ID
     * @param topic           主题关键词
     * @param facet           侧面关键词
     * @param informationNeed 信息需求描述
     * @param question        用户问题
     * @param size            返回数量
     * @return 匹配的章节命中列表
     */
    @Override
    public List<NavigationSectionHit> searchSections(Long documentId,
                                                     String topic,
                                                     String facet,
                                                     String informationNeed,
                                                     String question,
                                                     int size) {
        if (documentId == null) {
            return List.of();
        }
        // 构建查询文本列表（去重）
        List<String> queries = buildQueries(topic, facet, informationNeed, question);
        if (queries.isEmpty()) {
            return List.of();
        }
        int searchSize = size <= 0 ? DEFAULT_SEARCH_SIZE : Math.min(size, 20);
        log.info("导航索引搜索请求: documentId={}, topic='{}', facet='{}', informationNeed='{}', question='{}', size={}, queries={}",
            documentId,
            safeText(topic),
            safeText(facet),
            safeText(informationNeed),
            safeText(question),
            searchSize,
            queries);
        try {
            // 执行 ES 搜索
            SearchResponse<DocumentNavigationIndexRecord> response = elasticsearchClient.search(search -> search
                    .index(properties.getElasticsearch().getNavigationIndexName())
                    .size(searchSize)
                    .query(query -> query.bool(bool -> {
                        // 必须匹配文档ID
                        bool.filter(filter -> filter.term(term -> term
                            .field("documentId")
                            .value(documentId)
                        ));
                        // 必须是 SECTION 类型节点
                        bool.filter(filter -> filter.term(term -> term
                            .field("nodeType")
                            .value(DocumentStructureNodeTypeEnum.SECTION.name())
                        ));
                        // 对每个查询文本添加 should 条件
                        for (String queryText : queries) {
                            addSectionShouldQueries(bool, queryText);
                        }
                        bool.minimumShouldMatch("1");
                        return bool;
                    })),
                DocumentNavigationIndexRecord.class);
            // 将 ES 搜索结果转为 NavigationSectionHit 列表
            List<NavigationSectionHit> hits = new ArrayList<>();
            for (Hit<DocumentNavigationIndexRecord> hit : response.hits().hits()) {
                DocumentNavigationIndexRecord source = hit.source();
                if (source == null || source.getNodeId() == null) {
                    continue;
                }
                hits.add(new NavigationSectionHit(
                    source.getNodeId(),
                    safeText(source.getNodeCode()),
                    safeText(source.getTitle()),
                    safeText(source.getSectionPath()),
                    safeText(source.getCanonicalPath()),
                    hit.score() == null ? 0D : hit.score()
                ));
            }
            log.info("导航索引搜索完成: documentId={}, hitCount={}, topHits={}",
                documentId,
                hits.size(),
                hits.stream().limit(3).map(hit -> hit.nodeId() + ":" + hit.sectionPath() + ":" + hit.score()).toList());
            return hits;
        }
        catch (IOException exception) {
            // ES 搜索失败时返回空列表，由上层回退到结构图兜底匹配
            log.warn("导航索引章节搜索失败，自动回退到结构图兜底匹配: documentId={}, question='{}', error={}",
                documentId, safeText(question), exception.getMessage());
            return List.of();
        }
    }

    /**
     * 向 Bool Query 中添加章节搜索的 should 条件
     */
    private void addSectionShouldQueries(co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder bool,
                                         String queryText) {
        if (StrUtil.isBlank(queryText)) {
            return;
        }
        // title 短语匹配，权重最高
        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
            .field("title")
            .query(queryText)
            .boost(20.0f)
        ));
        // sectionPath 短语匹配
        bool.should(should -> should.matchPhrase(matchPhrase -> matchPhrase
            .field("sectionPath")
            .query(queryText)
            .boost(15.0f)
        ));
        // multi_match 全文搜索（多个字段加权）
        bool.should(should -> should.multiMatch(multiMatch -> multiMatch
            .query(queryText)
            .fields("title^10", "sectionPath^8", "anchorText^5", "contentText")
            .type(TextQueryType.BestFields)
        ));
    }

    /**
     * 构建查询文本列表（合并 topic、facet、informationNeed、question，去重）
     */
    private List<String> buildQueries(String topic, String facet, String informationNeed, String question) {
        List<String> queries = new ArrayList<>();
        addNonBlank(queries, topic);
        addNonBlank(queries, facet);
        addNonBlank(queries, informationNeed);
        addNonBlank(queries, question);
        return queries.stream().distinct().toList();
    }

    private void addNonBlank(List<String> queries, String value) {
        if (StrUtil.isNotBlank(value)) {
            queries.add(value.trim());
        }
    }

    /**
     * 将数据库结构节点实体转为 ES 索引记录
     */
    private DocumentNavigationIndexRecord toIndexRecord(KnowHubDocumentStructureNode node, Long parseTaskId) {
        DocumentStructureNodeTypeEnum nodeType = DocumentStructureNodeTypeEnum.getRc(node.getNodeType());
        return DocumentNavigationIndexRecord.builder()
            .nodeId(node.getId())
            .documentId(node.getDocumentId())
            .parseTaskId(node.getParseTaskId() == null ? parseTaskId : node.getParseTaskId())
            .nodeType(nodeType == null ? "" : nodeType.name())
            .nodeCode(safeText(node.getNodeCode()))
            .nodeNo(node.getNodeNo())
            .depth(node.getDepth())
            .parentNodeId(node.getParentNodeId())
            .title(safeText(node.getTitle()))
            .anchorText(safeText(node.getAnchorText()))
            .sectionPath(safeText(node.getSectionPath()))
            .canonicalPath(safeText(node.getCanonicalPath()))
            .contentText(safeText(node.getContentText()))
            .itemIndex(node.getItemIndex())
            .build();
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
