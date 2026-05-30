package ai.knowhub.document.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Neo4j 驱动自动配置。
 *
 * 当 app.manage.neo4j.enabled=true 时，创建并注册 Neo4j Driver Bean，
 * 供结构图谱投影和查询服务使用。连接超时时间从配置文件中读取。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.manage.neo4j", name = "enabled", havingValue = "true")
public class DocumentManageNeo4jConfiguration {

    @Bean("documentManageNeo4jDriver")
    public Driver documentManageNeo4jDriver(DocumentManageProperties properties) {
        DocumentManageProperties.Neo4j neo4j = properties.getNeo4j();
        Config config = Config.builder()
            .withConnectionTimeout(neo4j.getQueryTimeoutSeconds(), TimeUnit.SECONDS)
            .build();
        return GraphDatabase.driver(
            neo4j.getUri(),
            AuthTokens.basic(neo4j.getUsername(), neo4j.getPassword()),
            config
        );
    }
}
