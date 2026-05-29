package ai.knowhub.document.service;

import ai.knowhub.document.data.KnowHubDocumentChunk;

import java.util.List;

/**
 * 【文档向量网关接口】
 *
 * 作用：作为向量化存储的网关（Gateway），负责将文档分块写入向量数据库。
 * 是 RAG 流水线中"向量索引构建"的核心组件。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"向量存储"层
 *   - 采用 Gateway 设计模式，隔离向量数据库的具体实现
 *   - 上层服务通过此接口与向量数据库交互，无需关心底层实现细节
 *   - 底层通常使用 PostgreSQL + pgvector 或 Milvus 等向量数据库
 *
 * 核心概念：
 *   - 向量化（Vectorize）：将文本转换为高维向量表示的过程
 *   - 文档分块（Document Chunk）：文档被切分后的文本片段
 *   - 向量嵌入（Vector Embedding）：文本的数学向量表示，用于语义相似度计算
 *
 * 设计模式：Gateway 模式
 *   - 此接口是访问向量存储的唯一入口
 *   - 具体实现可以是 pgvector、Milvus、Pinecone 等不同向量数据库
 *   - 切换向量数据库只需更换实现类，无需修改上层代码
 */
public interface DocumentVectorGateway {

    /**
     * 将文档分块列表向量化并写入向量数据库
     *
     * 功能说明：
     *   - 接收文档分块列表
     *   - 为每个分块的文本内容生成向量嵌入（Embedding）
     *   - 将向量和元数据写入向量数据库
     *   - 向量嵌入通常通过调用 Embedding 模型 API 生成
     *
     * @param chunkList 文档分块列表，每个分块包含文本内容和元数据（文档ID、章节路径等）
     */
    void vectorize(List<KnowHubDocumentChunk> chunkList);

    /**
     * 删除指定文档的所有向量数据
     *
     * 功能说明：
     *   - 根据文档ID删除向量数据库中该文档的所有向量记录
     *   - 通常在文档删除或重建索引时调用
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);
}
