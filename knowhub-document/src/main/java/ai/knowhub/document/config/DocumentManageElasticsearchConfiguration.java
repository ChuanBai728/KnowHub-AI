package ai.knowhub.document.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档管理模块的 Elasticsearch 自动配置类。
 *
 * 本类负责创建文档管理模块专用的 Elasticsearch 客户端及相关基础设施 Bean。
 * 采用「三层 Bean」的构建模式：RestClient → Transport → Client，逐层封装。
 *
 * 为什么需要专用的 ES 客户端？
 * 
 *   项目中可能有多个模块使用 Elasticsearch（如 RAG 检索、文档管理），每个模块的
 *       连接参数、索引配置可能不同，因此需要独立的客户端实例。
 *   通过 Qualifier 限定符区分不同模块的客户端，避免 Bean 注入冲突。
 * 条件装配：使用 ConditionalOnProperty 注解，仅在
 * app.manage.elasticsearch.enabled=true 时生效（默认为 true）。
 *
 * 配置绑定：通过 EnableConfigurationProperties 绑定 DocumentManageProperties，
 * 从 application.yml 的 app.manage 前缀下读取配置。
 *
 * 设计模式：工厂模式 —— 本类充当 Bean 工厂，封装了 ES 客户端的创建逻辑。
 */
@Configuration
@EnableConfigurationProperties(DocumentManageProperties.class)
@ConditionalOnProperty(prefix = "app.manage.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentManageElasticsearchConfiguration {

    /**
     * 创建 Elasticsearch 底层 REST 客户端。
     *
     * RestClient 是与 Elasticsearch HTTP API 通信的最底层组件，负责发送 HTTP 请求。
     *
     * 配置项包括：
     * 
     *   集群地址列表（uris）：支持多节点高可用部署。
     *   连接超时和 Socket 超时：防止网络问题导致长时间阻塞。
     *   认证信息（可选）：如果 ES 配置了安全认证，会自动添加用户名密码。
     * destroyMethod = "close"：Spring 容器关闭时自动关闭底层 HTTP 连接池，防止资源泄漏。
     *
     * @param properties 文档管理配置属性
     * @return Elasticsearch REST 客户端实例
     * @throws IllegalStateException 如果 URIs 列表为空时抛出
     */
    @Bean(name = "documentManageElasticsearchRestClient", destroyMethod = "close")
    public RestClient documentManageElasticsearchRestClient(DocumentManageProperties properties) {
        DocumentManageProperties.Elasticsearch elasticsearch = properties.getElasticsearch();
        // 校验：ES 地址列表不能为空，否则无法建立连接
        if (CollUtil.isEmpty(elasticsearch.getUris())) {
            throw new IllegalStateException("app.manage.elasticsearch.uris 不能为空");
        }

        // 将配置中的 URI 字符串列表转换为 HttpHost 数组
        // filter 过滤掉空白字符串，map 将 URI 字符串转为 HttpHost 对象
        HttpHost[] hosts = elasticsearch.getUris().stream()
            .filter(StrUtil::isNotBlank)
            .map(HttpHost::create)
            .toArray(HttpHost[]::new);

        // 构建 RestClient，配置超时参数
        org.elasticsearch.client.RestClientBuilder builder = RestClient.builder(hosts)
            .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(elasticsearch.getConnectTimeoutMillis())  // 建立连接的超时时间
                .setSocketTimeout(elasticsearch.getSocketTimeoutMillis()));  // 等待数据的超时时间

        // 如果配置了用户名，则添加认证信息
        // 这样可以兼容开启了 X-Pack 安全特性的 ES 集群
        if (StrUtil.isNotBlank(elasticsearch.getUsername())) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                AuthScope.ANY,  // 对所有认证范围生效
                new UsernamePasswordCredentials(
                    elasticsearch.getUsername(),
                    StrUtil.blankToDefault(elasticsearch.getPassword(), "")  // 密码为空时默认使用空字符串
                )
            );
            builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                .setDefaultCredentialsProvider(credentialsProvider));
        }
        return builder.build();
    }

    /**
     * 创建 Elasticsearch 传输层对象。
     *
     * Transport 层负责将高层 API 调用转换为底层 HTTP 请求。
     * 这里使用 JacksonJsonpMapper 将 JSON 序列化/反序列化委托给 Jackson，
     * 与项目中其他模块的 JSON 处理保持一致。
     *
     * @param restClient   底层 REST 客户端（由上面的 Bean 注入）
     * @param objectMapper Spring 管理的 Jackson ObjectMapper（自动配置，支持自定义序列化）
     * @return Elasticsearch 传输层实例
     */
    @Bean(name = "documentManageElasticsearchTransport", destroyMethod = "close")
    public ElasticsearchTransport documentManageElasticsearchTransport(
        @Qualifier("documentManageElasticsearchRestClient") RestClient restClient,
        com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    /**
     * 创建 Elasticsearch 高级客户端。
     *
     * 这是业务代码中实际使用的客户端，提供类型安全的 API 来操作索引、文档、搜索等。
     * 所有 ES 操作都通过此 Bean 进行。
     *
     * @param transport 传输层实例
     * @return Elasticsearch 高级客户端实例
     */
    @Bean(name = "documentManageElasticsearchClient")
    public ElasticsearchClient documentManageElasticsearchClient(
        @Qualifier("documentManageElasticsearchTransport") ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
