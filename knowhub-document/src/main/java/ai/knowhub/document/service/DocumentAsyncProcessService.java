package ai.knowhub.document.service;

/**
 * 【文档异步处理服务接口】
 *
 * 作用：定义文档异步处理的核心方法，负责文档解析和索引构建的异步任务调度。
 * 在文档处理流水线中，解析和索引构建是耗时操作，因此采用异步方式执行，避免阻塞用户请求。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"任务调度层"
 *   - 解析路由（Parse Route）：将原始文档解析为结构化的文本和节点
 *   - 索引构建（Index Build）：将解析后的内容建立向量索引和关键词索引，用于后续检索
 *
 * 设计模式：此接口遵循"门面模式（Facade Pattern）"的思路，
 *           将复杂的异步处理逻辑封装为简单的接口方法，供上层服务调用。
 *
 * 使用场景：
 *   - 用户上传文档后，系统异步触发解析和索引构建
 *   - 重新解析或重建索引时调用
 */
public interface DocumentAsyncProcessService {

    /**
     * 处理文档的解析路由任务
     *
     * 功能说明：
     *   - 根据文档类型选择合适的解析策略（如 PDF、Word、Markdown 等）
     *   - 将原始文件解析为结构化文本内容
     *   - 提取文档的层级结构（章节、段落等）
     *   - 解析结果会存储到数据库和对象存储中
     *
     * @param documentId 文档ID，标识需要解析的文档
     * @param taskId     任务ID，用于追踪本次解析任务的执行状态和日志
     */
    void handleParseRoute(Long documentId, Long taskId);

    /**
     * 处理文档的索引构建任务
     *
     * 功能说明：
     *   - 将已解析的文档内容进行分块（Chunking）
     *   - 为每个文本块生成向量嵌入（Vector Embedding）
     *   - 将向量写入向量数据库（如 PostgreSQL + pgvector）
     *   - 同时将关键词索引写入 Elasticsearch
     *   - 构建文档结构图（Graph），用于基于图的检索增强生成（Graph RAG）
     *
     * @param documentId 文档ID，标识需要构建索引的文档
     * @param taskId     任务ID，用于追踪本次索引构建任务的执行状态
     * @param planId     策略计划ID，关联到用户确认的分块策略方案，
     *                   决定如何对文档进行分块和索引
     */
    void handleIndexBuild(Long documentId, Long taskId, Long planId);
}
