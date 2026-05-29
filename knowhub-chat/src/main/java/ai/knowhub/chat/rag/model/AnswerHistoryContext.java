package ai.knowhub.chat.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回答历史上下文 —— 记录与当前回答相关的历史对话信息。
 *
 * 在 RAG 流水线中的角色
 * 在多轮对话场景中，用户经常会基于之前的回答继续追问（例如"它还有什么功能？"中的"它"指代上一轮回答的对象）。
 * 本类封装了组装 Prompt 时需要用到的历史上下文信息，帮助大模型理解对话的连续性。
 *
 * 字段说明
 * 
 *   #renderedText：渲染后的历史上下文文本，直接放入 Prompt
 *   #structuredContext：结构化的上下文信息（如之前的检索引用、文档名等）
 *   #recentContext：最近几轮对话的原文
 *   #followUpQuestion：标记当前问题是否为追问（追问时需要更多历史上下文）
 *   #totalBudget / #recentBudget / #structuredBudget：字符预算分配
 * @see ConversationExecutionPlan#answerHistoryContext 在执行计划中引用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerHistoryContext {

    /**
     * 渲染后的历史上下文文本。
     *
     * 这是最终放入 Prompt 的历史信息文本，可能包含之前对话的摘要、关键事实等。
     * 由 ai.knowhub.chat.rag.service.RagPromptAssemblyService 组装。
     */
    private String renderedText;

    /**
     * 结构化的上下文信息。
     *
     * 以结构化格式（如 JSON 或 Markdown）记录之前对话中的关键信息，
     * 例如之前检索到的文档名、引用来源、已确认的事实等。比 renderedText 更适合机器解析。
     */
    private String structuredContext;

    /**
     * 最近几轮对话的原文。
     *
     * 保留最近 N 轮用户和助手的完整对话原文（而非摘要），因为最近的对话
     * 通常与当前问题最相关，保留原文比摘要更有用。
     */
    private String recentContext;

    /**
     * 当前问题是否为追问。
     *
     * true 表示用户正在基于之前的回答继续提问。追问时需要更多历史上下文
     * 来理解"它"、"那个"等指代词的含义。
     */
    private boolean followUpQuestion;

    /**
     * 历史上下文的总字符预算。
     *
     * 所有历史信息（recent + structured）加起来不能超过此值，
     * 防止历史上下文占用过多 Prompt 空间。
     */
    private Integer totalBudget;

    /**
     * 最近对话原文的字符预算。
     *
     * #recentContext 的最大字符数限制。
     */
    private Integer recentBudget;

    /**
     * 结构化上下文的字符预算。
     *
     * #structuredContext 的最大字符数限制。
     */
    private Integer structuredBudget;

    /**
     * 判断历史上下文是否为空。
     *
     * @return true 表示没有可用的历史上下文（renderedText 为 null 或空白）
     */
    public boolean isEmpty() {
        return renderedText == null || renderedText.isBlank();
    }
}
