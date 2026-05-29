package ai.knowhub.chat.service;

import ai.knowhub.chat.model.ChannelExecutionVo;
import ai.knowhub.chat.model.RetrievalResultVo;

import java.util.List;

/**
 * 【检索观察存储接口】
 *
 * 定义了 RAG（Retrieval-Augmented Generation）检索过程中，
 * 对检索结果和通道执行详情的持久化操作接口。
 *
 * 作用：在 RAG 检索完成后，系统需要记录"检索到了哪些文档切片"以及
 * "每个检索通道（关键词检索、向量检索等）的执行情况"，以便前端展示
 * 调试信息和运维人员排查问题。
 *
 * 设计模式：接口隔离原则（ISP）。将检索观察的存储操作抽象为独立接口，
 * 与会话归档（ConversationArchiveStore）分离，职责单一。
 *
 * 实现类：MybatisRetrievalObserveStore（基于 MyBatis-Plus 的 MySQL 实现）。
 *
 * 两个核心实体：
 * - RetrievalResultVo：单条检索结果（文档切片），包含排名、分数、是否被选中等信息。
 * - ChannelExecutionVo：单个检索通道的执行摘要，包含召回数、耗时、错误信息等。
 */
public interface RetrievalObserveStore {

    /**
     * 批量保存检索结果快照。
     *
     * @param conversationId 会话 ID
     * @param exchangeId     本轮问答回合 ID
     * @param results        检索到的文档切片列表，每个元素包含排名、分数、文档名等
     */
    void batchSaveResults(String conversationId, long exchangeId, List<RetrievalResultVo> results);

    /**
     * 批量保存通道执行详情。
     *
     * @param conversationId 会话 ID
     * @param exchangeId     本轮问答回合 ID
     * @param executions     各检索通道的执行摘要列表
     */
    void batchSaveChannelExecutions(String conversationId, long exchangeId, List<ChannelExecutionVo> executions);

    /**
     * 查询指定问答回合的所有检索结果。
     *
     * @param conversationId 会话 ID
     * @param exchangeId     本轮问答回合 ID
     * @return 检索结果列表，按子问题索引和最终排名排序
     */
    List<RetrievalResultVo> listResults(String conversationId, long exchangeId);

    /**
     * 查询指定问答回合的所有通道执行详情。
     *
     * @param conversationId 会话 ID
     * @param exchangeId     本轮问答回合 ID
     * @return 通道执行详情列表，按子问题索引和开始时间排序
     */
    List<ChannelExecutionVo> listChannelExecutions(String conversationId, long exchangeId);

    /**
     * 删除指定会话的所有检索观察数据。
     * 用于会话重置时清理历史数据。
     *
     * @param conversationId 会话 ID
     */
    void deleteByConversation(String conversationId);
}
