package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 查询改写结果 —— 记录查询改写阶段的输出。
 *
 * 在 RAG 流水线中的角色
 * 查询改写是 RAG 流水线的第一步：把用户口语化、省略上下文的问题改写成更适合检索的形式。
 *
 * 例如：
 * 
 *   原始问题："它的价格呢？" → 改写后："产品A的价格是多少？"
 *   原始问题："怎么用？" → 改写后："产品A的使用方法是什么？"
 *   原始问题："比较一下" → 拆分为："产品A的优缺点" 和 "产品B的优缺点"
 * 本类封装了改写阶段的输出，包括改写后的问题、拆分的子问题和模型的原始输出。
 *
 * @see ChatRagProperties#rewriteEnabled 改写开关
 * @see ai.knowhub.chat.rag.service.RagRetrievalEngine 使用本类的检索引擎
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagRewriteResult {

    /**
     * 改写后的主问题文本。
     *
     * 经过大模型改写后的问题，补充了省略的上下文信息，
     * 更适合用于向量检索或关键词检索。
     */
    private String rewrittenQuestion;

    /**
     * 拆分的子问题列表。
     *
     * 复杂问题会被拆成多个子问题分别检索。
     * 例如"比较 A 和 B 的优缺点"拆成 ["A 的优缺点", "B 的优缺点"]。
     *
     * 如果问题不需要拆分，此列表为空或只包含改写后的主问题。
     */
    private List<String> subQuestions;

    /**
     * 大模型的原始输出文本。
     *
     * 改写模型返回的原始 JSON 或文本，未经解析。
     * 用于调试和日志，方便排查改写质量。
     */
    private String rawModelOutput;

    /**
     * 简化构造函数 —— 只提供改写后的问题和子问题，不记录原始输出。
     *
     * @param rewrittenQuestion 改写后的主问题
     * @param subQuestions      拆分的子问题列表
     */
    public RagRewriteResult(String rewrittenQuestion, List<String> subQuestions) {
        this.rewrittenQuestion = rewrittenQuestion;
        this.subQuestions = subQuestions;
    }
}
