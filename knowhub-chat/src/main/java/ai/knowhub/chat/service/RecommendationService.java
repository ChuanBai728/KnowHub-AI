package ai.knowhub.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.knowhub.chat.config.ChatAgentProperties;
import ai.knowhub.chat.model.ConversationExchangeVo;
import ai.knowhub.prompt.PromptTemplateNames;
import ai.knowhub.prompt.PromptTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * 【推荐追问服务】
 *
 * 在每轮 AI 回答完成后，自动生成 1~3 个推荐的追问问题，引导用户继续深入对话。
 *
 * 工作流程：
 * 1. 检查是否启用了推荐功能（properties.isRecommendationEnabled()）
 * 2. 构建提示词，包含最近对话上下文、当前问题和回答
 * 3. 调用大模型生成推荐问题（JSON 数组格式）
 * 4. 解析 JSON 数组，去重后返回最多 3 个推荐问题
 *
 * 超时控制：
 * 使用 CompletableFuture.orTimeout() 设置超时时间，超时后返回空列表，
 * 不会阻塞整个会话的收尾流程。
 *
 * 设计模式：异步任务模式。推荐问题的生成在独立的线程池中执行，
 * 使用 @Qualifier("chatPostProcessExecutorService") 注入专用线程池。
 *
 * 使用场景：BusinessChatService.finishSuccessfully() 中调用，
 * 在回答生成完成后异步生成推荐追问。
 *
 * 提示词模板：使用 PromptTemplateNames.RECOMMENDATION_USER 模板，
 * 通过 PromptTemplateService 渲染。
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    /** 聊天 Agent 配置属性，包含推荐功能的开关、超时时间、提示词等配置 */
    private final ChatAgentProperties properties;

    /** Jackson 的 ObjectMapper，用于解析模型返回的 JSON 数组 */
    private final ObjectMapper objectMapper;

    /**
     * 推荐问题生成专用线程池。
     * 使用 @Qualifier 区分不同的 ExecutorService Bean。
     */
    private final ExecutorService recommendationExecutorService;

    /** 可观测的聊天模型服务，封装了模型调用和追踪功能 */
    private final ObservedChatModelService observedChatModelService;

    /** 提示词模板服务，用于渲染模板化的提示词 */
    private final PromptTemplateService promptTemplateService;

    /**
     * 构造函数。
     *
     * @param properties                    聊天 Agent 配置属性
     * @param objectMapper                  Jackson ObjectMapper
     * @param recommendationExecutorService 推荐问题生成专用线程池
     * @param observedChatModelService      可观测的聊天模型服务
     * @param promptTemplateService         提示词模板服务
     */
    public RecommendationService(ChatAgentProperties properties,
                                 ObjectMapper objectMapper,
                                 @Qualifier("chatPostProcessExecutorService") ExecutorService recommendationExecutorService,
                                 ObservedChatModelService observedChatModelService,
                                 PromptTemplateService promptTemplateService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.recommendationExecutorService = recommendationExecutorService;
        this.observedChatModelService = observedChatModelService;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 生成推荐追问问题。
     *
     * @param question        当前用户问题
     * @param answer          AI 生成的回答
     * @param recentExchanges 最近的对话历史（用于构建上下文）
     * @param traceRecorder   追踪记录器（用于记录模型调用追踪）
     * @return 推荐问题列表（最多 3 个），如果功能未启用或生成失败则返回空列表
     */
    public List<String> generateRecommendations(String question,
                                                String answer,
                                                List<ConversationExchangeVo> recentExchanges,
                                                ConversationTraceRecorder traceRecorder) {

        // 检查功能开关和前置条件
        if (!properties.isRecommendationEnabled() || StrUtil.isBlank(answer)) {
            return List.of();
        }

        try {
            // 使用 CompletableFuture 在独立线程池中异步执行，带超时控制
            return CompletableFuture.supplyAsync(
                    () -> generateRecommendationsInternal(question, answer, recentExchanges, traceRecorder),

                    recommendationExecutorService
                )

                .orTimeout(Math.max(properties.getRecommendationTimeoutMs(), 1L), TimeUnit.MILLISECONDS)
                .exceptionally(exception -> {
                    log.warn("生成推荐问题超时或失败: {}", exception.getMessage());
                    return List.of();
                })
                .join();
        }
        catch (Exception exception) {
            log.warn("生成推荐问题失败", exception);
            return List.of();
        }
    }

    /**
     * 内部实现：生成推荐追问问题。
     *
     * 步骤：
     * 1. 构建最近对话上下文（最近 N 轮的问答原文）
     * 2. 使用提示词模板渲染完整的提示词
     * 3. 调用大模型生成 JSON 数组格式的推荐问题
     * 4. 解析 JSON，去重，最多返回 3 个
     */
    private List<String> generateRecommendationsInternal(String question,
                                                         String answer,
                                                         List<ConversationExchangeVo> recentExchanges,
                                                         ConversationTraceRecorder traceRecorder) {

        List<ConversationExchangeVo> safeRecentExchanges = recentExchanges == null ? List.of() : recentExchanges;
        StringBuilder recentContext = new StringBuilder();

        // 只取最近 N 轮对话（N = historyPreviewTurns）
        int startIndex = Math.max(0, safeRecentExchanges.size() - properties.getHistoryPreviewTurns());

        for (int index = startIndex; index < safeRecentExchanges.size(); index++) {
            ConversationExchangeVo exchange = safeRecentExchanges.get(index);
            recentContext.append("用户：").append(exchange.getQuestion()).append('\n');
            if (StrUtil.isNotBlank(exchange.getAnswer())) {
                recentContext.append("助手：").append(exchange.getAnswer()).append('\n');
            }
        }

        // 渲染提示词
        String prompt = promptTemplateService.render(PromptTemplateNames.RECOMMENDATION_USER, Map.of(
            "basePrompt", StrUtil.blankToDefault(properties.getRecommendationPrompt(), ""),
            "recentContext", recentContext.toString().trim(),
            "question", StrUtil.blankToDefault(question, ""),
            "answer", StrUtil.blankToDefault(answer, "")
        ));

        try {

            // 调用大模型
            String content = observedChatModelService.callText("recommendation", null, prompt, traceRecorder);

            if (StrUtil.isBlank(content)) {
                return List.of();
            }

            // 提取 JSON 数组（模型可能在 JSON 前后输出额外文本）
            String jsonArray = extractJsonArray(content);
            if (StrUtil.isBlank(jsonArray)) {
                log.warn("推荐问题输出不是有效 JSON 数组: {}", content);
                return List.of();
            }

            // 解析 JSON 数组
            List<String> rawList = objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {
            });
            // 去重并限制最多 3 个
            LinkedHashSet<String> unique = new LinkedHashSet<>();

            for (String item : rawList) {
                if (StrUtil.isNotBlank(item)) {
                    unique.add(item.trim());
                }
                if (unique.size() >= 3) {
                    break;
                }
            }
            return new ArrayList<>(unique);
        }
        catch (Exception exception) {
            log.warn("生成推荐问题失败", exception);
            return List.of();
        }
    }

    /**
     * 从模型输出中提取 JSON 数组。
     *
     * 模型有时会在 JSON 数组前后输出额外的解释文本，
     * 此方法通过查找第一个 '[' 和最后一个 ']' 来提取纯 JSON 数组。
     *
     * @param content 模型输出的完整文本
     * @return 提取的 JSON 数组字符串，如果找不到则返回 null
     */
    private String extractJsonArray(String content) {

        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }
}
