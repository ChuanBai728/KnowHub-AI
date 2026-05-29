package ai.knowhub.chat.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tavily 搜索请求参数对象。
 *
 * 该类封装了调用 Tavily 搜索 API 时需要传递的请求参数。
 * Tavily 是一个专门为 AI Agent 设计的联网搜索服务，
 * 能够返回结构化的搜索结果，非常适合与 LLM 集成使用。
 *
 * 在架构中的角色
 * 作为 TavilySearchTool#search() 方法的输入参数，
 * 由 LLM 在 Agent 执行过程中通过工具调用（Tool Calling）机制自动生成。
 * Spring AI 框架会将 LLM 输出的 JSON 参数自动反序列化为该对象。
 *
 * Lombok 注解说明
 * 
 *   @Data - 自动生成 getter/setter/toString/equals/hashCode 方法
 *   @NoArgsConstructor - 自动生成无参构造方法
 *   @AllArgsConstructor - 自动生成包含所有字段的构造方法
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchDto {

    /**
     * 搜索查询词。
     *
     * 由 LLM 根据用户问题生成的搜索关键词，例如用户问"今天天气如何"，
     * LLM 可能生成 query = "2024年XX城市今天天气"。这是 Tavily API 的必填参数。
     */
    private String query;

    /**
     * 搜索主题分类。
     *
     * 用于限定搜索范围，Tavily 支持的主题包括：
     * 
     *   "general" - 通用搜索（默认）
     *   "news" - 新闻类搜索
     *   "finance" - 金融财经类搜索
     * 
     * 不同主题会影响搜索结果的排序和来源。
     */
    private String topic;

    /**
     * 最大返回结果数。
     *
     * 限制 Tavily API 返回的搜索结果数量。如果未指定或小于等于 0，
     * 则使用配置文件中的默认值（TavilySearchProperties#maxResults）。
     */
    private Integer maxResults;
}
