package ai.knowhub.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tavily 联网搜索服务的配置属性类。
 *
 * Tavily 是一个专门为 AI Agent 设计的搜索引擎 API，
 * 能够返回结构化的搜索结果，包括网页摘要、原文内容等，
 * 非常适合让大模型获取最新的互联网信息。
 *
 * 配置示例（application.yml）
 * 
 * app:
 *   tavily:
 *     enabled: true
 *     api-key: tvly-xxxxxxxxxxxx
 *     base-url: https://api.tavily.com
 *     search-path: /search
 *     topic: general
 *     search-depth: advanced
 *     max-results: 5
 *     include-answer: true
 *     include-raw-content: false
 *     connect-timeout-ms: 3000
 *     read-timeout-ms: 6000
 * Tavily API 参数说明
 * 
 *   topic：搜索主题类型 —— general（通用）、news（新闻）、finance（财经）
 *   searchDepth：搜索深度 —— basic（快速，返回摘要）或 advanced（深入，返回更多内容）
 *   includeAnswer：是否返回 AI 生成的直接答案
 *   includeRawContent：是否返回网页的原始 HTML 内容
 * 
 */
@ConfigurationProperties(prefix = "app.tavily")
public class TavilySearchProperties {

    /**
     * 是否启用 Tavily 搜索功能。
     * 设为 false 时，Agent 将无法进行联网搜索。
     * 默认启用。
     */
    private boolean enabled = true;

    /**
     * Tavily API 的基础 URL。
     * 默认值：https://api.tavily.com
     */
    private String baseUrl = "https://api.tavily.com";

    /**
     * 搜索接口的路径。
     * 与 baseUrl 拼接成完整的请求地址。
     * 默认值：/search
     */
    private String searchPath = "/search";

    /**
     * Tavily API 密钥。
     * 需要在 Tavily 官网注册获取。格式通常为 "tvly-xxxxxxxx"。
     * 安全提示：建议通过环境变量注入，不要硬编码在配置文件中。
     */
    private String apiKey;

    /**
     * 搜索主题类型。
     * 可选值：
     * 
     *   "general" —— 通用搜索（默认）
     *   "news" —— 新闻搜索
     *   "finance" —— 财经搜索
     * 
     */
    private String topic = "general";

    /**
     * 搜索深度。
     * 可选值：
     * 
     *   "basic" —— 基础搜索，速度快，返回摘要信息
     *   "advanced" —— 深度搜索，速度较慢，返回更详细的内容（默认）
     * 
     */
    private String searchDepth = "advanced";

    /**
     * 最大返回结果数。
     * 控制每次搜索返回多少条结果。结果越多，信息越全面，但 Token 消耗也越大。
     * 默认值：5
     */
    private int maxResults = 5;

    /**
     * 是否返回 AI 生成的直接答案。
     * 开启后，Tavily 会基于搜索结果生成一段综合性的答案。
     * 默认开启。
     */
    private boolean includeAnswer = true;

    /**
     * 是否返回网页的原始内容。
     * 开启后，会返回网页的完整 HTML 文本，数据量较大。
     * 一般场景建议关闭，只在需要详细分析网页内容时开启。
     * 默认关闭。
     */
    private boolean includeRawContent = false;

    /**
     * HTTP 连接超时时间（毫秒）。
     * 建立 TCP 连接的最大等待时间。
     * 默认值：3000ms（3 秒）
     */
    private int connectTimeoutMs = 3000;

    /**
     * HTTP 读取超时时间（毫秒）。
     * 等待服务器响应数据的最大时间。
     * 默认值：6000ms（6 秒）
     */
    private int readTimeoutMs = 6000;

    // ==================== Getter / Setter 方法 ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSearchPath() {
        return searchPath;
    }

    public void setSearchPath(String searchPath) {
        this.searchPath = searchPath;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSearchDepth() {
        return searchDepth;
    }

    public void setSearchDepth(String searchDepth) {
        this.searchDepth = searchDepth;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public boolean isIncludeAnswer() {
        return includeAnswer;
    }

    public void setIncludeAnswer(boolean includeAnswer) {
        this.includeAnswer = includeAnswer;
    }

    public boolean isIncludeRawContent() {
        return includeRawContent;
    }

    public void setIncludeRawContent(boolean includeRawContent) {
        this.includeRawContent = includeRawContent;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
