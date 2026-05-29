package ai.knowhub.document.config;

import org.apache.kafka.clients.admin.NewTopic;
import ai.knowhub.document.config.DocumentManageProperties.Kafka;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 文档管理模块的 Kafka 配置类。
 *
 * 本类负责自动创建文档管理模块所需的 Kafka Topic（主题）。
 * Kafka 在本系统中承担异步消息队列的角色，用于解耦文档上传和后续的解析、索引构建流程。
 *
 * Topic 说明：
 * 
 *   <b>文档解析 Topic</b>（parseTopic）：文档上传后发送消息到此 Topic，
 *       消费者负责执行文档解析（提取文本、识别结构等）。
 *   <b>索引构建 Topic</b>（indexTopic）：文档解析完成后发送消息到此 Topic，
 *       消费者负责将解析结果写入 Elasticsearch 和向量数据库。
 * 条件装配：
 * 
 *   EnableKafka：启用 Spring Kafka 的注解驱动消费者功能。
 *   ConditionalOnProperty：仅在 app.manage.kafka.auto-create-topics=true 时
 *       自动创建 Topic（默认为 true）。在生产环境中，可能由运维手动创建 Topic 并配置更多分区和副本。
 * 设计模式：配置类模式 —— 将基础设施配置从业务逻辑中分离出来。
 */
@EnableKafka
@Configuration
@EnableConfigurationProperties(DocumentManageProperties.class)
public class DocumentManageKafkaConfiguration {

    /**
     * 创建文档解析 Topic。
     *
     * 此 Topic 用于文档上传后的异步解析流程。当用户上传文档时，
     * 系统会将文档信息封装成消息发送到此 Topic，由后台消费者异步执行解析。
     *
     * 注意：partitions=1, replicas=1 是开发环境的默认配置。
     * 生产环境应根据吞吐量需求增加分区数和副本数。
     *
     * @param properties 文档管理配置属性（包含 Topic 名称等）
     * @return Kafka Topic 定义对象
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.manage.kafka", name = "auto-create-topics", havingValue = "true", matchIfMissing = true)
    public NewTopic documentParseTopic(DocumentManageProperties properties) {
        Kafka kafka = properties.getKafka();
        return TopicBuilder.name(kafka.getParseTopic()).partitions(1).replicas(1).build();
    }

    /**
     * 创建索引构建 Topic。
     *
     * 此 Topic 用于文档解析完成后的异步索引构建流程。解析完成后，
     * 系统将解析结果（切块文本、向量等）通过此 Topic 触发索引写入操作。
     *
     * @param properties 文档管理配置属性（包含 Topic 名称等）
     * @return Kafka Topic 定义对象
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.manage.kafka", name = "auto-create-topics", havingValue = "true", matchIfMissing = true)
    public NewTopic documentIndexTopic(DocumentManageProperties properties) {
        Kafka kafka = properties.getKafka();
        return TopicBuilder.name(kafka.getIndexTopic()).partitions(1).replicas(1).build();
    }
}
