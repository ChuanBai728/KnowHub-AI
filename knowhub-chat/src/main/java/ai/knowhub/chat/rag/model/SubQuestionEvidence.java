package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.chat.model.SearchReference;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 子问题证据 —— 记录单个子问题经过完整检索流程后的结果。
 *
 * 在 RAG 流水线中的角色
 * 在 RAG 检索阶段，复杂问题会被拆成多个子问题，每个子问题独立执行检索。
 * 本类封装了单个子问题的完整检索结果，包括：
 * 
 *   子问题的文本
 *   检索到的原始文档列表
 *   经过处理后的引用列表
 *   各通道的追踪信息
 *   各阶段的候选文档数量统计
 * 多个 SubQuestionEvidence 汇总到 RagRetrievalContext 中，
 * 作为"检索 → 组装 Prompt → 生成答案"的衔接数据。
 *
 * 检索流程与对应字段
 * 
 *   多通道检索（向量 + 关键词）→ #channelTraces 记录各通道统计
 *   RRF 融合 → #fusedCandidateCount 记录融合后候选数
 *   父块提升 → #parentCandidateCount 记录提升后候选数
 *   Rerank 重排序 → #rerankedCandidateCount 记录重排序后候选数
 *   最终选出的文档 → #documents 和 #references
 * @see RagRetrievalContext 检索上下文，汇总所有子问题的证据
 * @see SubQuestionChannelTrace 单个通道的追踪信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubQuestionEvidence {

    /**
     * 子问题的索引（从 0 开始）。
     *
     * 当问题被拆分成多个子问题时，每个子问题有一个唯一的索引。
     * 用于在日志和追踪中区分不同的子问题。
     */
    private int subQuestionIndex;

    /**
     * 子问题的文本内容。
     *
     * 经过查询改写和拆分后的子问题文本，用于检索。
     */
    private String subQuestion;

    /**
     * 检索到的原始文档列表。
     *
     * 经过 RRF 融合、父块提升、rerank 等处理后最终保留的文档。
     * 这些文档的文本内容会被放入 Prompt 作为证据。
     *
     * 类型为 org.springframework.ai.document.Document，是 Spring AI 的文档抽象，
     * 包含文本内容和元数据（如文档名、章节路径、相似度分数等）。
     */
    private List<Document> documents;

    /**
     * 文档引用列表。
     *
     * 从文档中提取的引用信息，包含引用 ID、文档名、章节路径、检索通道等。
     * 这些引用会展示给用户，让用户知道答案的来源。
     *
     * @see SearchReference
     */
    private List<SearchReference> references;

    /**
     * 各检索通道的追踪信息。
     *
     * 记录每个通道（向量、关键词等）的召回数和采纳数，
     * 用于调试和监控各通道的表现。
     *
     * @see SubQuestionChannelTrace
     */
    private List<SubQuestionChannelTrace> channelTraces;

    /**
     * RRF 融合后的候选文档数量。
     *
     * 向量和关键词两个通道的结果经过 RRF（Reciprocal Rank Fusion）融合后
     * 的候选文档总数。这个数字反映了融合前的"粗排"结果规模。
     */
    private Integer fusedCandidateCount;

    /**
     * 父块提升后的候选文档数量。
     *
     * 当检索命中的是子块（chunk）时，系统会尝试提升到其父块以提供更完整的上下文。
     * 提升后的候选数通常与融合后相同或略少（合并了相同父块的子块）。
     */
    private Integer parentCandidateCount;

    /**
     * Rerank 重排序后的候选文档数量。
     *
     * 经过交叉编码器模型重新打分排序后保留的文档数。
     * 这是最终送入 Prompt 的文档数。
     */
    private Integer rerankedCandidateCount;
}
