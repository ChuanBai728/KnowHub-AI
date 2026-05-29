package ai.knowhub.chat.service;

import ai.knowhub.chat.model.ConversationExchangeVo;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.model.debug.ChatDebugTrace;
import ai.knowhub.enums.ChatQueryMode;
import ai.knowhub.enums.ChatTurnStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 【会话归档存储接口】
 *
 * 定义了会话（Conversation）和问答回合（Exchange）的持久化操作接口。
 *
 * 核心概念：
 * - 会话（Session/Dialogue）：一个完整的对话流程，由 conversationId 唯一标识。
 *   一个会话包含多轮问答，以及查询模式、选中文档等元数据。
 * - 问答回合（Exchange）：一次"用户提问 -> AI 回答"的完整过程。
 *   每个回合有独立的状态（运行中、完成、失败、停止）和丰富的元数据。
 *
 * 数据库对应关系：
 * - knowhub_chat_dialogue 表 -> 会话记录
 * - knowhub_chat_exchange 表 -> 问答回合记录
 *
 * 设计模式：仓储模式（Repository Pattern）。
 * 将数据访问逻辑封装在接口后面，业务层不需要关心具体的数据库操作细节。
 *
 * 实现类：MybatisConversationArchiveStore（基于 MyBatis-Plus 的 MySQL 实现）。
 *
 * 内部记录类型：
 * - ConversationArchiveRecord: 会话归档记录，包含会话元数据和所有回合
 * - ConversationRemovalResult: 删除结果，记录被删除的会话数和回合数
 * - ConversationArchivePage: 分页查询结果
 */
public interface ConversationArchiveStore {

    /**
     * 开始一个新的问答回合。
     *
     * 执行逻辑：
     * 1. 如果会话不存在则创建，如果已存在则更新状态为"运行中"
     * 2. 创建一个新的 exchange 记录，状态为 RUNNING
     *
     * @param conversationId      会话 ID
     * @param question            用户提问内容
     * @param chatMode            查询模式（开放式/自动/文档问答）
     * @param selectedDocumentId  选中的文档 ID（文档问答模式下有值）
     * @param selectedDocumentName 选中的文档名称
     * @return 创建的问答回合返回值对象
     */
    ConversationExchangeVo startExchange(String conversationId,
                                           String question,
                                           ChatQueryMode chatMode,
                                           Long selectedDocumentId,
                                           String selectedDocumentName);

    /**
     * 刷新会话的作用域信息。
     *
     * 当执行计划编排器（ChatPreparationOrchestrator）发现需要切换查询模式或文档时，
     * 调用此方法更新会话的元数据。
     *
     * @param conversationId      会话 ID
     * @param chatMode            新的查询模式
     * @param selectedDocumentId  新的选中文档 ID
     * @param selectedDocumentName 新的选中文档名称
     */
    void refreshSessionScope(String conversationId,
                             ChatQueryMode chatMode,
                             Long selectedDocumentId,
                             String selectedDocumentName);

    /**
     * 完成一个问答回合。
     *
     * 无论成功、失败还是停止，都会调用此方法将回合的最终状态持久化。
     *
     * @param conversationId      会话 ID
     * @param exchangeId          回合 ID
     * @param answer              AI 生成的回答内容
     * @param thinkingSteps       思考步骤列表（Agent 的推理过程）
     * @param references          引用来源列表（检索到的文档切片）
     * @param recommendations     推荐追问列表
     * @param usedTools           使用过的工具列表
     * @param debugTrace          调试追踪信息（包含执行模式、检索笔记等）
     * @param status              回合最终状态：COMPLETED / FAILED / STOPPED
     * @param errorMessage        错误信息（成功时为空）
     * @param firstResponseTimeMs 首次响应时间（毫秒），用于衡量响应速度
     * @param totalResponseTimeMs 总响应时间（毫秒）
     */
    void completeExchange(String conversationId,
                          long exchangeId,
                          String answer,
                          List<String> thinkingSteps,
                          List<SearchReference> references,
                          List<String> recommendations,
                          List<String> usedTools,
                          ChatDebugTrace debugTrace,
                          ChatTurnStatus status,
                          String errorMessage,
                          Long firstResponseTimeMs,
                          Long totalResponseTimeMs);

    /**
     * 获取指定会话的归档记录。
     *
     * @param conversationId 会话 ID
     * @return 会话归档记录的 Optional 包装
     */
    Optional<ConversationArchiveRecord> getSessionRecord(String conversationId);

    /**
     * 列出指定会话的所有问答回合（按时间升序）。
     *
     * @param conversationId 会话 ID
     * @return 问答回合展示列表
     */
    List<ConversationExchangeVo> listExchanges(String conversationId);

    /**
     * 列出指定会话中，指定回合 ID 之后的所有回合。
     * 用于增量获取新增的回合数据。
     *
     * @param conversationId  会话 ID
     * @param afterExchangeId 起始回合 ID（不包含）
     * @return 之后的问答回合展示列表
     */
    List<ConversationExchangeVo> listExchangesAfter(String conversationId, long afterExchangeId);

    /**
     * 列出指定会话最近的 N 个问答回合（按时间降序取最近，再反转为升序）。
     * 用于构建对话历史上下文。
     *
     * @param conversationId 会话 ID
     * @param limit          最大返回数量
     * @return 最近的问答回合展示列表（时间升序）
     */
    List<ConversationExchangeVo> listRecentExchanges(String conversationId, int limit);

    /**
     * 列出所有会话的归档记录。
     *
     * @return 所有会话归档记录列表
     */
    List<ConversationArchiveRecord> listSessionRecords();

    /**
     * 分页查询会话归档记录。
     *
     * @param pageNo          页码（从 1 开始）
     * @param pageSize        每页大小
     * @param keyword         搜索关键词（可选，模糊匹配会话 ID、文档名、问答内容）
     * @param chatMode        查询模式过滤（可选）
     * @param latestTurnStatus 最近回合状态过滤（可选）
     * @return 分页结果
     */
    ConversationArchivePage listSessionRecordPage(int pageNo,
                                                  int pageSize,
                                                  String keyword,
                                                  ChatQueryMode chatMode,
                                                  ChatTurnStatus latestTurnStatus);

    /**
     * 删除指定会话的所有数据（会话记录和所有回合）。
     *
     * @param conversationId 会话 ID
     * @return 删除结果，包含被删除的会话数和回合数
     */
    ConversationRemovalResult deleteSession(String conversationId);

    /**
     * 【会话归档记录】
     *
     * 封装一个会话的完整信息，包括元数据和所有问答回合。
     * 使用 Java record 语法定义，自动提供 getter、equals、hashCode、toString。
     *
     * @param conversationId       会话 ID
     * @param running              是否正在执行中
     * @param chatMode             查询模式
     * @param selectedDocumentId   选中的文档 ID
     * @param selectedDocumentName 选中的文档名称
     * @param createdAt            创建时间
     * @param updatedAt            最后更新时间
     * @param exchanges            所有问答回合列表
     */
    record ConversationArchiveRecord(
        String conversationId,
        boolean running,
        ChatQueryMode chatMode,
        Long selectedDocumentId,
        String selectedDocumentName,
        Instant createdAt,
        Instant updatedAt,
        List<ConversationExchangeVo> exchanges
    ) {
    }

    /**
     * 【会话删除结果】
     *
     * @param removedDialogueCount 被删除的会话记录数
     * @param removedExchangeCount 被删除的问答回合数
     */
    record ConversationRemovalResult(
        int removedDialogueCount,
        int removedExchangeCount
    ) {
    }

    /**
     * 【会话归档分页结果】
     *
     * @param pageNo   当前页码
     * @param pageSize 每页大小
     * @param totalSize 总记录数
     * @param records  当前页的记录列表
     */
    record ConversationArchivePage(
        long pageNo,
        long pageSize,
        long totalSize,
        List<ConversationArchiveRecord> records
    ) {
    }
}
