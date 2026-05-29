package ai.knowhub.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 对话代理（ChatAgent）的配置属性类。
 *
 * 该类将 application.yml 中以 app.chat 为前缀的配置项绑定到 Java 对象。
 * 这些配置控制了 AI Agent 的行为，包括调用限制、系统提示词等。
 *
 * 配置示例（application.yml）
 * 
 * app:
 *   chat:
 *     system-prompt: 你是一个专业的AI助手...
 *     max-model-calls-per-run: 8
 *     max-tool-calls-per-run: 6
 *     history-preview-turns: 4
 *     recommendation-enabled: true
 *     recommendation-timeout-ms: 3000
 * 配置项说明
 * 
 *   <b>调用限制</b>：防止 Agent 陷入无限循环或过度调用模型/工具
 *   <b>系统提示词</b>：定义 Agent 的角色、行为和回答风格
 *   <b>推荐功能</b>：在回答后生成相关的追问建议
 *   <b>历史预览</b>：控制发送给模型的历史消息轮数
 * 
 */
@ConfigurationProperties(prefix = "app.chat")
public class ChatAgentProperties {

    /**
     * 是否开启追问推荐功能。
     * 开启后，每次回答结束后会自动生成几个相关的追问建议，
     * 帮助用户继续深入探讨。默认开启。
     */
    private boolean recommendationEnabled = true;

    /**
     * 单次运行中最大模型调用次数。
     * 一次用户提问可能触发多轮"思考-行动-观察"循环，
     * 此值限制了单次运行中最多调用多少次大模型。防止无限推理。
     * 默认值：8
     */
    private int maxModelCallsPerRun = 8;

    /**
     * 整个会话线程中最大模型调用次数。
     * 限制一个会话（Thread）从创建到销毁期间的总模型调用次数。
     * 默认值：40
     */
    private int maxModelCallsPerThread = 40;

    /**
     * 单次运行中最大工具调用次数。
     * 限制 Agent 在一次推理过程中最多调用多少次工具（如搜索）。
     * 默认值：6
     */
    private int maxToolCallsPerRun = 6;

    /**
     * 整个会话线程中最大工具调用次数。
     * 限制一个会话中总的工具调用次数。
     * 默认值：30
     */
    private int maxToolCallsPerThread = 30;

    /**
     * 历史消息预览轮数。
     * 在构建发送给模型的 Prompt 时，最多包含最近几轮的对话历史。
     * 设置过大可能导致 Token 超限，设置过小可能丢失上下文。
     * 默认值：4（即最近 4 轮对话）
     */
    private int historyPreviewTurns = 4;

    /**
     * 推荐功能的超时时间（毫秒）。
     * 生成追问推荐是异步操作，如果超时则跳过推荐，不影响主回答。
     * 默认值：3000ms（3 秒）
     */
    private long recommendationTimeoutMs = 3000L;

    /**
     * 系统提示词（System Prompt）。
     * 定义 AI Agent 的角色、行为准则和回答风格。
     * 这是控制 Agent 行为最重要的配置之一。
     * 示例："你是一个专业的AI助手，擅长回答用户的各种问题..."
     */
    private String systemPrompt = "";

    /**
     * 追问推荐的提示词。
     * 用于指导模型生成追问建议的专用提示词。
     * 如果为空，则使用默认的推荐逻辑。
     */
    private String recommendationPrompt = "";

    // ==================== Getter / Setter 方法 ====================

    public boolean isRecommendationEnabled() {
        return recommendationEnabled;
    }

    public void setRecommendationEnabled(boolean recommendationEnabled) {
        this.recommendationEnabled = recommendationEnabled;
    }

    public int getMaxModelCallsPerRun() {
        return maxModelCallsPerRun;
    }

    public void setMaxModelCallsPerRun(int maxModelCallsPerRun) {
        this.maxModelCallsPerRun = maxModelCallsPerRun;
    }

    public int getMaxModelCallsPerThread() {
        return maxModelCallsPerThread;
    }

    public void setMaxModelCallsPerThread(int maxModelCallsPerThread) {
        this.maxModelCallsPerThread = maxModelCallsPerThread;
    }

    public int getMaxToolCallsPerRun() {
        return maxToolCallsPerRun;
    }

    public void setMaxToolCallsPerRun(int maxToolCallsPerRun) {
        this.maxToolCallsPerRun = maxToolCallsPerRun;
    }

    public int getMaxToolCallsPerThread() {
        return maxToolCallsPerThread;
    }

    public void setMaxToolCallsPerThread(int maxToolCallsPerThread) {
        this.maxToolCallsPerThread = maxToolCallsPerThread;
    }

    public int getHistoryPreviewTurns() {
        return historyPreviewTurns;
    }

    public void setHistoryPreviewTurns(int historyPreviewTurns) {
        this.historyPreviewTurns = historyPreviewTurns;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getRecommendationPrompt() {
        return recommendationPrompt;
    }

    public void setRecommendationPrompt(String recommendationPrompt) {
        this.recommendationPrompt = recommendationPrompt;
    }

    public long getRecommendationTimeoutMs() {
        return recommendationTimeoutMs;
    }

    public void setRecommendationTimeoutMs(long recommendationTimeoutMs) {
        this.recommendationTimeoutMs = recommendationTimeoutMs;
    }
}
