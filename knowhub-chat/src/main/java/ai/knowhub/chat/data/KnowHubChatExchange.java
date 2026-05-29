package ai.knowhub.chat.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

/**
 * 聊天对话交换（Exchange）的数据实体。
 *
 * 对应数据库表：knowhub_chat_exchange
 *
 * 什么是交换（Exchange）？
 * 一次"交换"是用户与 AI 之间一个完整的问答交互：
 * 
 *   用户提出一个问题（userPrompt / question）
 *   AI 生成一个回答（replyContent / answer）
 *   系统记录完整的推理过程（思考步骤、引用来源、使用的工具等）
 * 
 * 一个会话（Dialogue）由多个 Exchange 组成，形成完整的对话历史。
 *
 * 记录的详细信息
 * 
 *   <b>问题和回答</b>：用户输入和 AI 输出的原始文本
 *   <b>思考步骤</b>：AI 的推理过程（Chain of Thought）
 *   <b>引用来源</b>：回答中引用的知识库文档
 *   <b>追问建议</b>：系统自动生成的后续问题
 *   <b>工具使用</b>：调用了哪些外部工具（如搜索）
 *   <b>性能指标</b>：首 Token 延迟、总耗时等
 * 继承关系
 * 继承自 BaseTableData，自动拥有创建时间、更新时间等公共字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_chat_exchange")
@EqualsAndHashCode(callSuper = true)
public class KnowHubChatExchange extends BaseTableData {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 对话编码（会话 ID）。
     * 标识这次交换属于哪个会话。
     */
    @TableField("dialogue_code")
    private String conversationId;

    /**
     * 用户的问题（Prompt）。
     * 用户在聊天框中输入的原始问题文本。
     */
    @TableField("user_prompt")
    private String question;

    /**
     * AI 的回答（Reply）。
     * AI 生成的最终回答文本，可能是 Markdown 格式。
     */
    @TableField("reply_content")
    private String answer;

    /**
     * 推理/思考步骤列表（JSON 格式）。
     * 记录 AI 的 Chain of Thought（思维链）过程，
     * 包括每一步的推理逻辑和判断依据。
     */
    @TableField("reasoning_note_list")
    private String thinkingSteps;

    /**
     * 引用来源快照列表（JSON 格式）。
     * 记录回答中引用的知识库文档片段，
     * 包括文档名称、片段内容、相似度分数等信息。
     */
    @TableField("source_snapshot_list")
    private String referenceList;

    /**
     * 追问建议列表（JSON 格式）。
     * 系统自动生成的后续问题建议，帮助用户继续深入探讨。
     * 例如：["能详细解释一下吗？", "有没有相关案例？"]
     */
    @TableField("followup_suggestion_list")
    private String recommendationList;

    /**
     * 使用的工具列表（JSON 格式）。
     * 记录 AI 在回答过程中调用了哪些外部工具，
     * 如 "tavily_search"（联网搜索）等。
     */
    @TableField("tool_trace_list")
    private String usedToolList;

    /**
     * 调试追踪信息（JSON 格式）。
     * 记录完整的调试信息，包括模型调用参数、中间结果等，
     * 用于问题排查和系统优化。
     */
    @TableField("debug_trace_json")
    private String debugTraceJson;

    /**
     * 交换状态。
     * 使用整数编码表示这次交换的状态，例如：
     * 
     *   0 —— 进行中（AI 正在生成回答）
     *   1 —— 已完成
     *   2 —— 已停止（用户手动停止）
     *   3 —— 出错
     * 
     */
    @TableField("exchange_state")
    private Integer turnStatus;

    /**
     * 完成说明 / 错误信息。
     * 如果交换正常完成，可能记录完成原因；
     * 如果出错，记录具体的错误信息。
     */
    @TableField("finish_note")
    private String errorMessage;

    /**
     * 首 Token 延迟（毫秒）。
     * 从用户发送问题到 AI 返回第一个 Token 的时间。
     * 这是衡量用户体验的关键指标 —— 首 Token 延迟越低，用户感觉 AI 响应越快。
     */
    @TableField("first_token_latency_ms")
    private Long firstResponseTimeMs;

    /**
     * 总响应时间（毫秒）。
     * 从用户发送问题到 AI 完成整个回答的总耗时。
     */
    @TableField("total_latency_ms")
    private Long totalResponseTimeMs;
}
