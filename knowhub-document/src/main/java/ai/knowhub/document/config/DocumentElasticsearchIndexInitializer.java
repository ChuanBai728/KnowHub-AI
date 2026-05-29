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
 * 文档 Elasticsearch 索引初始化器。
 *
 * 本类的作用是在 Spring 容器启动时，自动检查并创建文档管理模块所需的 Elasticsearch 索引。
 * 它属于「基础设施层」的配置组件，确保应用运行前索引结构已经就绪。
 *
 * 设计要点：
 * 
 *   使用 PostConstruct 注解，在 Bean 初始化完成后立即执行索引检查/创建。
 *   使用 ConditionalOnProperty 注解，仅在配置项 app.manage.elasticsearch.enabled=true 时才生效，
 *       这样在不需要 ES 的环境中可以关闭此功能。
 *   内置 IK 分词器回退机制：如果配置了 IK 分词器（ik_max_word/ik_smart）但 ES 中没有安装 IK 插件，
 *       会自动回退到 standard 分词器，保证服务能正常启动。
 * 涉及的 Elasticsearch 字段映射包括：chunkId、documentId、taskId、chunkNo、documentName、
 * sectionPath、structureNodeId、structureNodeType、canonicalPath、itemIndex、
 * knowledgeScopeCode、knowledgeScopeName、businessCategory、documentTags、chunkText。
 *
 * 设计模式：模板方法模式（indexExists/createIndex 为模板步骤，子类可覆盖）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.manage.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentElasticsearchIndexInitializer {

    /**
     * Elasticsearch 客户端，通过 Qualifier 注入文档管理模块专用的客户端 Bean。
     * 使用限定符 "documentManageElasticsearchClient" 避免与其他模块的 ES 客户端冲突。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * 文档管理模块的统一配置属性对象，包含 ES 连接信息、索引名称、分词器等配置。
     */
    private final DocumentManageProperties properties;

    /**
     * 构造器注入 Elasticsearch 客户端和配置属性。
     *
     * @param elasticsearchClient 文档管理专用的 Elasticsearch 客户端（由 Qualifier 指定）
     * @param properties          文档管理模块的配置属性（包含 ES 索引名、分词器等）
     */
    public DocumentElasticsearchIndexInitializer(
        @Qualifier("documentManageElasticsearchClient") ElasticsearchClient elasticsearchClient,
        DocumentManageProperties properties) {
        this.elasticsearchClient = elasticsearchClient;
        this.properties = properties;
    }

    /**
     * 初始化 Elasticsearch 索引。
     *
     * 在 Bean 创建后自动执行（PostConstruct）。流程如下：
     * 
     *   从配置中读取索引名称、搜索分词器和索引分词器。
     *   检查索引是否已存在，若存在则跳过创建（幂等操作）。
     *   若不存在则创建索引并配置字段映射。
     *   若创建失败且使用了 IK 分词器，则回退到 standard 分词器重试。
     * 
     */
    @PostConstruct
    public void initIndex() {
        // 从统一配置对象中获取 Elasticsearch 相关配置
        DocumentManageProperties.Elasticsearch elasticsearch = properties.getElasticsearch();
        String indexName = elasticsearch.getIndexName();
        String analyzer = elasticsearch.getAnalyzer();           // 索引时使用的分词器，如 ik_max_word
        String searchAnalyzer = elasticsearch.getSearchAnalyzer(); // 搜索时使用的分词器，如 ik_smart
        try {
            // 幂等检查：索引已存在则跳过，避免重复创建
            if (indexExists(indexName)) {
                log.info("Elasticsearch 索引 [{}] 已存在，跳过创建。", indexName);
                return;
            }
            // 索引不存在，执行创建
            createIndex(indexName, analyzer, searchAnalyzer);
            log.info("Elasticsearch 索引 [{}] 创建完成，analyzer={}, searchAnalyzer={}",
                indexName, analyzer, searchAnalyzer);
        }
        catch (Exception exception) {
            // 如果使用了 IK 分词器但创建失败（可能是 ES 没装 IK 插件），则回退到 standard
            if (isIkAnalyzer(analyzer) || isIkAnalyzer(searchAnalyzer)) {
                log.warn("使用 IK 分词器创建 Elasticsearch 索引失败，准备回退到 standard。原因: {}", exception.getMessage());
                fallbackToStandard(indexName);
                return;
            }
            log.error("初始化 Elasticsearch 索引失败: {}", exception.getMessage(), exception);
        }
    }

    /**
     * 检查指定名称的 Elasticsearch 索引是否已存在。
     *
     * @param indexName 索引名称
     * @return 如果索引存在返回 true，否则返回 false
     * @throws IOException 网络或 ES 通信异常
     */
    private boolean indexExists(String indexName) throws IOException {
        return elasticsearchClient.indices().exists(ExistsRequest.of(exists -> exists.index(indexName))).value();
    }

    /**
     * 创建 Elasticsearch 索引并配置字段映射（mapping）。
     *
     * 字段类型说明：
     * 
     *   <b>keyword</b>：不分词的精确匹配字段，适用于 ID、编码、标签等。
     *   <b>text</b>：全文检索字段，会经过分词器处理，适用于名称、路径、正文等。
     *   <b>long/integer</b>：数值类型字段，适用于 ID、编号等。
     * @param indexName      索引名称
     * @param analyzer       索引时使用的分词器
     * @param searchAnalyzer 搜索时使用的分词器
     * @throws IOException 网络或 ES 通信异常
     */
    private void createIndex(String indexName, String analyzer, String searchAnalyzer) throws IOException {
        elasticsearchClient.indices().create(create -> create
            .index(indexName)
            .mappings(mapping -> mapping
                // chunkId：文档切块的唯一标识，keyword 类型用于精确匹配
                .properties("chunkId", property -> property.keyword(keyword -> keyword))
                // documentId：关联的文档 ID
                .properties("documentId", property -> property.long_(number -> number))
                // taskId：关联的解析任务 ID
                .properties("taskId", property -> property.long_(number -> number))
                // chunkNo：切块在文档中的序号
                .properties("chunkNo", property -> property.integer(number -> number))
                // documentName：文档名称，text 类型支持全文检索
                .properties("documentName", property -> property.text(text -> text
                    .analyzer(analyzer)
                    .searchAnalyzer(searchAnalyzer)))
                // sectionPath：切块在文档结构中的路径（如 "第1章 > 第2节 > 第3小节"）
                .properties("sectionPath", property -> property.text(text -> text
                    .analyzer(analyzer)
                    .searchAnalyzer(searchAnalyzer)))
                // structureNodeId：关联的结构节点 ID
                .properties("structureNodeId", property -> property.long_(number -> number))
                // structureNodeType：结构节点类型（如标题、段落、表格等）
                .properties("structureNodeType", property -> property.integer(number -> number))
                // canonicalPath：结构节点的规范化路径标识
                .properties("canonicalPath", property -> property.keyword(keyword -> keyword))
                // itemIndex：在父级节点中的序号
                .properties("itemIndex", property -> property.integer(number -> number))
                // knowledgeScopeCode：知识范围编码，keyword 用于精确筛选
                .properties("knowledgeScopeCode", property -> property.keyword(keyword -> keyword))
                // knowledgeScopeName：知识范围名称，text 类型支持模糊搜索
                .properties("knowledgeScopeName", property -> property.text(text -> text
                    .analyzer(analyzer)
                    .searchAnalyzer(searchAnalyzer)))
                // businessCategory：业务分类标签
                .properties("businessCategory", property -> property.keyword(keyword -> keyword))
                // documentTags：文档标签，keyword 数组类型
                .properties("documentTags", property -> property.keyword(keyword -> keyword))
                // chunkText：切块正文内容，text 类型用于全文检索，是 RAG 检索的核心字段
                .properties("chunkText", property -> property.text(text -> text
                    .analyzer(analyzer)
                    .searchAnalyzer(searchAnalyzer)))
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
     * 这是一种降级策略，确保即使没有安装 IK 插件，索引也能被创建成功。
     * 代价是中文分词效果会变差（standard 按单字拆分），但系统仍然可用。
     *
     * @param indexName 索引名称
     */
    private void fallbackToStandard(String indexName) {
        try {
            // 再次检查，避免并发场景下其他线程已创建了索引
            if (indexExists(indexName)) {
                return;
            }
            createIndex(indexName, "standard", "standard");
            log.info("Elasticsearch 索引 [{}] 已回退到 standard 分词器。", indexName);
        }
        catch (Exception exception) {
            log.error("回退创建 Elasticsearch 索引失败: {}", exception.getMessage(), exception);
        }
    }
}
