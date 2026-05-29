package ai.knowhub.chat.service;

import ai.knowhub.chat.model.ConversationMemorySummaryVo;
import ai.knowhub.chat.model.memory.ConversationMemoryContext;

/**
 * 【会话记忆服务接口】
 *
 * 定义了会话记忆（Conversation Memory）的核心操作。
 * "会话记忆"是指系统在多轮对话中积累的上下文信息，包括：
 *   - 长期摘要：对历史对话的压缩总结（避免上下文窗口溢出）
 *   - 最近对话原文：保留最近几轮的完整问答内容
 *   - 检索提示：从历史对话中提取的关键词，用于 RAG 检索
 *
 * 作用：当用户与 AI Agent 进行多轮对话时，历史消息会越来越长，
 * 超过大模型的上下文窗口限制。会话记忆服务通过"摘要压缩"策略，
 * 将较早的对话压缩成结构化摘要，只保留最近几轮的原文，
 * 从而在有限的上下文窗口内承载更多历史信息。
 *
 * 设计模式：策略模式 + 接口抽象。
 * 实现类 PersistentConversationMemoryService 使用数据库持久化摘要，
 * 未来可以替换为 Redis 缓存或其他存储方案。
 *
 * 使用场景：
 * - ChatPreparationOrchestrator 在编排执行计划时调用 loadMemoryContext() 获取历史上下文
 * - BusinessChatService 在每轮回答完成后调用 refreshConversationSummaryAsync() 异步更新摘要
 * - 前端可以通过 getConversationSummary() 查看当前会话的摘要状态
 */
public interface ConversationMemoryService {

    /**
     * 加载指定会话的记忆上下文。
     *
     * 返回的 ConversationMemoryContext 包含：
     * - assembledHistory: 组装后的历史上下文（长期摘要 + 最近对话原文）
     * - longTermSummary: 长期摘要文本
     * - recentTranscript: 最近几轮对话原文
     * - answerRecentTranscript: 用于回答生成的最近对话上下文
     * - compressionApplied: 是否已经应用了摘要压缩
     *
     * @param conversationId 会话 ID
     * @return 会话记忆上下文对象
     */
    ConversationMemoryContext loadMemoryContext(String conversationId);

    /**
     * 加载指定会话的记忆上下文（带追踪记录器重载）。
     *
     * 与无参版本的区别：此方法接受一个 ConversationTraceRecorder，
     * 可以将记忆加载过程中的模型调用记录到追踪轨迹中，便于调试。
     * 默认实现直接调用无 traceRecorder 的版本。
     *
     * @param conversationId 会话 ID
     * @param traceRecorder  追踪记录器，用于记录模型调用耗时和 token 用量
     * @return 会话记忆上下文对象
     */
    default ConversationMemoryContext loadMemoryContext(String conversationId, ConversationTraceRecorder traceRecorder) {
        return loadMemoryContext(conversationId);
    }

    /**
     * 异步刷新会话摘要。
     *
     * 在每轮对话完成后调用，检查是否有足够的"溢出"轮次需要压缩，
     * 如果有则异步调用大模型生成新的摘要。
     * 使用 ConcurrentHashMap 做去重，避免同一会话并发刷新。
     *
     * @param conversationId 会话 ID
     */
    void refreshConversationSummaryAsync(String conversationId);

    /**
     * 获取当前会话的摘要展示（只读查询）。
     *
     * @param conversationId 会话 ID
     * @return 会话摘要展示，包含摘要文本、压缩次数、覆盖轮次等信息
     */
    ConversationMemorySummaryVo getConversationSummary(String conversationId);

    /**
     * 强制重建会话摘要。
     *
     * 删除现有摘要记录，重新从头压缩所有历史对话。
     * 用于管理员手动触发或摘要数据异常时的修复操作。
     *
     * @param conversationId 会话 ID
     * @return 重建后的会话摘要展示
     */
    ConversationMemorySummaryVo rebuildConversationSummary(String conversationId);

    /**
     * 删除指定会话的摘要记录。
     * 用于会话重置（resetConversation）时清理数据。
     *
     * @param conversationId 会话 ID
     */
    void deleteConversationSummary(String conversationId);
}
