package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG Prompt 组装结果 —— 记录 Prompt 组装阶段的输出和预算分配详情。
 *
 * 在 RAG 流水线中的角色
 * 在 RAG 流水线的"Prompt 组装"阶段，ai.knowhub.chat.rag.service.RagPromptAssemblyService
 * 会把检索到的证据、历史上下文、系统提示词等信息组装成最终的 system prompt 和 user prompt。
 *
 * 由于大模型的上下文窗口有限，不可能把所有证据都塞进去。组装服务会按照预算分配策略，
 * 优先保留最相关的证据，超出预算的部分会被截断或省略。本类记录了组装的结果和预算使用详情。
 *
 * @see ai.knowhub.chat.rag.service.RagPromptAssemblyService Prompt 组装服务
 * @see ChatRagProperties#totalEvidenceMaxChars 总证据预算
 * @see ChatRagProperties#perSubQuestionEvidenceMaxChars 单子问题证据预算
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagPromptAssemblyResult {

    /**
     * 组装后的系统提示词（System Prompt）。
     *
     * 告诉大模型的角色设定、回答规则、证据使用方式等。
     * 例如"你是一个基于知识库回答问题的助手，只根据提供的证据回答，不要编造信息"。
     */
    private String systemPrompt;

    /**
     * 组装后的用户提示词（User Prompt）。
     *
     * 包含用户的实际问题、检索到的证据文本、历史上下文等。
     * 这是大模型实际"看到"的输入内容。
     */
    private String userPrompt;

    /**
     * 证据的总字符预算。
     *
     * 所有子问题的证据加起来不能超过此值。
     * 来自 ChatRagProperties#totalEvidenceMaxChars。
     */
    private int totalBudget;

    /**
     * 每个子问题的证据字符预算。
     *
     * 单个子问题分配到的证据文本最大字符数。
     * 来自 ChatRagProperties#perSubQuestionEvidenceMaxChars。
     */
    private int perSubQuestionBudget;

    /**
     * 实际渲染（纳入 Prompt）的引用数量。
     *
     * 在预算范围内成功放入 Prompt 的文档引用数。
     */
    private int renderedReferenceCount;

    /**
     * 被省略（未纳入 Prompt）的引用数量。
     *
     * 因为超出预算而被截断或省略的文档引用数。
     */
    private int omittedReferenceCount;

    /**
     * 已渲染引用的详情列表。
     *
     * 每个元素描述一个被纳入 Prompt 的引用，包含引用 ID、文档名、章节路径等。
     * 用于调试和追踪。
     */
    private List<String> renderedReferenceDetails;

    /**
     * 被省略引用的详情列表。
     *
     * 每个元素描述一个因超出预算而被省略的引用。
     * 用于调试和追踪，帮助调整预算参数。
     */
    private List<String> omittedReferenceDetails;
}
