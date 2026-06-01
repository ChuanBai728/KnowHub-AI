package ai.knowhub.document.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.mq.message.DocumentIndexBuildMessage;
import ai.knowhub.document.mq.message.DocumentParseRouteMessage;
import ai.knowhub.core.SpringUtil;
import ai.knowhub.enums.DocumentManageCode;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 文档 Kafka 消息生产者（Document Kafka Producer）
 *
 * 【类的作用】
 * 负责向 Kafka 消息队列发送文档处理相关的异步任务消息。
 * 当文档上传、更新或需要重建索引时，通过此类发送消息触发异步处理。
 *
 * 【在架构中的角色】
 * 属于文档管理的消息队列（MQ）层，是异步处理流程的入口：
 * - 同步操作（接收文档上传请求）-> 发送 Kafka 消息 -> 异步处理（解析、索引）
 * - 这种设计避免了文档处理阻塞用户请求，提高系统响应速度
 *
 * 【Kafka 基础概念】
 * - Topic（主题）：消息的分类通道，类似消息的"邮箱地址"
 * - Key（键）：消息的分区键，相同 key 的消息会被发送到同一个分区，
 *   保证同一文档的消息有序处理
 * - Producer（生产者）：发送消息的一方
 * - Consumer（消费者）：接收消息的一方
 *
 * 【消息发送流程】
 * 1. 调用 sendParseRoute() 或 sendIndexBuild() 方法
 * 2. 方法内部调用 send()，将消息对象序列化为 JSON
 * 3. 通过 KafkaTemplate 发送到指定的 Kafka topic
 * 4. 使用 .get() 同步等待发送结果，确保消息发送成功
 *
 * 【Topic 命名规则】
 * Topic 名称格式：{环境前缀}-{配置的topic名称}
 * 例如：dev-document-parse-route、prod-document-index-build
 * 环境前缀通过 SpringUtil.getPrefixDistinctionName() 获取，
 * 确保不同环境（开发、测试、生产）使用不同的 topic，互不干扰。
 *
 * 【设计模式】
 * - 生产者模式（Producer Pattern）：Kafka 消息生产者的标准实现
 * - 模板方法模式（Template Method Pattern）：通过 KafkaTemplate 封装底层发送逻辑
 */
@AllArgsConstructor
@Component
public class DocumentKafkaProducer {

    /**
     * Kafka 模板
     * Spring Kafka 提供的发送消息工具类，
     * 封装了 Kafka 生产者的创建、序列化、发送等底层逻辑。
     * 泛型参数 <String, String> 表示 key 和 value 都是字符串类型。
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * JSON 序列化器
     * Jackson 提供的 JSON 处理工具，用于将 Java 对象序列化为 JSON 字符串。
     */
    private final ObjectMapper objectMapper;

    /**
     * 文档管理配置属性
     * 从 application.yml 中读取的文档管理相关配置，
     * 包含 Kafka topic 名称等配置项。
     */
    private final DocumentManageProperties properties;

    /**
     * 发送文档解析路由消息
     *
     * 【使用场景】
     * 当新文档上传或文档更新需要重新解析时调用。
     * 消费者收到消息后会执行文档解析和路由索引构建。
     *
     * @param message 解析路由消息，包含文档 ID 和任务 ID
     */
    public void sendParseRoute(DocumentParseRouteMessage message) {

        send(SpringUtil.getPrefixDistinctionName() + "-" + properties.getKafka().getParseTopic(), String.valueOf(message.getDocumentId()), message);
    }

    /**
     * 发送文档索引构建消息
     *
     * 【使用场景】
     * 当文档解析完成后，需要将解析结果写入 Elasticsearch 时调用。
     * 消费者收到消息后会执行索引构建。
     *
     * @param message 索引构建消息，包含文档 ID、任务 ID 和方案 ID
     */
    public void sendIndexBuild(DocumentIndexBuildMessage message) {

        send(SpringUtil.getPrefixDistinctionName() + "-" + properties.getKafka().getIndexTopic(), String.valueOf(message.getDocumentId()), message);
    }

    /**
     * 统一的消息发送方法
     *
     * 【发送流程】
     * 1. 使用 ObjectMapper 将消息对象序列化为 JSON 字符串
     * 2. 调用 kafkaTemplate.send() 发送到指定 topic
     * 3. 使用 .get() 同步等待发送结果（阻塞直到 broker 确认收到）
     * 4. 如果发送失败，抛出自定义异常 KnowHubFrameException
     *
     * 【异常处理】
     * 捕获所有异常并包装为 KnowHubFrameException，携带 KAFKA_SEND_FAILED 错误码，
     * 便于上层统一处理和日志记录。
     *
     * @param topic   Kafka topic 名称（已包含环境前缀）
     * @param key     消息分区键（使用文档 ID，保证同一文档的消息有序）
     * @param message 消息对象（会被序列化为 JSON）
     * @throws KnowHubFrameException 当消息发送失败时抛出
     */
    private void send(String topic, String key, Object message) {
        try {

            String payload = objectMapper.writeValueAsString(message);

            kafkaTemplate.send(topic, key, payload).get();
        } catch (Exception exception) {
            throw new KnowHubFrameException(DocumentManageCode.KAFKA_SEND_FAILED.getCode(),
                "Kafka 消息发送失败: " + exception.getMessage(), exception);
        }
    }
}
