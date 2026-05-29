package ai.knowhub.document.support;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档结构节点提取器（Document Structure Node Extractor）
 *
 * 【类的作用】
 * 文档结构分析的门面（Facade）类，将信号提取、歧义消解、层级构建和树验证
 * 四个步骤串联为一个完整的处理流程。调用方只需调用本类的 extract 方法，
 * 即可获得文档的完整结构树。
 *
 * 【在架构中的角色】
 * 属于文档结构解析流水线的顶层协调器。
 * 内部组合了四个核心组件：
 * 1. SignalExtractor   - 信号提取：将文本行分类为结构信号
 * 2. AmbiguityResolver - 歧义消解：用 LLM 消除模糊分类
 * 3. HierarchyResolver - 层级构建：将信号转换为树状结构
 * 4. TreeValidator     - 树验证：修复和验证结构树的完整性
 *
 * 【设计模式】
 * 门面模式（Facade）：封装了复杂的内部处理流程，提供简单的对外接口。
 * 组合模式（Composition）：通过组合四个组件实现完整功能。
 *
 * 【处理流程】
 * 文档标题 + 解析文本 → 信号提取 → 歧义消解 → 层级构建 → 树验证 → 节点候选列表
 */
@AllArgsConstructor
@Component
public class DocumentStructureNodeExtractor {

    /** 信号提取器：将文本行分类为结构信号 */
    private final DocumentStructureSignalExtractor signalExtractor;

    /** 歧义解析器：用 LLM 消除模糊的结构信号 */
    private final DocumentStructureAmbiguityResolver ambiguityResolver;

    /** 层级解析器：将信号转换为具有父子关系的节点草案 */
    private final DocumentStructureHierarchyResolver hierarchyResolver;

    /** 树验证器：修复和验证结构树的完整性 */
    private final DocumentStructureTreeValidator treeValidator;

    /**
     * 从文档文本中提取结构节点
     *
     * 【处理流程】
     * 1. 标准化标题和文本
     * 2. 如果文本为空，返回仅包含根节点的单节点树
     * 3. 信号提取：将文本行分类为结构信号
     * 4. 歧义消解：用 LLM 消除模糊信号
     * 5. 层级构建：将信号转换为节点草案
     * 6. 树验证：修复和验证结构树
     *
     * @param documentTitle 文档标题
     * @param parsedText    解析后的纯文本
     * @return 结构节点候选列表，表示文档的完整结构树
     */
    public List<DocumentStructureNodeCandidate> extract(String documentTitle, String parsedText) {
        String normalizedTitle = StrUtil.blankToDefault(documentTitle, "文档").trim();
        String normalizedText = StrUtil.blankToDefault(parsedText, "").trim();

        // 如果文本为空，返回仅包含根节点的单节点树
        if (normalizedText.isBlank()) {
            return List.of(new DocumentStructureNodeCandidate(
                1,
                DocumentStructureNodeTypeEnum.DOCUMENT.getCode(),
                null,
                0,
                0,
                0,
                "",
                normalizedTitle,
                normalizedTitle,
                "/document",
                "",
                "",
                null
            ));
        }

        // 步骤1：信号提取 - 将文本行分类为结构信号
        DocumentStructureSignalBatch signalBatch = signalExtractor.extract(normalizedTitle, normalizedText);
        List<DocumentStructureSignal> rawSignals = signalBatch == null ? List.of() : signalBatch.signals();
        List<String> allLines = signalBatch == null ? List.of() : signalBatch.contextLines();

        // 步骤2：歧义消解 - 用 LLM 消除模糊的结构信号
        List<DocumentStructureSignal> resolvedSignals = ambiguityResolver.resolve(normalizedTitle, allLines, rawSignals);

        // 步骤3：层级构建 - 将信号转换为具有父子关系的节点草案
        List<DocumentStructureNodeDraft> drafts = hierarchyResolver.resolve(normalizedTitle, resolvedSignals);

        // 步骤4：树验证 - 修复和验证结构树的完整性
        return treeValidator.validateAndBuild(normalizedTitle, drafts);
    }
}
