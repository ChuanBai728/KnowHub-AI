package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索问题计划 —— 记录用于检索的问题和子问题列表。
 *
 * 在 RAG 流水线中的角色
 * 在路由规划阶段，系统会决定用什么问题去检索知识库。
 * 本类封装了检索问题计划，通常嵌入到 DocumentNavigationDecision 中使用。
 *
 * 与 RagRewriteResult 的区别：
 * 
 *   RagRewriteResult：查询改写阶段的输出，包含改写后的问题和原始模型输出
 *   RetrievalQuestionPlan：路由规划阶段的输出，只包含最终的检索问题和子问题，更精简
 * @see DocumentNavigationDecision#retrievalPlan 在导航决策中引用
 * @see ConversationExecutionPlan#retrievalQuestion 执行计划中的检索问题
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalQuestionPlan {

    /**
     * 主检索问题。
     *
     * 用于向量检索和关键词检索的主要问题文本。
     * 通常是经过改写或规划后的问题，比用户原始问题更适合检索。
     */
    private String retrievalQuestion;

    /**
     * 子问题列表。
     *
     * 复杂问题拆分后的子问题列表，每个子问题会分别执行检索，
     * 最后合并结果。如果问题不需要拆分，此列表为空。
     */
    private List<String> subQuestions;
}
