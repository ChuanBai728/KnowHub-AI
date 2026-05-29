package ai.knowhub.document.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.prompt.PromptTemplateNames;
import ai.knowhub.prompt.PromptTemplateService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档结构歧义解析器（Document Structure Ambiguity Resolver）
 *
 * 【类的作用】
 * 在文档结构分析过程中，某些行可能被规则引擎标记为"标题候选"（HEADING_CANDIDATE），
 * 即无法确定是标题还是列表项。本类利用 LLM（大语言模型）来消除这些歧义，
 * 将模糊的结构信号转换为确定的结构信号。
 *
 * 【在架构中的角色】
 * 属于文档结构解析流水线的第二层——歧义消解层。
 * 流程：信号提取（SignalExtractor）→ 歧义消解（本类）→ 层级构建（HierarchyResolver）→ 树验证（TreeValidator）
 *
 * 【设计模式】
 * - 门面模式（Facade）：封装了 LLM 调用、提示词构建、结果解析等复杂逻辑
 * - 策略模式：当 LLM 不可用或配置禁用时，直接返回原始信号（规则结果作为兜底）
 * - 使用 ObjectProvider 实现可选依赖注入（ChatModel 可能不存在）
 */
@Slf4j
@Component
public class DocumentStructureAmbiguityResolver {

    /** 文档管理配置属性，控制歧义消解的行为参数 */
    private final DocumentManageProperties properties;

    /** ChatModel 的可选提供者，用于获取 LLM 模型（可能不存在） */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /** Jackson 的 JSON 序列化/反序列化工具 */
    private final ObjectMapper objectMapper;

    /** 提示词模板服务，用于渲染 LLM 提示词 */
    private final PromptTemplateService promptTemplateService;

    /**
     * 构造器注入依赖
     *
     * @param properties           文档管理配置
     * @param chatModelProvider    ChatModel 的可选提供者
     * @param objectMapper         JSON 处理工具
     * @param promptTemplateService 提示词模板服务
     */
    public DocumentStructureAmbiguityResolver(DocumentManageProperties properties,
                                              ObjectProvider<ChatModel> chatModelProvider,
                                              ObjectMapper objectMapper,
                                              PromptTemplateService promptTemplateService) {
        this.properties = properties;
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 解析文档结构中的歧义信号
     *
     * 【处理流程】
     * 1. 检查是否有歧义信号需要处理
     * 2. 检查 LLM 歧义消解是否启用
     * 3. 筛选出需要消解的歧义信号（置信度在配置范围内）
     * 4. 构建提示词并调用 LLM
     * 5. 解析 LLM 返回的结果
     * 6. 将消解结果合并到原始信号列表中
     *
     * @param documentTitle 文档标题，用于构建上下文
     * @param allLines      文档的所有行，用于提供上下文
     * @param sourceSignals 原始信号列表（可能包含歧义信号）
     * @return 消解后的信号列表，歧义信号被转换为确定信号
     */
    public List<DocumentStructureSignal> resolve(String documentTitle,
                                                 List<String> allLines,
                                                 List<DocumentStructureSignal> sourceSignals) {
        // 空信号列表直接返回
        if (sourceSignals == null || sourceSignals.isEmpty()) {
            return List.of();
        }
        // 如果配置禁用了 LLM 歧义消解，直接返回原始信号
        if (!Boolean.TRUE.equals(properties.getStructureParsing().getLlmDisambiguationEnabled())) {
            return sourceSignals;
        }
        // 获取 ChatModel，如果不可用则返回原始信号
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return sourceSignals;
        }

        // 筛选出需要消解的歧义信号
        // 条件：是歧义信号 + 置信度在配置的上下限之间 + 限制数量
        List<DocumentStructureSignal> ambiguousSignals = sourceSignals.stream()
            .filter(signal -> signal != null
                && signal.isAmbiguous()
                && signal.getConfidence() >= properties.getStructureParsing().getAmbiguityConfidenceFloor()
                && signal.getConfidence() <= properties.getStructureParsing().getAmbiguityConfidenceCeil())
            .limit(Math.max(1, properties.getStructureParsing().getMaxAmbiguousSignalsPerCall()))
            .toList();
        if (ambiguousSignals.isEmpty()) {
            return sourceSignals;
        }

        try {
            // 构建提示词并调用 LLM
            String prompt = buildPrompt(documentTitle, ambiguousSignals, allLines);
            String content = ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();
            // 解析 LLM 返回的消解结果
            List<DisambiguationResult> results = parse(content);
            if (results.isEmpty()) {
                return sourceSignals;
            }
            // 构建行号到消解结果的映射
            Map<Integer, DisambiguationResult> resultMap = new LinkedHashMap<>();
            for (DisambiguationResult result : results) {
                if (result.lineNo == null) {
                    continue;
                }
                resultMap.put(result.lineNo, result);
            }
            // 将消解结果合并到原始信号中
            List<DocumentStructureSignal> merged = new ArrayList<>(sourceSignals.size());
            for (DocumentStructureSignal signal : sourceSignals) {
                DisambiguationResult resolved = signal == null ? null : resultMap.get(signal.getLineNo());
                merged.add(applyResult(signal, resolved));
            }
            return merged;
        }
        catch (Exception exception) {
            // LLM 调用失败时，回退到规则结果
            log.warn("结构歧义判定失败，回退到规则结果: {}", exception.getMessage());
            return sourceSignals;
        }
    }

    /**
     * 构建 LLM 提示词
     *
     * @param documentTitle    文档标题
     * @param ambiguousSignals 歧义信号列表
     * @param allLines         文档所有行
     * @return 渲染后的提示词字符串
     */
    private String buildPrompt(String documentTitle,
                               List<DocumentStructureSignal> ambiguousSignals,
                               List<String> allLines) {
        return promptTemplateService.render(PromptTemplateNames.DOCUMENT_STRUCTURE_AMBIGUITY, Map.of(
            "documentTitle", StrUtil.blankToDefault(documentTitle, "未命名文档"),
            "candidateBlocks", buildCandidateBlocks(ambiguousSignals, allLines)
        ));
    }

    /**
     * 构建候选块的上下文信息
     * 为每个歧义信号提取其周围的上下文行（前后各 N 行），
     * 并标记当前行（用 >> 前缀），帮助 LLM 理解上下文
     *
     * @param ambiguousSignals 歧义信号列表
     * @param allLines         文档所有行
     * @return 格式化的候选块文本
     */
    private String buildCandidateBlocks(List<DocumentStructureSignal> ambiguousSignals,
                                        List<String> allLines) {
        StringBuilder builder = new StringBuilder();
        List<String> safeLines = allLines == null ? List.of() : allLines;
        int contextWindow = Math.max(1, properties.getStructureParsing().getContextWindowLines());
        for (DocumentStructureSignal signal : ambiguousSignals) {
            if (signal == null) {
                continue;
            }
            // 计算上下文窗口范围
            int currentIndex = Math.max(0, signal.getLineNo() - 1);
            int start = Math.max(0, currentIndex - contextWindow);
            int end = Math.min(safeLines.size() - 1, currentIndex + contextWindow);
            // 构建带标记的上下文文本
            StringBuilder contextBuilder = new StringBuilder();
            for (int index = start; index <= end; index++) {
                contextBuilder.append(index + 1 == signal.getLineNo() ? ">> " : "   ")
                    .append(index + 1)
                    .append(": ")
                    .append(StrUtil.blankToDefault(safeLines.get(index), ""))
                    .append('\n');
            }
            // 使用模板渲染单个候选块
            builder.append(promptTemplateService.render(PromptTemplateNames.DOCUMENT_STRUCTURE_AMBIGUITY_CANDIDATE, Map.of(
                "lineNo", signal.getLineNo(),
                "contextLines", contextBuilder.toString().stripTrailing(),
                "initialKind", signal.getKind() == null ? "" : signal.getKind().name(),
                "initialTitle", StrUtil.blankToDefault(signal.getTitle(), ""),
                "initialCode", StrUtil.blankToDefault(signal.getNodeCode(), "")
            ))).append("\n\n");
        }
        return builder.toString().trim();
    }

    /**
     * 解析 LLM 返回的消解结果
     * LLM 返回 JSON 数组格式的结果，包含每行的消解判定
     *
     * @param raw LLM 返回的原始文本
     * @return 消解结果列表
     * @throws Exception JSON 解析异常
     */
    private List<DisambiguationResult> parse(String raw) throws Exception {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        String normalized = raw.trim();
        // 提取 JSON 数组部分（跳过可能的前缀文本）
        int start = normalized.indexOf('[');
        int end = normalized.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        String jsonArray = normalized.substring(start, end + 1);
        // 反序列化为 List<Map>
        List<Map<String, Object>> items = objectMapper.readValue(jsonArray, new TypeReference<List<Map<String, Object>>>() {
        });
        List<DisambiguationResult> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            // 提取各字段
            Integer lineNo = item.get("line_no") instanceof Number number ? number.intValue() : null;
            String resolvedKind = item.get("resolved_kind") == null ? "" : String.valueOf(item.get("resolved_kind")).trim();
            Integer levelHint = item.get("level_hint") instanceof Number number ? number.intValue() : null;
            results.add(new DisambiguationResult(lineNo, resolvedKind, levelHint));
        }
        return results;
    }

    /**
     * 将消解结果应用到原始信号上
     *
     * @param source   原始信号
     * @param resolved 消解结果（可能为 null）
     * @return 更新后的信号
     */
    private DocumentStructureSignal applyResult(DocumentStructureSignal source,
                                                DisambiguationResult resolved) {
        if (source == null || resolved == null || StrUtil.isBlank(resolved.resolvedKind)) {
            return source;
        }
        // 将消解结果的类型字符串转换为枚举
        DocumentStructureSignalKind targetKind = switch (resolved.resolvedKind.trim().toUpperCase()) {
            case "HEADING" -> DocumentStructureSignalKind.HEADING;
            case "LIST_ITEM" -> DocumentStructureSignalKind.LIST_ITEM;
            default -> DocumentStructureSignalKind.BODY;
        };
        source.setKind(targetKind);
        // 如果判定为标题且有层级提示，更新层级
        if (targetKind == DocumentStructureSignalKind.HEADING && resolved.levelHint != null && resolved.levelHint > 0) {
            source.setLevelHint(resolved.levelHint);
        }
        // 记录消解原因并提升置信度
        source.getReasons().add("llm-disambiguated");
        source.setConfidence(Math.max(source.getConfidence(), 0.88D));
        return source;
    }

    /**
     * LLM 消解结果的内部记录类
     *
     * @param lineNo       行号
     * @param resolvedKind 消解后的类型（HEADING/LIST_ITEM/BODY）
     * @param levelHint    标题层级提示（仅标题类型有效）
     */
    private record DisambiguationResult(
        Integer lineNo,
        String resolvedKind,
        Integer levelHint
    ) {
    }
}
