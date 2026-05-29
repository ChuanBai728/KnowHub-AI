package ai.knowhub.document.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档解析路由消息（Document Parse Route Message）
 *
 * 【类的作用】
 * 封装通过 Kafka 消息队列传递的"文档解析与路由"任务消息。
 * 当新文档上传或文档更新时，需要解析文档内容并建立路由索引，
 * 此消息用于触发异步的文档解析和路由索引构建流程。
 *
 * 【在架构中的角色】
 * 属于文档处理异步流程的第一阶段消息：
 * 1. 用户上传文档 -> 创建解析任务 -> 发送此消息到 Kafka
 * 2. DocumentKafkaConsumer 消费此消息
 * 3. DocumentAsyncProcessService.handleParseRoute() 执行：
 *    a. 文档解析：将文档拆分为结构化的章节和条目
 *    b. 路由索引构建：在 Elasticsearch 中创建路由索引记录
 *    c. 图结构构建：在 Neo4j 中创建文档的树形结构
 *
 * 【与 DocumentIndexBuildMessage 的关系】
 * 两者是文档处理流程的两个阶段：
 * 1. DocumentParseRouteMessage（解析路由）：先执行，解析文档结构
 * 2. DocumentIndexBuildMessage（索引构建）：后执行，建立检索索引
 *
 * 这种两阶段设计的好处：
 * - 解耦文档解析和索引构建，各自独立重试
 * - 解析阶段可以生成多种索引结构（ES 索引、Neo4j 图）
 * - 索引构建阶段可以根据解析结果优化索引策略
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseRouteMessage {

    /**
     * 文档 ID
     * 需要解析的文档的唯一标识。
     * 消费者通过此 ID 从数据库读取文档的完整信息（文件路径、元数据等）。
     */
    private Long documentId;

    /**
     * 任务 ID
     * 解析任务的唯一标识，用于：
     * - 追踪任务状态（进行中、成功、失败）
     * - 记录任务日志和耗时
     * - 关联解析结果与任务
     */
    private Long taskId;
}
