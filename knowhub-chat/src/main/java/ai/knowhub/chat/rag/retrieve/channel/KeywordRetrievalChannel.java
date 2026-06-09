package ai.knowhub.chat.rag.retrieve.channel;

import cn.hutool.core.collection.CollectionUtil;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.service.DocumentRetrieveDtoFactory;
import ai.knowhub.document.service.DocumentKnowledgeService;
import ai.knowhub.enums.RetrievalChannelEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 关键词检索通道 —— 基于全文索引精确匹配的检索方式。
 *
 * 工作原理
 * 关键词检索的核心思想是"精确匹配"：
 * 
 *   <b>离线索引</b>：知识库中的每个文档片段都被分词后建立倒排索引（通常使用 Elasticsearch）
 *   <b>在线检索</b>：用户的问题也会被分词，然后在倒排索引中查找包含这些关键词的文档
 *   <b>返回结果</b>：按匹配分数排序返回 Top-K 个文档
 * 优势与局限
 * 
 *   <b>优势</b>：对精确编号（如"5.3.2"）、专有名词、配置项名称的匹配非常准确
 *   <b>局限</b>：无法理解语义，"价格"匹配不到"售价"、"费用"等同义词
 * 使用场景
 * 关键词检索特别适合命中以下类型的信息：
 * 
 *   配置项名称（如"maxRetryCount"、"timeout"）
 *   章节标题（如"5.3 操作步骤"）
 *   编号项（如"第 3 步"、"要求 5.3.2"）
 *   专有术语（如"ISO 9001"、"GB/T 19001"）
 * 配置参数
 * 
 *   ChatRagProperties#isKeywordChannelEnabled()：通道开关（默认开启）
 *   ChatRagProperties#getKeywordTopK()：返回的文档数量（默认 8）
 *   ChatRagProperties#getKeywordRelativeScoreFloor()：相对得分下限（默认 0.35）
 * @see RetrievalChannel 检索通道接口
 * @see VectorRetrievalChannel 向量检索通道（互补）
 * @see DocumentKnowledgeService#keywordSearch 实际的关键词搜索方法
 */
@Component
public class KeywordRetrievalChannel implements RetrievalChannel {

    /**
     * 文档知识服务，提供关键词搜索等底层能力。
     */
    private final DocumentKnowledgeService documentKnowledgeService;

    /**
     * RAG 配置属性，包含 Top-K、通道开关等参数。
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
    public KeywordRetrievalChannel(DocumentKnowledgeService documentKnowledgeService,
                                   ChatRagProperties properties,
                                   DocumentRetrieveDtoFactory documentRetrieveDtoFactory) {
        this.documentKnowledgeService = documentKnowledgeService;
        this.properties = properties;
        this.documentRetrieveDtoFactory = documentRetrieveDtoFactory;
    }

    /**
     * 返回通道名称："keyword"。
     *
     * @return 通道名称字符串
     */
    @Override
    public String channelName() {
        return RetrievalChannelEnum.KEYWORD.getName();
    }

    /**
     * 判断是否支持当前执行计划。
     *
     * 关键词检索需要同时满足两个条件：
     * 
     *   通道开关已开启（ChatRagProperties#isKeywordChannelEnabled()）
     *   已指定文档 ID（在哪个文档范围内搜索）
     * @param plan 当前的执行计划
     * @return true 表示可以执行关键词检索
     */
    @Override
    public boolean supports(ConversationExecutionPlan plan) {
        return properties.isKeywordChannelEnabled()
            && (plan.getSelectedDocumentId() != null
            || (plan.getRetrievalDocumentIds() != null && !plan.getRetrievalDocumentIds().isEmpty()));
    }

    /**
     * 执行关键词检索。
     *
     * 检索流程：
     * 
     *   通过 DocumentRetrieveDtoFactory 构建检索请求
     *   调用 DocumentKnowledgeService#keywordSearch 执行全文索引匹配
     *   返回匹配的文档列表
     * @param subQuestion 子问题文本，会被分词后用于关键词匹配
     * @param plan        当前的执行计划，包含文档 ID、Top-K 等参数
     * @return 检索结果，包含通道名称和匹配的文档列表
     */
    @Override
    public RetrievalChannelResult retrieve(String subQuestion, ConversationExecutionPlan plan) {
        // 关键词检索适合命中配置项、章节名、编号等"必须一字不差"的信息。
        List<Document> documentList = documentKnowledgeService.keywordSearch(
            documentRetrieveDtoFactory.build(subQuestion, plan, properties.getKeywordTopK())
        );

        return new RetrievalChannelResult(
            channelName(), documentList
        );
    }
}
