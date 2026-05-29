package ai.knowhub.chat.model.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 【会话摘要结构化载荷】
 *
 * 作用：存储 AI 生成的会话摘要的结构化数据。
 * 除了纯文本摘要外，还将摘要拆分为多个维度的结构化信息，
 * 以便在不同场景下精准使用（如检索时使用检索提示，回答时使用用户偏好等）。
 *
 * 所属架构位置：属于会话记忆管理模块的摘要压缩子系统。
 * 由 AI 模型在执行「摘要压缩」时生成，包含对对话历史的深度理解和提炼。
 *
 * 设计模式说明：「值对象（Value Object）」+ 「建造者模式（Builder）」，
 * 使用 @Builder.Default 为列表字段设置默认空列表，避免空指针异常。
 *
 * @author knowhub
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryPayload {

    /**
     * 摘要文本（summary）
     * AI 生成的对话历史纯文本摘要，概括了对话的主要内容和结论。
     */
    private String summary;

    /**
     * 对话目标（conversationGoal）
     * 用户通过对话想要达成的目标，如 "了解产品功能"、"解决技术问题" 等。
     * 有助于 AI 在后续对话中保持目标导向。
     */
    private String conversationGoal;

    /**
     * 稳定事实列表（stableFacts）
     * 从对话中提取的已确认事实，这些事实在后续对话中可以作为已知前提。
     * 例如：用户是Java开发者、用户使用Spring Boot 3.x 等。
     */
    @Builder.Default
    private List<String> stableFacts = new ArrayList<>();

    /**
     * 用户偏好列表（userPreferences）
     * 从对话中推断出的用户偏好，用于个性化回答。
     * 例如：偏好简洁回答、喜欢代码示例、使用中文交流等。
     */
    @Builder.Default
    private List<String> userPreferences = new ArrayList<>();

    /**
     * 已解决要点列表（resolvedPoints）
     * 对话中已经解决/回答过的问题要点。
     * 避免 AI 在后续对话中重复回答已经解决的问题。
     */
    @Builder.Default
    private List<String> resolvedPoints = new ArrayList<>();

    /**
     * 待解决问题列表（pendingQuestions）
     * 对话中提出但尚未完全解决的问题。
     * 帮助 AI 在后续对话中主动跟进未完成的议题。
     */
    @Builder.Default
    private List<String> pendingQuestions = new ArrayList<>();

    /**
     * 检索提示列表（retrievalHints）
     * 从对话历史中提取的检索关键词和提示信息。
     * 在后续 RAG 检索时可以使用这些提示来优化检索效果。
     */
    @Builder.Default
    private List<String> retrievalHints = new ArrayList<>();
}
