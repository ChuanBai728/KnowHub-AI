package ai.knowhub.chat.model.debug;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【聊天模型使用追踪】
 *
 * 作用：记录一次大语言模型（LLM）调用的详细使用信息，
 * 包括 Token 消耗、耗时、状态等。用于成本监控和性能优化。
 *
 * 所属架构位置：属于模型调用监控层，是可观测性系统的重要组成部分。
 * 在一次完整的对话中，可能会多次调用模型（问题改写、检索增强、回答生成等），
 * 每次调用都会产生一条追踪记录。
 *
 * 设计模式说明：「追踪记录（Trace Record）」模式，
 * 记录每次模型调用的关键指标，支持事后分析和成本核算。
 *
 * 关键概念说明：
 * - Token：大语言模型处理文本的基本单位，1个中文字约等于1-2个Token
 * - Prompt Tokens：输入（提示词）消耗的 Token 数
 * - Completion Tokens：输出（回答）消耗的 Token 数
 * - Total Tokens = Prompt Tokens + Completion Tokens
 *
 * @author knowhub
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatModelUsageTrace {

    /**
     * 阶段名称（stageName）
     * 标识本次模型调用属于哪个处理阶段，如：
     * - "QUERY_REWRITE"：问题改写阶段
     * - "RAG_RETRIEVE"：RAG 检索增强阶段
     * - "ANSWER_GENERATE"：回答生成阶段
     * - "SUMMARY_COMPRESSION"：摘要压缩阶段
     */
    private String stageName;

    /**
     * 模型提供商（provider）
     * 标识使用了哪个 AI 服务商的模型，如 "dashscope"（阿里云灵积）、
     * "siliconflow"（硅基流动）、"openai" 等。
     */
    private String provider;

    /**
     * 模型名称（model）
     * 具体使用的模型名称，如 "qwen-plus"、"qwen-max"、"gpt-4" 等。
     */
    private String model;

    /**
     * 输入 Token 数（promptTokens / PT）
     * 发送给模型的提示词（Prompt）消耗的 Token 数量。
     * 包括系统提示词、历史对话、检索到的文档片段等。
     */
    private Integer promptTokens;

    /**
     * 输出 Token 数（completionTokens / CT）
     * 模型生成的回答消耗的 Token 数量。
     */
    private Integer completionTokens;

    /**
     * 总 Token 数（totalTokens = PT + CT）
     * 本次模型调用消耗的 Token 总量。
     */
    private Integer totalTokens;

    /**
     * 预估费用（estimatedCost）
     * 根据 Token 消耗量和模型定价预估的本次调用费用（单位通常为元）。
     */
    private Double estimatedCost;

    /**
     * 调用耗时（durationMs，毫秒）
     * 从发送请求到收到完整响应的时间间隔。
     */
    private Long durationMs;

    /**
     * 调用状态（status）
     * 标识本次模型调用的结果，如 "SUCCESS"（成功）、"FAILED"（失败）等。
     */
    private String status;
}
