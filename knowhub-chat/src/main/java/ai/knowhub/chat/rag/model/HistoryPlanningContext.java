package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史规划上下文 —— 从历史对话中提取的规划信息，帮助当前轮次更好地理解和回答问题。
 *
 * 在 RAG 流水线中的角色
 * 在多轮对话中，之前的对话历史不仅包含"说了什么"，还隐含着"用户在关注什么"、
 * "已经确认了哪些事实"、"还有哪些问题没解决"等规划信息。
 *
 * 本类把这些规划信息显式提取出来，在当前轮次的检索和生成中使用。
 * 例如，如果历史对话中用户一直在关注"产品 A 的安全性"，那么当前轮次的检索
 * 应该优先考虑与"产品 A"和"安全性"相关的文档。
 *
 * 使用场景
 * 
 *   <b>查询改写</b>：根据 #conversationGoal 和 #retrievalHints 改写用户问题
 *   <b>检索范围</b>：根据 #queryContextHints 缩小检索范围
 *   <b>答案生成</b>：根据 #stableFacts 避免重复回答已知信息
 * @see ConversationExecutionPlan#historyPlanningContext 在执行计划中引用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryPlanningContext {

    /**
     * 对话目标。
     *
     * 从历史对话中推断出的用户整体目标。
     * 例如"用户想了解产品 A 的完整使用方法"、"用户在对比产品 A 和产品 B"。
     * 帮助系统理解当前问题在整个对话中的位置。
     */
    private String conversationGoal;

    /**
     * 已确认的稳定事实列表。
     *
     * 在之前的对话中已经确认、不会再变的事实信息。
     * 例如"用户使用的是产品 A v2.0"、"用户所在行业是制造业"。
     * 在当前轮次的答案生成中可以引用这些事实，避免重复询问。
     */
    @Builder.Default
    private List<String> stableFacts = new ArrayList<>();

    /**
     * 待解答的问题列表。
     *
     * 在之前的对话中提出但尚未完全解答的问题。
     * 例如用户之前问了"产品 A 有哪些功能"但只得到了部分回答，
     * 这里记录剩余待解答的部分，帮助当前轮次补充。
     */
    @Builder.Default
    private List<String> pendingQuestions = new ArrayList<>();

    /**
     * 检索提示列表。
     *
     * 从历史对话中提取的检索关键词或提示信息。
     * 例如用户之前提到的"安全认证"、"性能指标"等关注点，
     * 可以作为当前检索的额外关键词。
     */
    @Builder.Default
    private List<String> retrievalHints = new ArrayList<>();

    /**
     * 查询上下文提示列表。
     *
     * 比 #retrievalHints 更宽泛的上下文信息，
     * 包括用户的行业背景、使用场景、偏好等。
     * 用于帮助检索引擎更好地理解问题的上下文。
     */
    @Builder.Default
    private List<String> queryContextHints = new ArrayList<>();
}
