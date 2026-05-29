package ai.knowhub.document.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.mq.message.DocumentIndexBuildMessage;
import ai.knowhub.document.mq.message.DocumentParseRouteMessage;
import ai.knowhub.document.service.DocumentAsyncProcessService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static ai.knowhub.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * 文档 Kafka 消息消费者（Document Kafka Consumer）
 *
 * 【类的作用】
 * 监听 Kafka 消息队列中的文档处理消息，并调用业务服务执行异步处理。
 * 本类是异步文档处理流程的入口，将 Kafka 消息转化为业务操作。
 *
 * 【在架构中的角色】
 * 属于文档管理的消息队列（MQ）层，是异步处理流程的执行者：
 * DocumentKafkaProducer（生产者）-> Kafka（消息队列）-> DocumentKafkaConsumer（消费者）
 *                                                                   |
 *                                                          DocumentAsyncProcessService（业务处理）
 *
 * 【Kafka 消费者基础概念】
 * - @KafkaListener：Spring Kafka 提供的注解，声明一个方法为 Kafka 消息监听器
 * - topics：监听的 Kafka topic 名称
 * - groupId：消费者组 ID，同一组内的消费者共享消息消费
 *   （每条消息只会被组内的一个消费者处理，实现负载均衡）
 *
 * 【消费者组的作用】
 * 通过不同的 groupId 将两种消息的消费隔离开：
 * - parse 消费者组：处理文档解析路由消息
 * - index 消费者组：处理文档索引构建消息
 * 两个组独立消费，互不影响，可以分别扩缩容。
 *
 * 【Topic 命名规则】
 * Topic 名称格式：{环境前缀}-{配置的topic名称}
 * 环境前缀通过 SPRING_INJECT_PREFIX_DISTINCTION_NAME 常量注入，
 * 确保不同环境使用不同的 topic。
 *
 * 【异常处理策略】
 * 消费失败时仅记录日志，不抛出异常，避免 Kafka 消费者因异常而停止消费。
 * 这是一种"尽力而为"的处理策略，适合非关键路径的异步任务。
 * 对于关键场景，可以考虑加入重试机制或死信队列（DLQ）。
 *
 * 【设计模式】
 * - 消费者模式（Consumer Pattern）：Kafka 消息消费者的标准实现
 * - 事件驱动模式（Event-Driven Pattern）：通过消息队列实现事件驱动的异步处理
 * - 委托模式（Delegation Pattern）：消费者本身不处理业务逻辑，委托给 DocumentAsyncProcessService
 */
@Slf4j
@Component
public class DocumentKafkaConsumer {

    /**
     * 文档异步处理服务
     * 实际执行文档解析、路由索引构建、检索索引构建等业务逻辑的服务。
     * 消费者只负责接收消息和反序列化，具体处理逻辑委托给此服务。
     */
    private final DocumentAsyncProcessService asyncProcessService;

    /**
     * JSON 反序列化器
     * Jackson 提供的 JSON 处理工具，用于将 JSON 字符串反序列化为 Java 对象。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入依赖
     *
     * @param asyncProcessService 文档异步处理服务
     * @param objectMapper        JSON 反序列化器
     */
    public DocumentKafkaConsumer(DocumentAsyncProcessService asyncProcessService,
                                 ObjectMapper objectMapper) {
        this.asyncProcessService = asyncProcessService;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费文档解析路由消息
     *
     * 【监听配置】
     * - topic：{环境前缀}-{parse-topic配置值}
     * - groupId：{group-id配置值}-parse
     *
     * 【处理流程】
     * 1. 接收 Kafka 消息（JSON 字符串）
     * 2. 反序列化为 DocumentParseRouteMessage 对象
     * 3. 调用 asyncProcessService.handleParseRoute() 执行文档解析和路由构建
     *
     * @param payload Kafka 消息体（JSON 字符串）
     */
    @KafkaListener(topics = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"${app.manage.kafka.parse-topic}", groupId = "${app.manage.kafka.group-id}-parse")
    public void consumeParseRoute(String payload) {
        try {

            // Kafka 消息体是 JSON 字符串，先反序列化成任务消息，再交给业务服务处理。
            DocumentParseRouteMessage message = objectMapper.readValue(payload, DocumentParseRouteMessage.class);

            asyncProcessService.handleParseRoute(message.getDocumentId(), message.getTaskId());
        }
        catch (Exception exception) {

            log.error("消费解析路由消息失败，payload={}", payload, exception);
        }
    }

    /**
     * 消费文档索引构建消息
     *
     * 【监听配置】
     * - topic：{环境前缀}-{index-topic配置值}
     * - groupId：{group-id配置值}-index
     *
     * 【处理流程】
     * 1. 接收 Kafka 消息（JSON 字符串）
     * 2. 反序列化为 DocumentIndexBuildMessage 对象
     * 3. 调用 asyncProcessService.handleIndexBuild() 执行索引构建
     *
     * @param payload Kafka 消息体（JSON 字符串）
     */
    @KafkaListener(topics = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"${app.manage.kafka.index-topic}", groupId = "${app.manage.kafka.group-id}-index")
    public void consumeIndexBuild(String payload) {
        try {

            // 索引构建消息只携带关键 ID，详细文档、方案和任务状态都从数据库重新读取。
            DocumentIndexBuildMessage message = objectMapper.readValue(payload, DocumentIndexBuildMessage.class);

            asyncProcessService.handleIndexBuild(message.getDocumentId(), message.getTaskId(), message.getPlanId());
        }
        catch (Exception exception) {
            log.error("消费索引构建消息失败，payload={}", payload, exception);
        }
    }
}
