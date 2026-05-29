package ai.knowhub.chat.rag.service;

import cn.hutool.core.util.StrUtil;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.AnswerHistoryContext;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 【回答历史上下文组装器】
 *
 * 这个类负责判断用户的当前问题是否是"追问"（即对前几轮对话的延续），
 * 如果是追问，就从最近的对话记录中提取用户的历史问题，作为上下文补充到 Prompt 中。
 *
 * 作用：在 RAG 流水线中，用户经常会问"那它怎么配置？"、"继续说"、"第三条呢？"这类追问，
 * 这些问题单独看没有完整语义，需要结合上文才能理解。这个类就是为了解决这个问题。
 *
 * 设计模式：策略模式（Strategy Pattern）——根据问题特征决定是否附加历史上下文。
 *
 * 使用场景：在 {@link ChatPreparationOrchestrator} 编排阶段被调用，
 * 产出的 {@link AnswerHistoryContext} 会被传递给 {@link RagPromptAssemblyService} 组装到最终 Prompt 中。
 */
@Service
public class AnswerHistoryContextAssembler {

    /**
     * 追问提示词集合——这些词出现时，大概率说明用户在问前几轮的内容。
     * 例如"刚才"指代上一轮，"继续"表示接着之前的话题，"为什么"可能是追问原因。
     */
    private static final Set<String> FOLLOW_UP_HINTS = Set.of(
        "刚才", "上面", "前面", "前文", "上一条", "上一个", "上一轮", "这个", "那个", "这条", "那条",
        "继续", "展开", "补充", "详细", "细说", "进一步", "为什么", "怎么做", "怎么理解", "还有呢"
    );

    /**
     * RAG 配置属性，包含历史上下文的最大字符数等配置项。
     */
    private final ChatRagProperties properties;

    /**
     * 构造函数，通过 Spring 依赖注入 ChatRagProperties。
     *
     * @param properties RAG 配置属性
     */
    public AnswerHistoryContextAssembler(ChatRagProperties properties) {
        this.properties = properties;
    }

    /**
     * 组装回答历史上下文。
     *
     * 核心逻辑：
     * 1. 判断当前问题是否像追问（通过关键词匹配、问题长度等规则）
     * 2. 从最近的对话记录中提取用户的历史问题
     * 3. 如果是追问且有历史上下文，就返回包含历史文本的上下文对象
     * 4. 否则返回空上下文（不附加历史信息）
     *
     * @param question              当前用户问题原文
     * @param answerRecentTranscript 最近的对话记录文本（来自会话记忆压缩服务）
     * @return 组装好的回答历史上下文，包含是否为追问、渲染后的历史文本等信息
     */
    public AnswerHistoryContext assemble(String question, String answerRecentTranscript) {
        // 去除首尾空白
        String normalizedQuestion = safeText(question);
        // 从对话记录中提取用户的历史问题行
        String recentUserContext = extractRecentUserQuestions(answerRecentTranscript);
        // 获取历史上下文的最大字符预算，至少为 1
        int totalBudget = Math.max(1, properties.getAnswerHistoryMaxChars());
        boolean hasRecentContext = StrUtil.isNotBlank(recentUserContext);
        // 判断当前问题是否像追问
        boolean followUpQuestion = looksLikeFollowUpQuestion(normalizedQuestion, hasRecentContext);

        // 如果不像追问，或者没有历史上下文，直接返回空上下文
        if (!followUpQuestion || !hasRecentContext) {
            return emptyContext(totalBudget, followUpQuestion);
        }

        // 渲染历史上下文文本（带标题和预算裁剪）
        String recentPart = renderRecentContext(recentUserContext, totalBudget);
        if (recentPart.isBlank()) {
            return emptyContext(totalBudget, followUpQuestion);
        }
        // 构建完整的历史上下文对象
        return AnswerHistoryContext.builder()
            .renderedText(recentPart)           // 渲染后的完整文本，直接拼入 Prompt
            .structuredContext("")               // 结构化上下文（当前未使用）
            .recentContext(recentPart)           // 最近对话上下文
            .followUpQuestion(followUpQuestion)  // 是否为追问
            .totalBudget(totalBudget)            // 总字符预算
            .recentBudget(totalBudget)           // 最近上下文分配的预算
            .structuredBudget(0)                 // 结构化上下文分配的预算
            .build();
    }

    /**
     * 构建空的历史上下文对象（不附加任何历史信息）。
     *
     * @param totalBudget      总字符预算
     * @param followUpQuestion 是否为追问
     * @return 空的 AnswerHistoryContext 对象
     */
    private AnswerHistoryContext emptyContext(int totalBudget, boolean followUpQuestion) {
        return AnswerHistoryContext.builder()
            .renderedText("")
            .structuredContext("")
            .recentContext("")
            .followUpQuestion(followUpQuestion)
            .totalBudget(totalBudget)
            .recentBudget(0)
            .structuredBudget(0)
            .build();
    }

    /**
     * 从对话记录中提取用户的历史问题。
     *
     * 对话记录的格式通常是：
     * 【最近相关对话】
     * 用户：xxx
     * 助手：xxx
     * 用户：xxx
     *
     * 这个方法只保留"用户："开头的行，忽略助手的回复。
     *
     * @param answerRecentTranscript 最近的对话记录原文
     * @return 只包含用户问题的文本，每行一个问题
     */
    private String extractRecentUserQuestions(String answerRecentTranscript) {
        String normalized = safeText(answerRecentTranscript);
        // 去掉可能存在的标题前缀
        if (normalized.startsWith("【最近相关对话】")) {
            normalized = normalized.substring("【最近相关对话】".length()).trim();
        }
        if (normalized.startsWith("最近相关对话：")) {
            normalized = normalized.substring("最近相关对话：".length()).trim();
        }
        StringBuilder builder = new StringBuilder();
        for (String line : normalized.split("\n")) {
            String trimmed = safeText(line);
            // 只保留"用户："开头的行
            if (!trimmed.startsWith("用户：")) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(trimmed);
        }
        return builder.toString().trim();
    }

    /**
     * 判断当前问题是否"看起来像追问"。
     *
     * 判断逻辑（满足任一条件即可）：
     * 1. 包含追问提示词（如"刚才"、"继续"、"为什么"等）
     * 2. 包含"第X条/点/项"这样的序号引用
     * 3. 问题很短（<=12 字），短问题在有上下文时通常是追问
     * 4. 问题<=18 字且以"呢"或"吗"结尾（疑问语气+短问题=追问）
     *
     * @param normalizedQuestion 标准化后的问题文本
     * @param hasRecentContext   是否有最近的对话上下文
     * @return true 表示像追问，false 表示不像
     */
    private boolean looksLikeFollowUpQuestion(String normalizedQuestion, boolean hasRecentContext) {
        // 没有上下文或问题为空，不可能是追问
        if (!hasRecentContext || StrUtil.isBlank(normalizedQuestion)) {
            return false;
        }
        // 包含追问提示词
        if (FOLLOW_UP_HINTS.stream().anyMatch(normalizedQuestion::contains)) {
            return true;
        }
        // 包含"第X条/点/项"的序号引用模式，例如"第三条呢"
        if (normalizedQuestion.matches(".*第\\s*[0-9一二三四五六七八九十百]+\\s*(条|点|项).*")) {
            return true;
        }
        // 短问题（<=12字）大概率是追问
        if (normalizedQuestion.length() <= 12) {
            return true;
        }
        // 问题<=18字且以"呢"或"吗"结尾
        return normalizedQuestion.length() <= 18 && (normalizedQuestion.endsWith("呢") || normalizedQuestion.endsWith("吗"));
    }

    /**
     * 渲染最近的对话上下文，加上标题说明。
     *
     * 标题"对话承接上下文（仅用于理解指代，不作为事实证据）"的作用是告诉 LLM：
     * 这段历史信息只是用来理解"它"、"那个"这类指代词，不能当作事实依据来回答。
     *
     * @param recentUserContext 用户历史问题文本
     * @param budget           最大字符数预算
     * @return 渲染后的上下文文本
     */
    private String renderRecentContext(String recentUserContext, int budget) {
        if (budget <= 0 || StrUtil.isBlank(recentUserContext)) {
            return "";
        }
        String title = "对话承接上下文（仅用于理解指代，不作为事实证据）：\n";
        // 如果预算连标题都放不下，直接裁剪原始文本
        if (budget <= title.length()) {
            return clipTail(recentUserContext, budget);
        }
        // 裁剪正文部分（保留最近的内容）
        String body = clipTail(recentUserContext, budget - title.length());
        if (body.isBlank()) {
            return "";
        }
        return title + body;
    }

    /**
     * 从文本末尾截取指定长度的内容（保留最近的对话）。
     *
     * 为什么从末尾截取？因为最近的对话对理解追问最有用。
     * 例如预算 100 字，对话有 200 字，就只保留最后 99 字 + 省略号。
     *
     * @param text     原始文本
     * @param maxChars 最大字符数
     * @return 截取后的文本
     */
    private String clipTail(String text, int maxChars) {
        String normalized = safeText(text);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        if (maxChars <= 1) {
            return "";
        }
        int start = Math.max(0, normalized.length() - (maxChars - 1));
        return "…" + normalized.substring(start);
    }

    /**
     * 安全的文本处理：null 转为空字符串，并去除首尾空白。
     *
     * @param text 原始文本
     * @return 处理后的文本，不会为 null
     */
    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
