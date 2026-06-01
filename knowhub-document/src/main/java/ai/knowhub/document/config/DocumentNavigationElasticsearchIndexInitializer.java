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
 * 文档导航结构 Elasticsearch 索引初始化器。
 *
 * 本类在 Spring 容器启动时，自动创建文档导航结构索引。
 * 该索引用于存储文档的结构树信息（标题层级、段落节点等），
 * 支持用户在前端浏览文档的目录结构，以及基于结构的精准检索。
 *
 * 与 DocumentElasticsearchIndexInitializer（切块索引）的区别：
 * 
 *   <b>切块索引</b>：存储文档拆分后的文本片段，用于 RAG 语义检索。
 *   <b>导航索引</b>：存储文档的结构节点（标题、段落），用于目录导航和结构化查询。
 * 导航索引的典型使用场景：
 * 
 *   用户上传文档后，前端展示文档的章节结构树。
 *   用户点击某个章节，系统定位到该章节对应的切块内容。
 *   RAG 检索时，根据命中切块反查其所在的章节路径，提供上下文信息。
 * 设计模式：模板方法模式（与 DocumentElasticsearchIndexInitializer 结构一致）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.manage.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentNavigationElasticsearchIndexInitializer {

    /**
     * Elasticsearch 客户端，通过限定符注入文档管理专用的客户端。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * 文档管理模块的配置属性，包含导航索引名称和分词器配置。
     */
    private final DocumentManageProperties properties;

    /**
     * 构造器注入 Elasticsearch 客户端和配置属性。
     *
     * @param elasticsearchClient 文档管理专用的 Elasticsearch 客户端
     * @param properties          文档管理配置属性
     */
    public DocumentNavigationElasticsearchIndexInitializer(
        @Qualifier("documentManageElasticsearchClient") ElasticsearchClient elasticsearchClient,
        DocumentManageProperties properties) {
        this.elasticsearchClient = elasticsearchClient;
        this.properties = properties;
    }

    /**
     * 初始化文档导航结构索引。
     *
     * 在 Bean 创建后自动执行。流程与切块索引初始化一致：
     * 检查是否存在 → 不存在则创建 → IK 失败则回退 standard。
     */
    @PostConstruct
    public void initIndex() {
        DocumentManageProperties.Elasticsearch elasticsearch = properties.getElasticsearch();
        String indexName = elasticsearch.getNavigationIndexName();
        String analyzer = elasticsearch.getAnalyzer();
        String searchAnalyzer = elasticsearch.getSearchAnalyzer();
        try {
            if (indexExists(indexName)) {
                log.info("Elasticsearch 导航索引 [{}] 已存在，跳过创建。", indexName);
                return;
            }
            createIndex(indexName, analyzer, searchAnalyzer);
            log.info("Elasticsearch 导航索引 [{}] 创建完成，analyzer={}, searchAnalyzer={}",
                indexName, analyzer, searchAnalyzer);
        }
        catch (Exception exception) {
            if (isIkAnalyzer(analyzer) || isIkAnalyzer(searchAnalyzer)) {
                log.warn("使用 IK 分词器创建导航索引失败，准备回退到 standard。原因: {}", exception.getMessage());
                fallbackToStandard(indexName);
                return;
            }
            log.error("初始化导航索引失败: {}", exception.getMessage(), exception);
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
     * 创建导航结构索引并配置字段映射。
     *
     * 字段映射说明：
     * 
     *   <b>nodeId</b>：结构节点的唯一 ID（对应数据库中的主键）。
     *   <b>documentId</b>：所属文档的 ID。
     *   <b>parseTaskId</b>：关联的解析任务 ID。
     *   <b>nodeType</b>：节点类型（如 heading-1、heading-2、paragraph 等）。
     *   <b>nodeCode</b>：节点编码（如 "1.2.3" 表示第一章第二节第三小节）。
     *   <b>nodeNo</b>：节点在同级中的序号。
     *   <b>depth</b>：节点在树中的深度（根节点为 0）。
     *   <b>parentNodeId</b>：父节点 ID，用于构建树形结构。
     *   <b>title</b>：节点标题文本。
     *   <b>anchorText</b>：锚点文本（用于前端定位）。
     *   <b>sectionPath</b>：节点在文档中的路径（如 "第1章 > 第2节"）。
     *   <b>canonicalPath</b>：规范化路径标识。
     *   <b>contentText</b>：节点的正文内容。
     *   <b>itemIndex</b>：在父节点中的序号。
     * @param indexName      索引名称
     * @param analyzer       索引分词器
     * @param searchAnalyzer 搜索分词器
     * @throws IOException 网络或 ES 通信异常
     */
    private void createIndex(String indexName, String analyzer, String searchAnalyzer) throws IOException {
        elasticsearchClient.indices().create(create -> create
            .index(indexName)
            .mappings(mapping -> mapping
                // nodeId：结构节点 ID，keyword 精确匹配
                .properties("nodeId", property -> property.long_(number -> number))
                // documentId：所属文档 ID
                .properties("documentId", property -> property.long_(number -> number))
                // parseTaskId：解析任务 ID
                .properties("parseTaskId", property -> property.long_(number -> number))
                // nodeType：节点类型，keyword 精确筛选
                .properties("nodeType", property -> property.keyword(keyword -> keyword))
                // nodeCode：节点编码（如 "1.2.3"），keyword 精确匹配
                .properties("nodeCode", property -> property.keyword(keyword -> keyword))
                // nodeNo：节点序号
                .properties("nodeNo", property -> property.integer(number -> number))
                // depth：节点深度
                .properties("depth", property -> property.integer(number -> number))
                // parentNodeId：父节点 ID
                .properties("parentNodeId", property -> property.long_(number -> number))
                // title：节点标题，text 全文检索
                .properties("title", property -> property.text(text -> text
                    .analyzer(analyzer)
                    .searchAnalyzer(searchAnalyzer)))
                // anchorText：锚点文本
                .properties("anchorText", property -> property.text(text -> text
                    .analyzer(analyzer)
                    .searchAnalyzer(searchAnalyzer)))
                // sectionPath：章节路径
                .properties("sectionPath", property -> property.text(text -> text
                    .analyzer(analyzer)
                    .searchAnalyzer(searchAnalyzer)))
                // canonicalPath：规范化路径
                .properties("canonicalPath", property -> property.keyword(keyword -> keyword))
                // contentText：正文内容，text 全文检索
                .properties("contentText", property -> property.text(text -> text
                    .analyzer(analyzer)
                    .searchAnalyzer(searchAnalyzer)))
                // itemIndex：在父节点中的序号
                .properties("itemIndex", property -> property.integer(number -> number))
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
            log.info("Elasticsearch 导航索引 [{}] 已回退到 standard 分词器。", indexName);
        }
        catch (Exception exception) {
            log.error("回退创建导航索引失败: {}", exception.getMessage(), exception);
        }
    }
}
