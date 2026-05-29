package ai.knowhub.document.service;

import ai.knowhub.document.model.DocumentRetrieveDto;
import ai.knowhub.document.model.KnowledgeDocumentDescriptor;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 【文档知识检索服务接口】
 *
 * 作用：提供文档知识检索的核心能力，是 RAG（检索增强生成）流水线中的"检索引擎"层。
 * 负责根据用户的查询请求，从已索引的文档中检索出最相关的文本片段。
 *
 * 架构角色：
 *   - 属于 RAG 流水线的"多通道检索"模块
 *   - 支持两种检索通道：向量检索（语义相似度）和关键词检索（精确匹配）
 *   - 检索结果会传递给下游的重排序（Reranking）和提示词组装模块
 *
 * 核心概念：
 *   - 向量检索：将查询文本转换为向量，在向量空间中计算相似度，返回语义最相关的结果
 *   - 关键词检索：基于 Elasticsearch 的全文检索，擅长精确匹配和关键词命中
 *   - 多通道融合：将两种检索结果合并，提供更全面的召回能力
 */
public interface DocumentKnowledgeService {

    /**
     * 列出所有可检索的文档描述信息
     *
     * 功能说明：
     *   - 返回系统中已建立索引、可供检索的文档列表
     *   - 每个描述包含文档ID、名称、知识域等元数据
     *   - 用于前端展示可检索文档范围，或在检索前过滤目标文档
     *
     * @return 可检索文档的描述信息列表
     */
    List<KnowledgeDocumentDescriptor> listRetrievableDocuments();

    /**
     * 向量检索：基于语义相似度查找相关文档
     *
     * 功能说明：
     *   - 将查询文本转换为向量嵌入（Embedding）
     *   - 在向量数据库中执行近似最近邻搜索（ANN）
     *   - 返回语义最相似的文档片段列表
     *   - 适合处理同义词、近义表达等语义层面的匹配
     *
     * @param request 检索请求，包含查询文本、目标文档范围、返回数量等参数
     * @return 检索到的文档片段列表，按相关度降序排列
     */
    List<Document> vectorSearch(DocumentRetrieveDto request);

    /**
     * 关键词检索：基于全文索引查找相关文档
     *
     * 功能说明：
     *   - 利用 Elasticsearch 的全文检索能力
     *   - 支持短语匹配、多字段加权匹配等高级查询
     *   - 擅长精确的关键词命中和专业术语匹配
     *   - 与向量检索互补，提供更全面的召回结果
     *
     * @param request 检索请求，包含查询文本、过滤条件、返回数量等参数
     * @return 检索到的文档片段列表，按相关度降序排列
     */
    List<Document> keywordSearch(DocumentRetrieveDto request);

    /**
     * 将子文档块提升到父文档块
     *
     * 功能说明：
     *   - 当检索命中的文本块是较细粒度的子块时，可能缺少完整上下文
     *   - 此方法将子块替换为其所属的父块，提供更完整的上下文信息
     *   - 受 maxChars 参数限制，避免返回过大的文本块
     *   - 这是"父文档检索策略（Parent Document Retriever）"的核心思想
     *
     * @param childDocuments 检索命中的子文档块列表
     * @param maxChars       父文档块的最大字符数限制
     * @return 提升后的父文档块列表，保持原有的排序顺序
     */
    List<Document> elevateToParentBlocks(List<Document> childDocuments, int maxChars);
}
