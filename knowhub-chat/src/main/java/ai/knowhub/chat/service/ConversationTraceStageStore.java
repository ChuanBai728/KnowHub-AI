package ai.knowhub.chat.service;

import ai.knowhub.chat.model.trace.ConversationTraceStageCode;
import ai.knowhub.chat.model.trace.ConversationTraceStageState;
import ai.knowhub.chat.model.trace.ConversationTraceStageVo;

import java.util.List;

/**
 * 【会话追踪阶段存储接口】
 *
 * 定义了会话执行过程中"追踪阶段"（Trace Stage）的持久化操作。
 *
 * 背景：一次 AI 会话的执行会经历多个阶段，例如：
 *   - QUERY_REWRITE（查询改写）
 *   - RETRIEVAL（知识检索）
 *   - RERANKING（重排序）
 *   - ANSWER_GENERATION（答案生成）
 *   - RECOMMENDATION（推荐追问生成）
 *   - FINALIZE（收尾）
 *
 * 每个阶段的开始时间、结束时间、耗时、执行状态、错误信息等都会被记录下来，
 * 形成一条完整的"执行轨迹"，方便前端可视化展示和后端性能分析。
 *
 * 设计模式：接口隔离原则（ISP）。追踪阶段的存储操作独立于会话归档和检索观察，
 * 职责清晰，便于单独扩展。
 *
 * 实现类：MybatisConversationTraceStageStore（基于 MyBatis-Plus 的 MySQL 实现）。
 */
public interface ConversationTraceStageStore {

    /**
     * 开始一个新的追踪阶段。
     *
     * @param conversationId 会话 ID
     * @param exchangeId     本轮问答回合 ID
     * @param traceId        本次追踪的唯一标识（UUID）
     * @param stageCode      阶段代码枚举，定义了阶段的名称、标签和排序
     * @param stageLevel     阶段层级（1=顶层阶段，2=子阶段等）
     * @param parentStageId  父阶段 ID（用于子阶段关联父阶段，顶层为 null）
     * @param executionMode  执行模式名称（如 RAG、REACT_AGENT 等）
     * @param summaryText    阶段摘要描述（如"正在检索知识库"）
     * @param snapshot       阶段快照数据（可以是任意对象，序列化为 JSON 存储）
     * @return 新创建的阶段 ID（由 UID 生成器生成的唯一长整数）
     */
    long startStage(String conversationId,
                    long exchangeId,
                    String traceId,
                    ConversationTraceStageCode stageCode,
                    int stageLevel,
                    Long parentStageId,
                    String executionMode,
                    String summaryText,
                    Object snapshot);

    /**
     * 结束一个追踪阶段（成功或失败）。
     *
     * @param stageId     阶段 ID（由 startStage 返回）
     * @param stageState  阶段最终状态：COMPLETED（成功）或 FAILED（失败）
     * @param summaryText 结束时的摘要描述
     * @param errorMessage 错误信息（成功时为空字符串）
     * @param snapshot    结束时的快照数据
     * @param durationMs  阶段耗时（毫秒）
     */
    void finishStage(long stageId,
                     ConversationTraceStageState stageState,
                     String summaryText,
                     String errorMessage,
                     Object snapshot,
                     long durationMs);

    /**
     * 查询指定问答回合的所有追踪阶段。
     *
     * @param conversationId 会话 ID
     * @param exchangeId     本轮问答回合 ID
     * @return 阶段展示列表，按阶段排序和开始时间升序排列
     */
    List<ConversationTraceStageVo> listStageViews(String conversationId, long exchangeId);

    /**
     * 删除指定会话的所有追踪阶段数据。
     * 用于会话重置时清理数据。
     *
     * @param conversationId 会话 ID
     */
    void deleteStages(String conversationId);
}
