package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 证据满足度评估结果 —— 判断检索到的证据是否足够回答用户问题。
 *
 * 在 RAG 流水线中的角色
 * 在 RAG 检索完成后，系统需要评估检索到的证据是否"足够"回答用户问题。
 * 如果证据不足（例如检索到的文档与问题相关性很低），系统应该返回兜底话术
 * 而不是让大模型"编造"答案。
 *
 * 本类封装了评估结果，包括是否满足、评估备注和被采纳的证据列表。
 *
 * 泛型参数 T
 * 本类使用泛型 <T> 表示证据的类型，通常是 org.springframework.ai.document.Document
 * 或自定义的引用类型。这样同一个评估逻辑可以复用于不同类型的证据。
 *
 * @see RagRetrievalContext 检索上下文，包含所有子问题的证据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceSatisfactionResult<T> {

    /**
     * 证据是否满足回答需求。
     *
     * true 表示检索到的证据足够回答用户问题，可以进入 Prompt 组装和答案生成阶段。
     * false 表示证据不足，应该返回兜底话术（ConversationExecutionPlan#noEvidenceReply）。
     */
    private boolean satisfied;

    /**
     * 评估备注列表。
     *
     * 记录评估过程中的关键信息，例如：
     * 
     *   "所有子问题的向量相似度均低于阈值"
     *   "关键词检索未命中任何文档"
     *   "共 5 个引用，3 个满足相似度要求"
     * 
     * 用于调试和追踪。
     */
    @Builder.Default
    private List<String> notes = new ArrayList<>();

    /**
     * 被采纳的证据列表。
     *
     * 经过质量筛选后，被认为足够相关、可以用于生成答案的证据。
     * 这些证据会被送入 Prompt 组装阶段。
     */
    @Builder.Default
    private List<T> acceptedEvidence = new ArrayList<>();
}
