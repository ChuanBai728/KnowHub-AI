package ai.knowhub.document.service.keyword;

import ai.knowhub.document.data.KnowHubDocumentChunk;
import ai.knowhub.document.model.DocumentRetrieveDto;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 【文档关键词搜索网关接口】
 *
 * 作用：定义关键词检索的统一接口，是关键词检索通道的抽象层。
 * 与向量检索互补，关键词检索擅长精确匹配和专业术语的命中。
 *
 * 架构角色：
 *   - 属于 RAG 流水线的"关键词检索"通道
 *   - 采用 Gateway 设计模式，隔离关键词搜索引擎的具体实现
 *   - 上层的 DocumentKnowledgeService 通过此接口执行关键词检索
 *   - 底层实现可以是 Elasticsearch、Solr、Lucene 等搜索引擎
 *
 * 核心概念：
 *   - 关键词检索（Keyword Search）：基于全文索引的精确匹配检索
 *   - 文档分块（Document Chunk）：被索引的文本片段
 *   - 检索请求（Retrieve Request）：封装查询文本、过滤条件、返回数量等参数
 *
 * 设计模式：Gateway 模式
 *   - 此接口是访问关键词搜索引擎的唯一入口
 *   - 不同的搜索引擎只需实现此接口即可替换
 */
public interface DocumentKeywordSearchGateway {

    /**
     * 将文档分块索引到关键词搜索引擎
     *
     * 功能说明：
     *   - 接收文档分块列表
     *   - 为每个分块建立全文索引
     *   - 索引包含文本内容和元数据（文档名、章节路径等）
     *   - 索引完成后即可通过 search 方法进行检索
     *
     * @param chunkList 文档分块列表
     */
    void indexChunks(List<KnowHubDocumentChunk> chunkList);

    /**
     * 执行关键词检索
     *
     * 功能说明：
     *   - 根据检索请求中的查询文本进行全文搜索
     *   - 支持多字段加权匹配、短语匹配等高级查询
     *   - 返回匹配的文档片段列表，按相关度排序
     *
     * @param request 检索请求，包含查询文本、过滤条件、返回数量等
     * @return 匹配的文档片段列表
     */
    List<Document> search(DocumentRetrieveDto request);

    /**
     * 删除指定文档的关键词索引
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);
}
