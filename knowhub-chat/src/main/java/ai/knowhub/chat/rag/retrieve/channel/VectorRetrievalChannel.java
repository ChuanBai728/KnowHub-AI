package ai.knowhub.chat.rag.retrieve.channel;

import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.service.DocumentRetrieveDtoFactory;
import ai.knowhub.document.service.DocumentKnowledgeService;
import ai.knowhub.enums.RetrievalChannelEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量检索通道 —— 基于 embedding 语义相似度的检索方式。
 *
 * 工作原理
 * 向量检索的核心思想是"语义匹配"：
 * 
 *   <b>离线索引</b>：知识库中的每个文档片段（chunk）都被预先转换成一个高维向量（embedding），
 *       存储在向量数据库（如 PostgreSQL + pgvector）中
 *   <b>在线检索</b>：用户的问题也会被转换成向量，然后在向量数据库中找到与之"距离最近"的文档片段
 *   <b>返回结果</b>：按相似度排序返回 Top-K 个文档
 * 优势与局限
 * 
 *   <b>优势</b>：能理解语义，"价格"能匹配到"售价"、"费用"、"多少钱"等同义词
 *   <b>局限</b>：对精确编号（如"5.3.2"）或专有名词的匹配不如关键词检索
 * 配置参数
 * 
 *   ChatRagProperties#getVectorTopK()：返回的文档数量（默认 8）
 *   ChatRagProperties#getMinVectorSimilarity()：最低相似度阈值（默认 0.45）
 * @see RetrievalChannel 检索通道接口
 * @see KeywordRetrievalChannel 关键词检索通道（互补）
 * @see DocumentKnowledgeService#vectorSearch 实际的向量搜索方法
 */
@Component
public class VectorRetrievalChannel implements RetrievalChannel {

    /**
     * 文档知识服务，提供向量搜索和关键词搜索等底层能力。
     */
    private final DocumentKnowledgeService documentKnowledgeService;

    /**
     * RAG 配置属性，包含 Top-K、相似度阈值等参数。
     */
    private final ChatRagProperties properties;

    /**
     * 文档检索请求工厂，负责构建检索请求对象。
     */
    private final DocumentRetrieveDtoFactory documentRetrieveDtoFactory;

    /**
     * 构造函数，注入依赖。
     *
     * @param documentKnowledgeService      文档知识服务
     * @param properties                    RAG 配置属性
     * @param documentRetrieveDtoFactory 检索请求工厂
     */
    public VectorRetrievalChannel(DocumentKnowledgeService documentKnowledgeService,
                                  ChatRagProperties properties,
                                  DocumentRetrieveDtoFactory documentRetrieveDtoFactory) {
        this.documentKnowledgeService = documentKnowledgeService;
        this.properties = properties;
        this.documentRetrieveDtoFactory = documentRetrieveDtoFactory;
    }

    /**
     * 返回通道名称："vector"。
     *
     * @return 通道名称字符串
     */
    @Override
    public String channelName() {
        return RetrievalChannelEnum.VECTOR.getName();
    }

    /**
     * 判断是否支持当前执行计划。
     *
     * 向量检索需要指定文档 ID（在哪个文档范围内搜索），
     * 如果执行计划中没有指定文档，则不支持。
     *
     * @param plan 当前的执行计划
     * @return true 表示已指定文档 ID，可以执行向量检索
     */
    @Override
    public boolean supports(ConversationExecutionPlan plan) {
        return plan.getSelectedDocumentId() != null;
    }

    /**
     * 执行向量检索。
     *
     * 检索流程：
     * 
     *   通过 DocumentRetrieveDtoFactory 构建检索请求（包含文档范围、Top-K 等参数）
     *   调用 DocumentKnowledgeService#vectorSearch 执行向量相似度搜索
     *   返回匹配的文档列表
     * @param subQuestion 子问题文本，会被转换成 embedding 向量
     * @param plan        当前的执行计划，包含文档 ID、Top-K 等参数
     * @return 检索结果，包含通道名称和匹配的文档列表
     */
    @Override
    public RetrievalChannelResult retrieve(String subQuestion, ConversationExecutionPlan plan) {
        // 向量检索会先把问题转成 embedding，再找语义最接近的文档 chunk。
        List<Document> documentList = documentKnowledgeService.vectorSearch(
            documentRetrieveDtoFactory.build(subQuestion, plan, properties.getVectorTopK())
        );
        return new RetrievalChannelResult(
            channelName(), documentList
        );
    }
}
