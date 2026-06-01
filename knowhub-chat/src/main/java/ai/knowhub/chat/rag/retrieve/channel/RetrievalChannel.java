package ai.knowhub.chat.rag.retrieve.channel;

import ai.knowhub.chat.rag.model.ConversationExecutionPlan;

/**
 * 检索通道接口 —— 定义"如何从知识库中检索文档"的统一契约。
 *
 * 设计模式：策略模式（Strategy Pattern）
 * 本接口是检索通道的策略抽象。系统中存在多种检索方式（向量检索、关键词检索等），
 * 每种方式实现本接口，提供不同的检索逻辑。
 *
 * 多通道检索架构
 * RAG 流水线采用"多通道并行检索"架构：同一个子问题会同时发给多个检索通道，
 * 每个通道独立返回结果，最后通过 RRF（Reciprocal Rank Fusion）算法融合。
 *
 * 这样做的好处是：
 * 
 *   <b>向量检索</b>擅长语义匹配（"价格"能匹配到"售价"、"费用"）
 *   <b>关键词检索</b>擅长精确匹配（"5.3.2"能精确命中编号）
 *   两者互补，融合后的结果比单独使用任何一个都更好
 * 各实现类
 * 
 *   VectorRetrievalChannel：向量检索通道，基于 embedding 语义相似度
 *   KeywordRetrievalChannel：关键词检索通道，基于全文索引精确匹配
 * @see RetrievalChannelResult 检索通道的返回结果
 * @see VectorRetrievalChannel 向量检索实现
 * @see KeywordRetrievalChannel 关键词检索实现
 */
public interface RetrievalChannel {

    /**
     * 返回本通道的名称。
     *
     * 用于日志、追踪和统计。例如 "vector"、"keyword"。
     * 在 SubQuestionChannelTrace#channelName 中使用。
     *
     * @return 通道名称字符串
     */
    String channelName();

    /**
     * 判断本通道是否支持当前执行计划。
     *
     * 有些通道可能不适合某些场景。例如：
     * 
     *   关键词通道在 ChatRagProperties#isKeywordChannelEnabled() 为 false 时不可用
     *   向量通道在没有指定文档 ID 时可能不适用
     * @param plan 当前的执行计划
     * @return true 表示本通道可以用于当前检索，false 表示跳过
     */
    boolean supports(ConversationExecutionPlan plan);

    /**
     * 执行检索，返回匹配的文档列表。
     *
     * 每个实现类在这里定义自己的检索逻辑：
     * 
     *   向量通道：把子问题转成 embedding，找语义最相似的文档
     *   关键词通道：用子问题的关键词做全文索引匹配
     * @param subQuestion 子问题文本，用于检索
     * @param plan        当前的执行计划，包含文档范围、Top-K 等参数
     * @return 检索结果，包含通道名称和匹配的文档列表
     */
    RetrievalChannelResult retrieve(String subQuestion, ConversationExecutionPlan plan);
}
