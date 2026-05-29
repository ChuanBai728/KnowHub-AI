package ai.knowhub.document.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档索引构建消息（Document Index Build Message）
 *
 * 【类的作用】
 * 封装通过 Kafka 消息队列传递的"文档索引构建"任务消息。
 * 当文档解析完成后，需要将解析结果写入 Elasticsearch 建立索引，
 * 此消息用于触发异步的索引构建流程。
 *
 * 【在架构中的角色】
 * 属于文档处理异步流程的消息模型：
 * 1. 文档上传 -> 解析任务完成 -> 发送此消息到 Kafka
 * 2. DocumentKafkaConsumer 消费此消息
 * 3. DocumentAsyncProcessService.handleIndexBuild() 执行索引构建
 *
 * 【Kafka 消息传递流程】
 * Producer（生产者）: DocumentKafkaProducer.sendIndexBuild()
 *   -> 将此消息序列化为 JSON，发送到 Kafka 的索引构建 topic
 * Consumer（消费者）: DocumentKafkaConsumer.consumeIndexBuild()
 *   -> 从 Kafka 接收 JSON，反序列化为此消息对象
 *   -> 调用业务服务处理索引构建
 *
 * 【设计模式】
 * - 消息对象模式（Message Object Pattern）：将消息内容封装为一个对象，
 *   通过 JSON 序列化在生产者和消费者之间传递。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIndexBuildMessage {

    /**
     * 文档 ID
     * 需要构建索引的文档的唯一标识。
     * 消费者通过此 ID 从数据库读取文档的完整信息。
     */
    private Long documentId;

    /**
     * 任务 ID
     * 索引构建任务的唯一标识，用于：
     * - 追踪任务状态
     * - 区分同一文档的多次索引构建
     * - 记录任务日志
     */
    private Long taskId;

    /**
     * 方案 ID
     * 索引构建方案的唯一标识。
     * 不同的文档可能使用不同的索引构建方案
     * （例如不同分块策略、不同向量化模型等），
     * 此 ID 用于获取具体的构建方案配置。
     */
    private Long planId;
}
