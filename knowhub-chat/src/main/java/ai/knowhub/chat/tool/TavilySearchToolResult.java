package ai.knowhub.chat.tool;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ai.knowhub.chat.model.SearchReference;

/**
 * Tavily 搜索结果对象。
 *
 * 该类封装了 Tavily 搜索 API 返回的结构化结果，包括 AI 生成的摘要答案
 * 和原始搜索结果列表。该对象会被返回给 LLM，作为工具调用的执行结果，
 * LLM 基于此结果生成最终的用户回答。
 *
 * 在架构中的角色
 * 作为 TavilySearchTool#search() 方法的返回值，
 * 属于"工具结果"（Tool Result）类型。在 Spring AI 的工具调用机制中，
 * 工具执行结果会被序列化为 JSON 并反馈给 LLM，LLM 据此决定下一步行动
 * 或直接生成回答。
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
public class TavilySearchToolResult {

    /**
     * 实际执行的搜索查询词。
     *
     * 可能与原始请求中的 query 不同，因为系统会根据当前日期等上下文信息
     * 对查询词进行增强（例如自动添加日期前缀以获取时效性更强的结果）。
     */
    private String query;

    /**
     * Tavily AI 生成的摘要答案。
     *
     * Tavily API 会基于搜索结果自动生成一段简洁的摘要回答，
     * 该字段可以直接作为 LLM 生成最终回答的参考素材。
     * 如果 Tavily 未能生成摘要，该字段为空字符串。
     */
    private String answer;

    /**
     * 搜索结果引用列表。
     *
     * 包含多条搜索结果，每条结果包含标题（title）、URL 和内容摘要（content）。
     * 这些引用会同时传递给 LLM 用于生成回答，并保存到数据库用于前端展示来源链接。
     * 列表使用 List.copyOf() 创建不可变副本，确保数据安全。
     */
    private List<SearchReference> results;
}
