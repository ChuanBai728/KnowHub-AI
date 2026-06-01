package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.chat.model.SearchReference;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索上下文 —— 汇总所有子问题的检索结果，是检索阶段的最终输出。
 *
 * 在 RAG 流水线中的角色
 * 在 RAG 检索阶段，每个子问题会分别执行向量检索和关键词检索，
 * 然后经过 RRF 融合、父块提升、rerank 等处理。本类汇总所有子问题的检索结果，
 * 作为"检索 → 组装 Prompt → 生成答案"的衔接数据。
 *
 * 数据结构
 * 
 *   #retrievalQuestion：最终用于检索的问题文本
 *   #subQuestionEvidenceList：每个子问题的证据列表（包含文档、引用、通道追踪等）
 *   #retrievalNotes：检索过程中的说明信息（用于前端展示思考步骤）
 *   #usedChannels：使用了哪些检索通道（向量、关键词等）
 * @see SubQuestionEvidence 单个子问题的证据
 * @see ai.knowhub.chat.rag.service.RagRetrievalEngine 检索引擎
 * @see ai.knowhub.chat.rag.executor.RagChatExecutor 使用本类的执行器
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagRetrievalContext {

    /**
     * 最终用于检索的问题文本。
     *
     * 可能是用户原始问题，也可能是经过查询改写后的文本。
     * 用于日志和调试追踪。
     */
    private String retrievalQuestion;

    /**
     * 所有子问题的证据列表。
     *
     * 每个元素对应一个子问题的检索结果，包含该子问题检索到的文档列表、
     * 引用列表、各通道的追踪信息等。
     *
     * @see SubQuestionEvidence
     */
    private List<SubQuestionEvidence> subQuestionEvidenceList = new ArrayList<>();

    /**
     * 检索过程说明列表。
     *
     * 记录检索过程中的关键步骤和决策，例如：
     * 
     *   "查询改写：原始问题 '它的价格' → 改写为 '产品A的价格是多少'"
     *   "向量检索返回 8 个文档，关键词检索返回 5 个文档"
     *   "RRF 融合后保留 10 个候选"
     * 
     * 这些说明会作为"思考步骤"推送给前端，让用户看到系统的推理过程。
     */
    private List<String> retrievalNotes = new ArrayList<>();

    /**
     * 使用的检索通道名称列表。
     *
     * 记录本次检索实际使用了哪些通道，例如 ["vector", "keyword"]。
     * 用于调试和统计。
     *
     * @see ai.knowhub.chat.rag.retrieve.channel.RetrievalChannel 检索通道接口
     */
    private List<String> usedChannels = new ArrayList<>();

    /**
     * 判断检索结果是否为空（没有任何子问题找到证据）。
     *
     * @return true 表示所有子问题都没有检索到任何引用文档
     */
    public boolean isEmpty() {
        return subQuestionEvidenceList == null
            || subQuestionEvidenceList.stream().allMatch(item -> item.getReferences() == null || item.getReferences().isEmpty());
    }

    /**
     * 将所有子问题的引用"拍平"成一个列表。
     *
     * 每个子问题可能有多个引用，本方法把它们合并成一个扁平列表，
     * 方便后续统一处理（如记录到任务上下文中、展示给前端等）。
     *
     * @return 所有子问题的引用合并列表，如果没有引用则返回空列表
     */
    public List<SearchReference> flattenReferences() {
        if (subQuestionEvidenceList == null || subQuestionEvidenceList.isEmpty()) {
            return List.of();
        }
        List<SearchReference> references = new ArrayList<>();
        for (SubQuestionEvidence item : subQuestionEvidenceList) {
            if (item.getReferences() == null || item.getReferences().isEmpty()) {
                continue;
            }
            references.addAll(item.getReferences());
        }
        return references;
    }
}
