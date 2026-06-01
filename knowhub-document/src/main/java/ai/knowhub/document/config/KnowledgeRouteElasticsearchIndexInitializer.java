package ai.knowhub.document.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 知识路由 Elasticsearch 索引初始化器。
 *
 * 本类在 Spring 容器启动时，自动创建知识路由索引。
 * 该索引用于存储知识范围（Scope）和知识主题（Topic）的路由信息，
 * 支持在 RAG 检索过程中快速定位用户问题应该路由到哪个知识领域。
 *
 * 「知识路由」是本系统 RAG 检索流程中的关键环节：
 * 
 *   用户提出问题。
 *   系统通过知识路由索引，将问题映射到最相关的知识范围和主题。
 *   根据路由结果，缩小检索范围，只在相关文档中进行语义检索。
 *   这样可以显著提高检索的精确率和响应速度。
 * 知识路由索引存储的信息包括：
 * 
 *   路由实体的基本信息（编码、名称、描述、别名等）。
 *   关联的文档信息（文档 ID、名称、分类等）。
 *   语义文本字段（用于全文检索匹配用户问题）。
 *   标签和关键词字段（用于精确筛选）。
 * 设计模式：模板方法模式（与其他 ES 索引初始化器结构一致）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.manage.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeRouteElasticsearchIndexInitializer {

    /**
     * Elasticsearch 客户端，通过限定符注入文档管理专用的客户端。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * 文档管理模块的配置属性，包含路由索引名称和分词器配置。
     */
    private final DocumentManageProperties properties;

    /**
     * 构造器注入 Elasticsearch 客户端和配置属性。
     *
     * @param elasticsearchClient 文档管理专用的 Elasticsearch 客户端
     * @param properties          文档管理配置属性
     */
    public KnowledgeRouteElasticsearchIndexInitializer(
        @Qualifier("documentManageElasticsearchClient") ElasticsearchClient elasticsearchClient,
        DocumentManageProperties properties) {
        this.elasticsearchClient = elasticsearchClient;
        this.properties = properties;
    }

    /**
     * 初始化知识路由索引。
     *
     * 在 Bean 创建后自动执行。流程与其他索引初始化器一致：
     * 检查是否存在 → 不存在则创建 → IK 失败则回退 standard。
     */
    @PostConstruct
    public void initIndex() {
        DocumentManageProperties.Elasticsearch elasticsearch = properties.getElasticsearch();
        String indexName = elasticsearch.getRouteIndexName();
        String analyzer = elasticsearch.getAnalyzer();
        String searchAnalyzer = elasticsearch.getSearchAnalyzer();
        try {
            if (indexExists(indexName)) {
                log.info("Elasticsearch 知识路由索引 [{}] 已存在，跳过创建。", indexName);
                return;
            }
            createIndex(indexName, analyzer, searchAnalyzer);
            log.info("Elasticsearch 知识路由索引 [{}] 创建完成，analyzer={}, searchAnalyzer={}",
                indexName, analyzer, searchAnalyzer);
        }
        catch (Exception exception) {
            if (isIkAnalyzer(analyzer) || isIkAnalyzer(searchAnalyzer)) {
                log.warn("使用 IK 分词器创建知识路由索引失败，准备回退到 standard。原因: {}", exception.getMessage());
                fallbackToStandard(indexName);
                return;
            }
            log.error("初始化知识路由索引失败: {}", exception.getMessage(), exception);
        }
    }

    /**
     * 检查指定名称的 Elasticsearch 索引是否已存在。
     *
     * @param indexName 索引名称
     * @return 如果索引存在返回 true
     * @throws IOException 网络或 ES 通信异常
     */
    private boolean indexExists(String indexName) throws IOException {
        return elasticsearchClient.indices().exists(ExistsRequest.of(exists -> exists.index(indexName))).value();
    }

    /**
     * 创建知识路由索引并配置字段映射。
     *
     * 字段映射说明：
     * 
     *   <b>routeId</b>：路由记录的唯一标识。
     *   <b>entityType</b>：实体类型（如 "scope" 表示知识范围，"topic" 表示知识主题）。
     *   <b>entityCode</b>：实体编码（如 "finance.accounting"）。
     *   <b>documentId</b>：关联的文档 ID。
     *   <b>scopeCode/scopeName</b>：所属知识范围的编码和名称。
     *   <b>topicCode/topicName</b>：所属知识主题的编码和名称。
     *   <b>documentName</b>：文档名称。
     *   <b>businessCategory</b>：业务分类。
     *   <b>displayName</b>：显示名称。
     *   <b>descriptionText</b>：描述文本，用于语义匹配。
     *   <b>aliasesText</b>：别名文本，用于扩大匹配范围。
     *   <b>examplesText</b>：示例文本，用于语义匹配。
     *   <b>summaryText</b>：摘要文本。
     *   <b>routeText</b>：路由文本（综合了名称、描述、别名等）。
     *   <b>entityTerms</b>：实体关键词，keyword 类型用于精确匹配。
     *   <b>tags</b>：标签，keyword 类型用于精确筛选。
     * @param indexName      索引名称
     * @param analyzer       索引分词器
     * @param searchAnalyzer 搜索分词器
     * @throws IOException 网络或 ES 通信异常
     */
    private void createIndex(String indexName, String analyzer, String searchAnalyzer) throws IOException {
        elasticsearchClient.indices().create(create -> create
            .index(indexName)
            .mappings(mapping -> mapping
                // routeId：路由记录唯一标识
                .properties("routeId", property -> property.keyword(keyword -> keyword))
                // entityType：实体类型（scope/topic）
                .properties("entityType", property -> property.keyword(keyword -> keyword))
                // entityCode：实体编码
                .properties("entityCode", property -> property.keyword(keyword -> keyword))
                // documentId：关联文档 ID
                .properties("documentId", property -> property.long_(number -> number))
                // scopeCode：知识范围编码
                .properties("scopeCode", property -> property.keyword(keyword -> keyword))
                // scopeName：知识范围名称，text 全文检索
                .properties("scopeName", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // topicCode：知识主题编码
                .properties("topicCode", property -> property.keyword(keyword -> keyword))
                // topicName：知识主题名称，text 全文检索
                .properties("topicName", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // documentName：文档名称，text 全文检索
                .properties("documentName", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // businessCategory：业务分类
                .properties("businessCategory", property -> property.keyword(keyword -> keyword))
                // displayName：显示名称，text 全文检索
                .properties("displayName", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // descriptionText：描述文本，text 全文检索（核心匹配字段）
                .properties("descriptionText", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // aliasesText：别名文本，text 全文检索（扩大匹配范围）
                .properties("aliasesText", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // examplesText：示例文本，text 全文检索
                .properties("examplesText", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // summaryText：摘要文本，text 全文检索
                .properties("summaryText", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // routeText：路由综合文本，text 全文检索
                .properties("routeText", property -> property.text(text -> text.analyzer(analyzer).searchAnalyzer(searchAnalyzer)))
                // entityTerms：实体关键词，keyword 精确匹配
                .properties("entityTerms", property -> property.keyword(keyword -> keyword))
                // tags：标签，keyword 精确筛选
                .properties("tags", property -> property.keyword(keyword -> keyword))
            )
        );
    }

    /**
     * 判断分词器名称是否为 IK 分词器（以 "ik_" 开头）。
     *
     * @param analyzer 分词器名称
     * @return 如果是 IK 分词器返回 true
     */
    private boolean isIkAnalyzer(String analyzer) {
        return analyzer != null && analyzer.startsWith("ik_");
    }

    /**
     * 当 IK 分词器不可用时，回退使用 standard 分词器创建索引。
     *
     * @param indexName 索引名称
     */
    private void fallbackToStandard(String indexName) {
        try {
            if (indexExists(indexName)) {
                return;
            }
            createIndex(indexName, "standard", "standard");
            log.info("Elasticsearch 知识路由索引 [{}] 已回退到 standard 分词器。", indexName);
        }
        catch (Exception exception) {
            log.error("回退创建知识路由索引失败: {}", exception.getMessage(), exception);
        }
    }
}
