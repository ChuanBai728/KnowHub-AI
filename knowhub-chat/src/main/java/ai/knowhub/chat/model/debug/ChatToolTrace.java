package ai.knowhub.chat.model.debug;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【聊天工具调用追踪】
 *
 * 作用：记录一次工具调用的详细信息。
 * 在 ReAct Agent 模式下，AI 会根据需要调用外部工具（如搜索引擎、数据库、API 等），
 * 每次工具调用都会产生一条追踪记录。
 *
 * 所属架构位置：属于 Agent 执行层的工具调用监控，是调试追踪系统的核心组成部分。
 * 通过此模型可以追踪 AI 的「行动（Act）」过程，了解 AI 如何使用工具解决问题。
 *
 * 设计模式说明：「追踪记录（Trace Record）」模式，
 * 与 ChatModelUsageTrace（模型追踪）配合，完整记录 Agent 的推理-行动循环。
 *
 * @author knowhub
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatToolTrace {

    /**
     * 工具名称（toolName）
     * 被调用的工具名称，如：
     * - "tavily_search"：Tavily 网络搜索工具
     * - "knowledge_retrieval"：知识库检索工具
     * - "sql_query"：SQL 查询工具 等
     */
    private String toolName;

    /**
     * 调用状态（status）
     * 标识工具调用的结果，如 "SUCCESS"（成功）、"FAILED"（失败）、
     * "TIMEOUT"（超时）等。
     */
    private String status;

    /**
     * 输入摘要（inputSummary）
     * 工具调用参数的摘要描述，便于在不暴露完整参数的情况下了解调用意图。
     */
    private String inputSummary;

    /**
     * 实际生效的输入（effectiveInput）
     * 工具最终使用的输入参数（可能经过拦截器修正）。
     * 例如 Tavily 搜索工具的入参拦截器可能会自动补充缺失的 query 参数。
     */
    private String effectiveInput;

    /**
     * 输出摘要（outputSummary）
     * 工具返回结果的摘要描述。
     */
    private String outputSummary;

    /**
     * 错误信息（errorMessage）
     * 如果工具调用失败，记录详细的错误信息。
     */
    private String errorMessage;

    /**
     * 引用数量（referenceCount）
     * 工具返回的引用/结果数量。例如搜索工具返回了多少条搜索结果。
     */
    private Integer referenceCount;

    /**
     * 主题/话题（topic）
     * 工具调用的主题标签，用于分类和分析。
     */
    private String topic;

    /**
     * 调用耗时（durationMs，毫秒）
     * 工具调用的总耗时。
     */
    private Long durationMs;
}
