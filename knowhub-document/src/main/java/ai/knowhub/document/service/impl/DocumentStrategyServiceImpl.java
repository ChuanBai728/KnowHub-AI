package ai.knowhub.document.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentStrategyPlan;
import ai.knowhub.document.data.KnowHubDocumentStrategyStep;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.service.DocumentStrategyService;
import ai.knowhub.document.service.DocumentStructureNodeService;
import ai.knowhub.document.support.ChunkCandidate;
import ai.knowhub.document.support.DocumentAnalysisResult;
import ai.knowhub.document.support.DocumentLineClassifier;
import ai.knowhub.document.support.DocumentStrategyPlanDraft;
import ai.knowhub.document.support.DocumentStrategyStepDraft;
import ai.knowhub.document.support.ParentBlockCandidate;
import ai.knowhub.prompt.PromptTemplateNames;
import ai.knowhub.prompt.PromptTemplateService;
import ai.knowhub.enums.DocumentChunkSourceTypeEnum;
import ai.knowhub.enums.DocumentContentQualityLevelEnum;
import ai.knowhub.enums.DocumentFileTypeEnum;
import ai.knowhub.enums.DocumentStrategyExecuteStatusEnum;
import ai.knowhub.enums.DocumentStrategyPipelineTypeEnum;
import ai.knowhub.enums.DocumentStrategyRoleEnum;
import ai.knowhub.enums.DocumentStrategySourceTypeEnum;
import ai.knowhub.enums.DocumentStrategyTypeEnum;
import ai.knowhub.enums.DocumentStructureLevelEnum;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 【文档切块策略服务实现】
 *
 * 这个类是 DocumentStrategyService 接口的核心实现，负责文档的切块策略推荐、策略规范化和切块执行。
 * 它是 RAG 流程中"索引构建"阶段的核心组件，决定了文档如何被切分成小块以便后续向量化和检索。
 *
 * 核心能力：
 *   1. 策略推荐（recommendStrategy）：根据文档特征（文件类型、结构化程度、内容质量）自动推荐切块策略
 *   2. 策略规范化（normalizeSteps）：验证和规范化用户调整后的策略步骤
 *   3. 切块执行（buildParentBlocks）：按照确认的策略执行切块，生成 Parent-Child 结构
 *
 * Parent-Child 切块设计：
 *   - 父块（Parent Block）：较大的文本段落，提供回答问题时需要的上下文
 *   - 子块（Child Chunk）：较小的文本片段，用于精确检索和匹配
 *   - 检索时命中子块，但最终发给 LLM 的是父块（包含完整上下文）
 *
 * 四种切块策略：
 *   1. STRUCTURE（结构切块）：基于文档标题/章节结构切分，保留天然的语义边界
 *   2. RECURSIVE（递归切块）：按段落 -> 行 -> 句子 -> 固定窗口的优先级递归切分，有重叠
 *   3. SEMANTIC（语义切块）：基于句子间的 Jaccard 相似度，在主题切换处切分
 *   4. LLM（大模型切块）：调用大模型智能判断切分点，处理复杂或低质量文本
 *
 * 策略推荐规则：
 *   - 父块流水线：有明显结构 -> STRUCTURE，否则 -> RECURSIVE
 *   - 子块流水线：低质量文本 -> LLM，有语义边界 -> SEMANTIC，都需要兜底 -> RECURSIVE
 *
 * 设计模式：Pipeline 模式（策略步骤按顺序执行，前一步的输出是后一步的输入），
 *           策略模式（每种切块策略封装为独立方法）
 */
@Slf4j
@AllArgsConstructor
@Service
public class DocumentStrategyServiceImpl implements DocumentStrategyService {

    /** 英文单词/数字 token 的正则模式 */
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[A-Za-z0-9]{2,}");

    /** 父块递归切块的最大字符数（父块较大，提供充足上下文） */
    private static final int PARENT_BLOCK_MAX_CHARS = 2200;

    /** 父块递归切块的重叠字符数 */
    private static final int PARENT_BLOCK_OVERLAP_CHARS = 180;

    /** 父块语义切块的最大字符数 */
    private static final int PARENT_SEMANTIC_MAX_CHARS = 1600;

    /** 父块语义切块的最小字符数 */
    private static final int PARENT_SEMANTIC_MIN_CHARS = 480;

    /** 文档管理配置属性（包含各种切块参数） */
    private final DocumentManageProperties properties;

    /** Jackson JSON 序列化器（用于解析大模型返回的 JSON 数组） */
    private final ObjectMapper objectMapper;

    /** ChatModel 的延迟提供者（大模型切块时需要，可能未配置） */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /** 文档行分类器，用于判断一行文本是否为标题 */
    private final DocumentLineClassifier documentLineClassifier;

    /** 文档结构节点服务（用于获取文档的章节结构） */
    private final DocumentStructureNodeService structureNodeService;

    /** 提示词模板服务（用于渲染大模型切块的提示词） */
    private final PromptTemplateService promptTemplateService;

    /**
     * 推荐切块策略。
     *
     * 根据文档特征自动判断应该使用哪些切块策略：
     *   - 文件类型（PDF/DOC/MD/HTML 更适合结构切块）
     *   - 结构化程度（标题数量、章节层次）
     *   - 内容质量（乱码比例、字符数）
     *   - 文档长度（是否需要递归切块兜底）
     *
     * @param document      文档记录
     * @param analysisResult 文档解析结果（标题数、段落数、字符数、质量等级等）
     * @return 策略方案草案（包含父块和子块的策略步骤、推荐理由）
     */
    @Override
    public DocumentStrategyPlanDraft recommendStrategy(KnowHubDocument document, DocumentAnalysisResult analysisResult) {

        List<String> reasonList = new ArrayList<>();
        DocumentFileTypeEnum fileType = DocumentFileTypeEnum.getRc(document.getFileType());

        // 根据文档特征判断是否推荐各种策略
        boolean structureRecommended = shouldUseStructure(fileType, analysisResult);
        boolean recursiveRecommended = shouldUseRecursive(analysisResult);
        boolean semanticRecommended = shouldUseSemantic(analysisResult);
        boolean llmRecommended = shouldUseLlm(analysisResult);

        // 构建父块流水线策略
        List<Integer> parentStrategyTypes = new ArrayList<>();
        Map<Integer, String> parentReasonMap = new LinkedHashMap<>();
        if (structureRecommended) {
            parentStrategyTypes.add(DocumentStrategyTypeEnum.STRUCTURE.getCode());
            parentReasonMap.put(DocumentStrategyTypeEnum.STRUCTURE.getCode(),
                "检测到文档具有较明显的标题或章节结构，父块优先保留天然章节边界。");
            reasonList.add("父块流水线优先采用基于文档结构切块，保留回答阶段需要的大语义单元。");
        }
        else {
            parentStrategyTypes.add(DocumentStrategyTypeEnum.RECURSIVE.getCode());
            parentReasonMap.put(DocumentStrategyTypeEnum.RECURSIVE.getCode(),
                "未识别出稳定结构时，父块先使用较大粒度的递归分块作为稳定回答单元。");
            reasonList.add("父块流水线未命中明显结构信号，默认使用较大粒度递归分块作为回答单元。");
        }

        // 构建子块流水线策略
        List<Integer> childStrategyTypes = new ArrayList<>();
        Map<Integer, String> childReasonMap = new LinkedHashMap<>();
        if (llmRecommended) {
            childStrategyTypes.add(DocumentStrategyTypeEnum.LLM.getCode());
            childReasonMap.put(DocumentStrategyTypeEnum.LLM.getCode(),
                "文档质量偏低或结构识别不稳定，子块先使用大模型智能切块增强复杂场景。");
            reasonList.add("子块流水线追加大模型智能切块，处理低质量或结构不稳定文本。");
        }
        else if (semanticRecommended) {
            childStrategyTypes.add(DocumentStrategyTypeEnum.SEMANTIC.getCode());
            childReasonMap.put(DocumentStrategyTypeEnum.SEMANTIC.getCode(),
                "文本主题边界相对明确，子块先使用语义分块优化召回边界。");
            reasonList.add("子块流水线优先采用语义分块，优化召回边界和主题完整性。");
        }

        // 递归切块作为兜底策略（当文档较长或需要控制长度时）
        if (recursiveRecommended || llmRecommended || childStrategyTypes.isEmpty()) {
            childStrategyTypes.add(DocumentStrategyTypeEnum.RECURSIVE.getCode());
            childReasonMap.put(DocumentStrategyTypeEnum.RECURSIVE.getCode(),
                "文档整体较长、存在超长段落，或需要在增强切块后追加长度兜底。");
            reasonList.add("子块流水线追加递归分块，控制召回单元长度并作为兜底。");
        }

        List<DocumentStrategyStepDraft> parentSteps = buildDraftSteps(
            DocumentStrategyPipelineTypeEnum.PARENT, parentStrategyTypes, parentReasonMap
        );
        List<DocumentStrategyStepDraft> childSteps = buildDraftSteps(
            DocumentStrategyPipelineTypeEnum.CHILD, childStrategyTypes, childReasonMap
        );

        String strategySnapshot = buildCombinedStrategySnapshot(parentSteps, childSteps);
        return new DocumentStrategyPlanDraft(strategySnapshot, String.join("；", reasonList), parentSteps, childSteps);
    }

    /**
     * 规范化策略步骤。
     *
     * 当用户调整了系统推荐的策略时，需要：
     *   1. 去重（移除重复的策略类型）
     *   2. 校验策略类型有效性
     *   3. 保留基础方案中已有的步骤（标记为 USER_KEEP），新增的标记为 USER_ADD
     *
     * @param basePlan                 基础方案
     * @param baseSteps                基础方案的步骤列表
     * @param requestParentStrategyTypes 用户请求的父块策略类型列表
     * @param requestChildStrategyTypes  用户请求的子块策略类型列表
     * @param documentId               文档ID
     * @return 规范化后的步骤列表
     */
    @Override
    public List<KnowHubDocumentStrategyStep> normalizeSteps(KnowHubDocumentStrategyPlan basePlan,
                                                               List<KnowHubDocumentStrategyStep> baseSteps,
                                                               List<Integer> requestParentStrategyTypes,
                                                               List<Integer> requestChildStrategyTypes,
                                                               Long documentId) {

        // 去重并校验策略类型有效性
        List<Integer> normalizedParentTypes = normalizePipelineTypes(requestParentStrategyTypes);
        List<Integer> normalizedChildTypes = normalizePipelineTypes(requestChildStrategyTypes);

        // 构建基础方案步骤的索引（流水线类型 -> 策略类型 -> 步骤）
        Map<String, Map<Integer, KnowHubDocumentStrategyStep>> baseStepMap = new LinkedHashMap<>();
        for (KnowHubDocumentStrategyStep baseStep : baseSteps) {
            String pipelineType = baseStep.getPipelineType();
            if (StrUtil.isBlank(pipelineType)) {
                pipelineType = DocumentStrategyPipelineTypeEnum.CHILD.getCode();
            }
            baseStepMap.computeIfAbsent(pipelineType, ignored -> new LinkedHashMap<>())
                .put(baseStep.getStrategyType(), baseStep);
        }

        // 分别规范化父块和子块流水线的步骤
        List<KnowHubDocumentStrategyStep> normalizedStepList = new ArrayList<>();
        normalizedStepList.addAll(buildNormalizedSteps(
            DocumentStrategyPipelineTypeEnum.PARENT,
            normalizedParentTypes,
            baseStepMap.getOrDefault(DocumentStrategyPipelineTypeEnum.PARENT.getCode(), Map.of()),
            documentId
        ));
        normalizedStepList.addAll(buildNormalizedSteps(
            DocumentStrategyPipelineTypeEnum.CHILD,
            normalizedChildTypes,
            baseStepMap.getOrDefault(DocumentStrategyPipelineTypeEnum.CHILD.getCode(), Map.of()),
            documentId
        ));
        return normalizedStepList;
    }

    /**
     * 构建 Parent-Child 块结构。
     *
     * 流程：
     *   1. 加载文档的结构节点（标题、章节等）
     *   2. 按父块策略流水线生成父块种子列表
     *   3. 对每个父块种子，按子块策略流水线生成子块列表
     *   4. 如果子块为空，将父块自身作为唯一的子块
     *   5. 清理去重后返回
     *
     * @param document   文档记录
     * @param plan       策略方案
     * @param steps      策略步骤列表
     * @param parsedText 解析后的纯文本
     * @return ParentBlockCandidate 列表
     */
    @Override
    public List<ParentBlockCandidate> buildParentBlocks(KnowHubDocument document,
                                                        KnowHubDocumentStrategyPlan plan,
                                                        List<KnowHubDocumentStrategyStep> steps,
                                                        String parsedText) {
        // 分离父块和子块流水线的步骤
        List<KnowHubDocumentStrategyStep> parentSteps = sortPipelineSteps(steps, DocumentStrategyPipelineTypeEnum.PARENT);
        List<KnowHubDocumentStrategyStep> childSteps = sortPipelineSteps(steps, DocumentStrategyPipelineTypeEnum.CHILD);
        if (parentSteps.isEmpty()) {
            throw new IllegalStateException("当前方案缺少父块流水线，无法生成 Parent-Child 结构。");
        }
        if (childSteps.isEmpty()) {
            throw new IllegalStateException("当前方案缺少子块流水线，无法生成 Parent-Child 结构。");
        }

        // 加载文档的结构节点
        List<KnowHubDocumentStructureNode> structureNodes = structureNodeService.listDocumentNodes(
            document == null ? null : document.getId(),
            document == null ? null : document.getLastParseTaskId()
        );
        // 生成父块种子列表
        List<ChunkCandidate> parentSeedList = buildParentSeedList(parsedText, parentSteps, structureNodes);
        List<ParentBlockCandidate> parentBlockList = new ArrayList<>();
        for (ChunkCandidate parentSeed : cleanupChunkList(parentSeedList)) {
            if (parentSeed == null || StrUtil.isBlank(parentSeed.getText())) {
                continue;
            }
            // 为每个父块生成子块列表
            List<ChunkCandidate> childSeedList = buildChildSeedList(parentSeed, childSteps, structureNodes);
            List<ChunkCandidate> finalChildren = cleanupChunkList(childSeedList);
            // 如果子块为空，将父块自身作为唯一的子块
            if (finalChildren.isEmpty()) {
                finalChildren = List.of(cloneChunkCandidate(parentSeed, parentSeed.getText().trim()));
            }

            parentBlockList.add(new ParentBlockCandidate(
                parentSeed.getSectionPath(),
                parentSeed.getStructureNodeId(),
                parentSeed.getStructureNodeType(),
                parentSeed.getCanonicalPath(),
                parentSeed.getItemIndex(),
                parentSeed.getText().trim(),
                parentSeed.getSourceType(),
                finalChildren
            ));
        }
        return cleanupParentBlockList(parentBlockList);
    }

    /**
     * 构建父块种子列表。
     * 如果父块策略包含结构切块且有结构节点，先用结构切块生成种子，
     * 再将剩余策略步骤应用到种子上。
     */
    private List<ChunkCandidate> buildParentSeedList(String parsedText,
                                                     List<KnowHubDocumentStrategyStep> parentSteps,
                                                     List<KnowHubDocumentStructureNode> structureNodes) {
        if (containsStructureStep(parentSteps) && structureNodes != null && !structureNodes.isEmpty()) {
            List<ChunkCandidate> structureSeeds = buildStructureParentSeeds(structureNodes);
            if (structureSeeds.isEmpty()) {
                // 结构切块没有产生种子，用原始文本走完整流水线
                return executePipeline(
                    List.of(new ChunkCandidate("", parsedText, DocumentChunkSourceTypeEnum.ORIGINAL.getCode())),
                    parentSteps,
                    DocumentStrategyPipelineTypeEnum.PARENT
                );
            }
            // 移除结构步骤，将剩余步骤应用到结构种子上
            List<KnowHubDocumentStrategyStep> remainingSteps = stripStructureSteps(parentSteps);
            if (remainingSteps.isEmpty()) {
                return structureSeeds;
            }
            return executePipeline(structureSeeds, remainingSteps, DocumentStrategyPipelineTypeEnum.PARENT);
        }
        // 没有结构步骤，用原始文本走完整流水线
        return executePipeline(
            List.of(new ChunkCandidate("", parsedText, DocumentChunkSourceTypeEnum.ORIGINAL.getCode())),
            parentSteps,
            DocumentStrategyPipelineTypeEnum.PARENT
        );
    }

    /**
     * 构建子块种子列表。
     * 如果子块策略包含结构切块且父块有结构节点，用子节点作为种子。
     */
    private List<ChunkCandidate> buildChildSeedList(ChunkCandidate parentSeed,
                                                    List<KnowHubDocumentStrategyStep> childSteps,
                                                    List<KnowHubDocumentStructureNode> structureNodes) {
        if (containsStructureStep(childSteps)
            && parentSeed != null
            && parentSeed.getStructureNodeId() != null
            && structureNodes != null
            && !structureNodes.isEmpty()) {
            List<ChunkCandidate> structureSeeds = buildStructureChildSeeds(parentSeed, structureNodes);
            List<KnowHubDocumentStrategyStep> remainingSteps = stripStructureSteps(childSteps);
            if (remainingSteps.isEmpty()) {
                return structureSeeds;
            }
            return executePipeline(structureSeeds, remainingSteps, DocumentStrategyPipelineTypeEnum.CHILD);
        }
        // 没有结构步骤，用父块文本走完整子块流水线
        return executePipeline(
            List.of(cloneChunkCandidate(parentSeed, parentSeed.getText())),
            childSteps,
            DocumentStrategyPipelineTypeEnum.CHILD
        );
    }

    /** 判断步骤列表中是否包含结构切块策略 */
    private boolean containsStructureStep(List<KnowHubDocumentStrategyStep> steps) {
        return steps != null && steps.stream().anyMatch(step -> DocumentStrategyTypeEnum.STRUCTURE.getCode().equals(step.getStrategyType()));
    }

    /** 从步骤列表中移除结构切块策略 */
    private List<KnowHubDocumentStrategyStep> stripStructureSteps(List<KnowHubDocumentStrategyStep> steps) {
        return steps == null ? List.of() : steps.stream()
            .filter(step -> !DocumentStrategyTypeEnum.STRUCTURE.getCode().equals(step.getStrategyType()))
            .toList();
    }

    /**
     * 从结构节点构建父块种子。
     * 只选择有实际内容的章节节点（排除仅有标题没有正文的节点）。
     */
    private List<ChunkCandidate> buildStructureParentSeeds(List<KnowHubDocumentStructureNode> structureNodes) {
        // 记录每个节点是否有子章节
        Map<Long, Boolean> parentHasChildSection = new LinkedHashMap<>();
        for (KnowHubDocumentStructureNode node : structureNodes) {
            if (node == null || node.getParentNodeId() == null) {
                continue;
            }
            if (DocumentStructureNodeTypeEnum.SECTION.getCode().equals(node.getNodeType())) {
                parentHasChildSection.put(node.getParentNodeId(), true);
            }
        }
        List<ChunkCandidate> seeds = new ArrayList<>();
        for (KnowHubDocumentStructureNode node : structureNodes) {
            if (node == null || !DocumentStructureNodeTypeEnum.SECTION.getCode().equals(node.getNodeType())) {
                continue;
            }
            // 只选择有实际内容的章节（不是仅有标题的容器节点）
            if (!isContentBearingSection(node, parentHasChildSection.getOrDefault(node.getId(), false))) {
                continue;
            }
            seeds.add(toChunkCandidate(node));
        }
        return seeds;
    }

    /**
     * 从结构节点构建子块种子。
     * 将父节点的直接子节点（子章节、步骤、列表项）作为子块种子。
     */
    private List<ChunkCandidate> buildStructureChildSeeds(ChunkCandidate parentSeed,
                                                          List<KnowHubDocumentStructureNode> structureNodes) {
        // 按父节点ID分组
        Map<Long, List<KnowHubDocumentStructureNode>> childrenByParent = new LinkedHashMap<>();
        for (KnowHubDocumentStructureNode node : structureNodes) {
            if (node == null || node.getParentNodeId() == null) {
                continue;
            }
            childrenByParent.computeIfAbsent(node.getParentNodeId(), ignored -> new ArrayList<>()).add(node);
        }
        List<ChunkCandidate> seeds = new ArrayList<>();
        for (KnowHubDocumentStructureNode child : childrenByParent.getOrDefault(parentSeed.getStructureNodeId(), List.of())) {
            if (child == null || StrUtil.isBlank(child.getContentText())) {
                continue;
            }
            DocumentStructureNodeTypeEnum nodeType = DocumentStructureNodeTypeEnum.getRc(child.getNodeType());
            if (nodeType == DocumentStructureNodeTypeEnum.SECTION
                || nodeType == DocumentStructureNodeTypeEnum.STEP
                || nodeType == DocumentStructureNodeTypeEnum.LIST_ITEM) {
                seeds.add(toChunkCandidate(child));
            }
        }
        if (!seeds.isEmpty()) {
            return seeds;
        }
        // 没有子节点时，将父块自身作为种子
        return List.of(cloneChunkCandidate(parentSeed, parentSeed.getText()));
    }

    /**
     * 判断章节节点是否有实际内容。
     * 有子章节的容器节点如果内容仅等于标题文本，则视为无实际内容。
     */
    private boolean isContentBearingSection(KnowHubDocumentStructureNode node, boolean hasChildSection) {
        if (node == null || StrUtil.isBlank(node.getContentText())) {
            return false;
        }
        String content = node.getContentText().trim();
        if (!hasChildSection) {
            return true;
        }
        String headingText = StrUtil.blankToDefault(node.getAnchorText(), node.getTitle()).trim();
        if (content.equals(headingText)) {
            return false;
        }
        return content.length() > headingText.length() + 16 || content.contains("\n");
    }

    /** 将结构节点转为 ChunkCandidate */
    private ChunkCandidate toChunkCandidate(KnowHubDocumentStructureNode node) {
        return new ChunkCandidate(
            node.getSectionPath(),
            node.getId(),
            node.getNodeType(),
            StrUtil.blankToDefault(node.getCanonicalPath(), ""),
            node.getItemIndex(),
            node.getContentText(),
            DocumentChunkSourceTypeEnum.ORIGINAL.getCode()
        );
    }

    /**
     * 构建策略步骤草案列表。
     */
    private List<DocumentStrategyStepDraft> buildDraftSteps(DocumentStrategyPipelineTypeEnum pipelineType,
                                                            List<Integer> strategyTypes,
                                                            Map<Integer, String> reasonMap) {
        List<DocumentStrategyStepDraft> draftList = new ArrayList<>();
        for (int index = 0; index < strategyTypes.size(); index++) {
            Integer strategyType = strategyTypes.get(index);
            draftList.add(new DocumentStrategyStepDraft(
                pipelineType.getCode(),
                strategyType,
                resolveRole(index, strategyType),
                DocumentStrategySourceTypeEnum.SYSTEM_RECOMMEND.getCode(),
                reasonMap.getOrDefault(strategyType, "系统为当前流水线生成的推荐步骤。")
            ));
        }
        return draftList;
    }

    /**
     * 规范化流水线策略类型列表。
     * 去重并校验每个类型是否有效。
     */
    private List<Integer> normalizePipelineTypes(List<Integer> requestStrategyTypes) {
        LinkedHashSet<Integer> requestTypeSet = new LinkedHashSet<>();
        for (Integer strategyType : requestStrategyTypes == null ? List.<Integer>of() : requestStrategyTypes) {
            if (DocumentStrategyTypeEnum.getRc(strategyType) != null) {
                requestTypeSet.add(strategyType);
            }
        }
        return new ArrayList<>(requestTypeSet);
    }

    /**
     * 构建规范化后的步骤列表。
     * 对于基础方案中已有的步骤标记为 USER_KEEP，新增的标记为 USER_ADD。
     */
    private List<KnowHubDocumentStrategyStep> buildNormalizedSteps(DocumentStrategyPipelineTypeEnum pipelineType,
                                                                      List<Integer> normalizedTypes,
                                                                      Map<Integer, KnowHubDocumentStrategyStep> baseStepMap,
                                                                      Long documentId) {
        List<KnowHubDocumentStrategyStep> normalizedStepList = new ArrayList<>();
        for (int index = 0; index < normalizedTypes.size(); index++) {
            Integer strategyType = normalizedTypes.get(index);
            KnowHubDocumentStrategyStep baseStep = baseStepMap.get(strategyType);
            KnowHubDocumentStrategyStep step = new KnowHubDocumentStrategyStep();
            step.setDocumentId(documentId);
            step.setPipelineType(pipelineType.getCode());
            step.setStepNo(index + 1);
            step.setStrategyType(strategyType);
            step.setStrategyRole(resolveRole(index, strategyType));
            step.setSourceType(baseStep == null
                ? DocumentStrategySourceTypeEnum.USER_ADD.getCode()
                : DocumentStrategySourceTypeEnum.USER_KEEP.getCode());
            step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
            step.setRecommendReason(baseStep == null ? "用户手动追加该策略。" : baseStep.getRecommendReason());
            normalizedStepList.add(step);
        }
        return normalizedStepList;
    }

    /**
     * 按流水线类型过滤并排序步骤。
     */
    private List<KnowHubDocumentStrategyStep> sortPipelineSteps(List<KnowHubDocumentStrategyStep> steps,
                                                                   DocumentStrategyPipelineTypeEnum pipelineType) {
        return steps.stream()
            .filter(step -> pipelineType.getCode().equalsIgnoreCase(
                StrUtil.blankToDefault(step.getPipelineType(), DocumentStrategyPipelineTypeEnum.CHILD.getCode())
            ))
            .sorted(Comparator.comparingInt(KnowHubDocumentStrategyStep::getStepNo))
            .toList();
    }

    /**
     * 执行切块流水线。
     * 按步骤顺序依次执行每种切块策略，前一步的输出是后一步的输入。
     *
     * @param sourceList   输入的文本块列表
     * @param orderedSteps 按顺序排列的策略步骤
     * @param pipelineType 流水线类型（PARENT 或 CHILD）
     * @return 切块结果列表
     */
    private List<ChunkCandidate> executePipeline(List<ChunkCandidate> sourceList,
                                                 List<KnowHubDocumentStrategyStep> orderedSteps,
                                                 DocumentStrategyPipelineTypeEnum pipelineType) {
        List<ChunkCandidate> currentChunks = cleanupChunkList(sourceList);
        for (KnowHubDocumentStrategyStep step : orderedSteps) {
            DocumentStrategyTypeEnum strategyType = DocumentStrategyTypeEnum.getRc(step.getStrategyType());
            if (strategyType == null) {
                continue;
            }
            // 根据策略类型分发到对应的切块方法
            currentChunks = switch (strategyType) {
                case STRUCTURE -> applyStructureChunking(currentChunks, pipelineType);
                case RECURSIVE -> applyRecursiveChunking(currentChunks, pipelineType);
                case SEMANTIC -> applySemanticChunking(currentChunks, pipelineType);
                case LLM -> applyLlmChunking(currentChunks, pipelineType);
            };
            currentChunks = cleanupChunkList(currentChunks);
        }
        return cleanupChunkList(currentChunks);
    }

    /** 构建组合策略快照字符串 */
    private String buildCombinedStrategySnapshot(List<DocumentStrategyStepDraft> parentSteps,
                                                 List<DocumentStrategyStepDraft> childSteps) {
        String parentSnapshot = buildPipelineSnapshot(parentSteps.stream()
            .map(DocumentStrategyStepDraft::getStrategyType)
            .toList());
        String childSnapshot = buildPipelineSnapshot(childSteps.stream()
            .map(DocumentStrategyStepDraft::getStrategyType)
            .toList());
        return "PARENT:" + parentSnapshot + ";CHILD:" + childSnapshot;
    }

    /** 构建单个流水线的策略快照（逗号分隔的策略类型编码） */
    private String buildPipelineSnapshot(List<Integer> strategyTypes) {
        return strategyTypes.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }

    /**
     * 判断是否应该使用结构切块。
     * 条件：文件类型适合（PDF/DOC/MD/HTML）且结构化程度达到 MEDIUM 或标题数 >= 2。
     */
    private boolean shouldUseStructure(DocumentFileTypeEnum fileType, DocumentAnalysisResult analysisResult) {

        boolean suitableType = fileType == DocumentFileTypeEnum.PDF
            || fileType == DocumentFileTypeEnum.DOC
            || fileType == DocumentFileTypeEnum.DOCX
            || fileType == DocumentFileTypeEnum.MD
            || fileType == DocumentFileTypeEnum.HTML;
        return suitableType && (analysisResult.getStructureLevel() >= DocumentStructureLevelEnum.MEDIUM.getCode()
            || analysisResult.getHeadingCount() >= 2);
    }

    /**
     * 判断是否应该使用递归切块。
     * 条件：文档字符数或最大段落长度超过递归切块的最大字符数。
     */
    private boolean shouldUseRecursive(DocumentAnalysisResult analysisResult) {

        return analysisResult.getCharCount() >= properties.getChunk().getRecursiveMaxChars()
            || analysisResult.getMaxParagraphLength() >= properties.getChunk().getRecursiveMaxChars();
    }

    /**
     * 判断是否应该使用语义切块。
     * 条件：文档足够长、段落数 >= 3、内容质量 >= MEDIUM。
     */
    private boolean shouldUseSemantic(DocumentAnalysisResult analysisResult) {

        return analysisResult.getCharCount() >= properties.getChunk().getSemanticMinChars()
            && analysisResult.getParagraphCount() >= 3
            && analysisResult.getContentQualityLevel() >= DocumentContentQualityLevelEnum.MEDIUM.getCode();
    }

    /**
     * 判断是否应该使用大模型切块。
     * 条件：配置允许、内容质量为 LOW、文档足够长。
     */
    private boolean shouldUseLlm(DocumentAnalysisResult analysisResult) {

        return Boolean.TRUE.equals(properties.getChunk().getRecommendLlmWhenLowQuality())
            && analysisResult.getContentQualityLevel().equals(DocumentContentQualityLevelEnum.LOW.getCode())
            && analysisResult.getCharCount() >= properties.getChunk().getSemanticMinChars();
    }

    /** 对单个文本应用结构切块（简化入口） */
    private List<ChunkCandidate> applyStructureChunking(String parsedText) {
        return applyStructureChunking(
            parsedText,
            DocumentStrategyPipelineTypeEnum.PARENT,
            "",
            DocumentChunkSourceTypeEnum.ORIGINAL.getCode()
        );
    }

    /**
     * 对 ChunkCandidate 列表应用结构切块。
     * 将每个候选块的文本按标题行切分。
     */
    private List<ChunkCandidate> applyStructureChunking(List<ChunkCandidate> sourceList,
                                                        DocumentStrategyPipelineTypeEnum pipelineType) {
        List<ChunkCandidate> resultList = new ArrayList<>();
        for (ChunkCandidate candidate : sourceList) {
            if (candidate == null || StrUtil.isBlank(candidate.getText())) {
                continue;
            }
            resultList.addAll(applyStructureChunking(
                candidate.getText(),
                pipelineType,
                candidate.getSectionPath(),
                candidate.getSourceType()
            ));
        }
        return resultList;
    }

    /**
     * 结构切块核心实现。
     * 逐行扫描文本，遇到标题行时切分，同时维护标题层级栈构建章节路径。
     * 如果没有识别到任何标题，降级为递归切块。
     */
    private List<ChunkCandidate> applyStructureChunking(String parsedText,
                                                        DocumentStrategyPipelineTypeEnum pipelineType,
                                                        String baseSectionPath,
                                                        Integer sourceType) {
        List<ChunkCandidate> candidateList = new ArrayList<>();
        Deque<String> headingStack = new ArrayDeque<>();
        StringBuilder currentChunk = new StringBuilder();
        String currentSectionPath = StrUtil.blankToDefault(baseSectionPath, "");

        for (String line : parsedText.split("\n")) {
            String trimmed = line.trim();
            DocumentLineClassifier.LineClassification classification = documentLineClassifier.classify(trimmed);
            if (classification.isHeading()) {
                // 遇到标题，先将当前积累的文本 flush 为一个 chunk
                flushChunk(candidateList, currentSectionPath, sourceType, currentChunk);

                // 维护标题层级栈（移除同级或更低级别的标题）
                while (headingStack.size() >= classification.level()) {
                    headingStack.removeLast();
                }
                headingStack.addLast(classification.title());
                currentSectionPath = composeSectionPath(baseSectionPath, String.join(" > ", headingStack));
                currentChunk.append(trimmed).append('\n');
                continue;
            }

            currentChunk.append(line).append('\n');
        }
        // flush 最后一个 chunk
        flushChunk(candidateList, currentSectionPath, sourceType, currentChunk);

        if (candidateList.isEmpty()) {
            // 没有识别到标题，降级为递归切块
            return applyRecursiveChunking(
                List.of(new ChunkCandidate(baseSectionPath, parsedText, sourceType)),
                pipelineType
            );
        }
        return candidateList;
    }

    /** 对 ChunkCandidate 列表应用递归切块（使用默认 CHILD 流水线） */
    private List<ChunkCandidate> applyRecursiveChunking(List<ChunkCandidate> sourceList) {
        return applyRecursiveChunking(sourceList, DocumentStrategyPipelineTypeEnum.CHILD);
    }

    /**
     * 递归切块。
     * 将每个候选块的文本按最大字符数递归切分。
     * 切分优先级：段落 > 行 > 句子 > 固定窗口。
     */
    private List<ChunkCandidate> applyRecursiveChunking(List<ChunkCandidate> sourceList,
                                                        DocumentStrategyPipelineTypeEnum pipelineType) {
        List<ChunkCandidate> resultList = new ArrayList<>();
        int maxChars = resolveRecursiveMaxChars(pipelineType);
        int overlapChars = resolveRecursiveOverlap(maxChars, pipelineType);
        for (ChunkCandidate candidate : sourceList) {

            List<String> splitTextList = recursiveSplit(candidate.getText(), maxChars, overlapChars);
            for (String splitText : splitTextList) {
                resultList.add(cloneChunkCandidate(candidate, splitText));
            }
        }
        return resultList;
    }

    /** 对 ChunkCandidate 列表应用语义切块（使用默认 CHILD 流水线） */
    private List<ChunkCandidate> applySemanticChunking(List<ChunkCandidate> sourceList) {
        return applySemanticChunking(sourceList, DocumentStrategyPipelineTypeEnum.CHILD);
    }

    /**
     * 语义切块。
     * 将文本按句子分割，计算相邻句子间的 Jaccard 相似度，
     * 在相似度低于阈值且达到最小长度时切分。
     */
    private List<ChunkCandidate> applySemanticChunking(List<ChunkCandidate> sourceList,
                                                       DocumentStrategyPipelineTypeEnum pipelineType) {
        List<ChunkCandidate> resultList = new ArrayList<>();
        int semanticMinChars = resolveSemanticMinChars(pipelineType);
        for (ChunkCandidate candidate : sourceList) {
            if (StrUtil.isBlank(candidate.getText())
                || candidate.getText().length() <= semanticMinChars) {
                // 文本太短，不切分
                resultList.add(candidate);
                continue;
            }

            resultList.addAll(semanticSplit(candidate, pipelineType));
        }
        return resultList;
    }

    /** 对 ChunkCandidate 列表应用大模型切块（使用默认 CHILD 流水线） */
    private List<ChunkCandidate> applyLlmChunking(List<ChunkCandidate> sourceList) {
        return applyLlmChunking(sourceList, DocumentStrategyPipelineTypeEnum.CHILD);
    }

    /**
     * 大模型切块。
     * 调用大模型（通过 ChatClient）判断文本的最佳切分点。
     * 如果大模型调用失败或返回空结果，降级为语义切块。
     */
    private List<ChunkCandidate> applyLlmChunking(List<ChunkCandidate> sourceList,
                                                  DocumentStrategyPipelineTypeEnum pipelineType) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (!Boolean.TRUE.equals(properties.getChunk().getLlmEnabled()) || chatModel == null) {
            // 大模型不可用，降级为语义切块
            return applySemanticChunking(sourceList, pipelineType);
        }

        List<ChunkCandidate> resultList = new ArrayList<>();
        for (ChunkCandidate candidate : sourceList) {
            if (StrUtil.isBlank(candidate.getText())) {
                continue;
            }

            // 如果文本超过大模型切块的最大长度，先用递归切块预处理
            int llmMaxChars = resolveLlmMaxChars(pipelineType);
            List<String> sourceTextList = candidate.getText().length() > llmMaxChars
                ? recursiveSplit(candidate.getText(), llmMaxChars, 0)
                : List.of(candidate.getText());

            for (String sourceText : sourceTextList) {
                List<String> llmChunkList = llmSplit(chatModel, sourceText);
                if (llmChunkList.isEmpty()) {
                    // 大模型切块失败，降级为语义切块
                    resultList.addAll(semanticSplit(cloneChunkCandidate(candidate, sourceText), pipelineType));
                    continue;
                }
                for (String llmChunk : llmChunkList) {
                    resultList.add(cloneChunkCandidate(candidate, llmChunk));
                }
            }
        }
        return resultList;
    }

    /**
     * 语义切块核心实现。
     * 将文本按句子分割，计算相邻句子间的 Jaccard 相似度，
     * 在相似度低于阈值且当前块达到最小长度时切分。
     *
     * Jaccard 相似度 = |A ∩ B| / |A ∪ B|
     * 其中 A、B 分别是两个句子的 token 集合（英文单词 + 中文字符）
     */
    private List<ChunkCandidate> semanticSplit(ChunkCandidate candidate,
                                               DocumentStrategyPipelineTypeEnum pipelineType) {
        List<ChunkCandidate> resultList = new ArrayList<>();
        List<String> sentenceList = splitSentences(candidate.getText());
        if (sentenceList.size() <= 1) {
            // 只有一个句子，不切分
            resultList.add(candidate);
            return resultList;
        }

        StringBuilder currentChunk = new StringBuilder();
        Set<String> currentTokenSet = new LinkedHashSet<>();
        int semanticMinChars = resolveSemanticMinChars(pipelineType);
        int semanticMaxChars = resolveSemanticMaxChars(pipelineType);

        for (String sentence : sentenceList) {

            Set<String> sentenceTokenSet = extractTokens(sentence);

            boolean exceedMaxChars = currentChunk.length() + sentence.length() > semanticMaxChars;
            double similarity = currentTokenSet.isEmpty() ? 1D : jaccard(currentTokenSet, sentenceTokenSet);
            boolean semanticBreak = currentChunk.length() >= semanticMinChars
                && similarity < properties.getChunk().getSemanticSimilarityThreshold();

            if (currentChunk.length() > 0 && (exceedMaxChars || semanticBreak)) {
                // 超过最大长度或检测到语义边界，切分
                resultList.add(cloneChunkCandidate(candidate, currentChunk.toString().trim()));
                currentChunk.setLength(0);
                currentTokenSet.clear();
            }

            currentChunk.append(sentence);
            currentTokenSet.addAll(sentenceTokenSet);
        }

        if (currentChunk.length() > 0) {
            resultList.add(cloneChunkCandidate(candidate, currentChunk.toString().trim()));
        }
        return resultList;
    }

    /**
     * 递归切块核心实现。
     * 切分优先级：段落（双换行）> 行（单换行）> 句子 > 固定窗口。
     * 每一层如果切分结果仍然超过最大长度，会递归进入下一层。
     */
    private List<String> recursiveSplit(String text, int maxChars, int overlapChars) {
        String trimmed = text == null ? "" : text.trim();
        if (StrUtil.isBlank(trimmed)) {
            return List.of();
        }
        if (trimmed.length() <= maxChars) {
            // 文本不超过最大长度，直接返回
            return List.of(trimmed);
        }

        // 尝试按段落（双换行）分割
        List<String> paragraphList = splitByRegex(trimmed, "\\n\\s*\\n");
        if (paragraphList.size() > 1) {
            return mergeAndSplit(paragraphList, maxChars, overlapChars);
        }

        // 尝试按行（单换行）分割
        List<String> lineList = splitByRegex(trimmed, "\\n");
        if (lineList.size() > 1) {
            return mergeAndSplit(lineList, maxChars, overlapChars);
        }

        // 尝试按句子分割
        List<String> sentenceList = splitSentences(trimmed);
        if (sentenceList.size() > 1) {
            return mergeAndSplit(sentenceList, maxChars, overlapChars);
        }

        // 最后使用固定窗口切分
        List<String> fixedWindowList = new ArrayList<>();
        int start = 0;
        int step = Math.max(1, maxChars - overlapChars);
        while (start < trimmed.length()) {

            int end = Math.min(trimmed.length(), start + maxChars);
            fixedWindowList.add(trimmed.substring(start, end).trim());
            if (end >= trimmed.length()) {
                break;
            }

            start += step;
        }
        return fixedWindowList;
    }

    /**
     * 合并和切分文本段落。
     * 将小段落合并到不超过最大长度，超过的递归切分。
     */
    private List<String> mergeAndSplit(List<String> segmentList, int maxChars, int overlapChars) {
        List<String> rawResultList = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String segment : segmentList) {
            String trimmed = segment.trim();
            if (StrUtil.isBlank(trimmed)) {
                continue;
            }

            if (trimmed.length() > maxChars) {
                // 单个段落超过最大长度，先 flush 当前积累的文本，再递归切分
                if (current.length() > 0) {
                    rawResultList.add(current.toString().trim());
                    current.setLength(0);
                }
                rawResultList.addAll(recursiveSplit(trimmed, maxChars, overlapChars));
                continue;
            }

            if (current.length() + trimmed.length() + 1 > maxChars) {
                // 合并后超过最大长度，先 flush 当前积累的文本
                rawResultList.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(trimmed).append('\n');
        }

        if (current.length() > 0) {
            rawResultList.add(current.toString().trim());
        }
        // 应用重叠（在相邻 chunk 之间添加前一个 chunk 的尾部文本）
        return applyOverlap(rawResultList, maxChars, overlapChars);
    }

    /**
     * 在相邻 chunk 之间应用重叠。
     * 将前一个 chunk 的尾部文本添加到后一个 chunk 的开头，
     * 以保证检索时不会因为切分点恰好在关键信息中间而丢失上下文。
     */
    private List<String> applyOverlap(List<String> rawChunkList, int maxChars, int overlapChars) {
        if (rawChunkList.isEmpty() || overlapChars <= 0) {
            return rawChunkList;
        }

        List<String> overlappedChunkList = new ArrayList<>(rawChunkList.size());
        for (int index = 0; index < rawChunkList.size(); index++) {
            String current = rawChunkList.get(index);
            if (StrUtil.isBlank(current)) {
                continue;
            }
            if (index == 0) {
                // 第一个 chunk 不需要重叠
                overlappedChunkList.add(current);
                continue;
            }

            String previous = rawChunkList.get(index - 1);
            String overlapPrefix = buildOverlapPrefix(previous, current, maxChars, overlapChars);
            if (StrUtil.isNotBlank(overlapPrefix)) {
                overlappedChunkList.add(overlapPrefix + "\n" + current);
            }
            else {
                overlappedChunkList.add(current);
            }
        }
        return overlappedChunkList;
    }

    /**
     * 构建重叠前缀。
     * 从上一个 chunk 的尾部截取不超过 overlapChars 长度的文本。
     */
    private String buildOverlapPrefix(String previous, String current, int maxChars, int overlapChars) {
        if (StrUtil.isBlank(previous) || StrUtil.isBlank(current)) {
            return "";
        }

        int allowedChars = Math.min(overlapChars, Math.max(0, maxChars - current.length() - 1));
        if (allowedChars <= 0) {
            return "";
        }

        String suffix = previous.length() <= allowedChars
            ? previous
            : previous.substring(previous.length() - allowedChars);
        return suffix.trim();
    }

    /** 计算递归切块的重叠字符数（使用默认 CHILD 流水线） */
    private int resolveRecursiveOverlap(int maxChars) {
        return resolveRecursiveOverlap(maxChars, DocumentStrategyPipelineTypeEnum.CHILD);
    }

    /**
     * 计算递归切块的重叠字符数。
     * 父块使用固定的重叠值，子块使用配置值。
     */
    private int resolveRecursiveOverlap(int maxChars, DocumentStrategyPipelineTypeEnum pipelineType) {
        if (pipelineType == DocumentStrategyPipelineTypeEnum.PARENT) {
            return Math.min(PARENT_BLOCK_OVERLAP_CHARS, Math.max(0, maxChars - 1));
        }
        Integer configuredOverlap = properties.getChunk().getRecursiveOverlapChars();
        if (configuredOverlap == null || configuredOverlap <= 0) {
            return 0;
        }

        return Math.min(configuredOverlap, Math.max(0, maxChars - 1));
    }

    /**
     * 解析递归切块的最大字符数。
     * 父块使用较大的固定值（2200），子块使用配置值。
     */
    private int resolveRecursiveMaxChars(DocumentStrategyPipelineTypeEnum pipelineType) {
        return pipelineType == DocumentStrategyPipelineTypeEnum.PARENT
            ? PARENT_BLOCK_MAX_CHARS
            : properties.getChunk().getRecursiveMaxChars();
    }

    /**
     * 解析语义切块的最大字符数。
     * 父块使用较大的值。
     */
    private int resolveSemanticMaxChars(DocumentStrategyPipelineTypeEnum pipelineType) {
        return pipelineType == DocumentStrategyPipelineTypeEnum.PARENT
            ? Math.max(PARENT_SEMANTIC_MAX_CHARS, properties.getChunk().getSemanticMaxChars())
            : properties.getChunk().getSemanticMaxChars();
    }

    /**
     * 解析语义切块的最小字符数。
     * 父块使用较大的值。
     */
    private int resolveSemanticMinChars(DocumentStrategyPipelineTypeEnum pipelineType) {
        return pipelineType == DocumentStrategyPipelineTypeEnum.PARENT
            ? Math.max(PARENT_SEMANTIC_MIN_CHARS, properties.getChunk().getSemanticMinChars())
            : properties.getChunk().getSemanticMinChars();
    }

    /**
     * 解析大模型切块的最大字符数。
     * 父块使用较大的值。
     */
    private int resolveLlmMaxChars(DocumentStrategyPipelineTypeEnum pipelineType) {
        return pipelineType == DocumentStrategyPipelineTypeEnum.PARENT
            ? Math.max(properties.getChunk().getLlmMaxChars(), PARENT_BLOCK_MAX_CHARS)
            : properties.getChunk().getLlmMaxChars();
    }

    /** 按正则分割文本并去除空白 */
    private List<String> splitByRegex(String text, String regex) {
        return Arrays.stream(text.split(regex))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    /**
     * 按句子分割文本。
     * 使用中文标点（。！？!?；;）和英文句号（.）作为句子分隔符。
     */
    private List<String> splitSentences(String text) {
        return Arrays.stream(text.split("(?<=[。！？!?；;\\.])"))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    /**
     * 从文本中提取 token 集合（用于 Jaccard 相似度计算）。
     * 包括：英文单词（转小写）和中文字符（逐字）。
     */
    private Set<String> extractTokens(String text) {
        LinkedHashSet<String> tokenSet = new LinkedHashSet<>();
        Matcher matcher = ENGLISH_WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {

            tokenSet.add(matcher.group());
        }
        for (char current : text.toCharArray()) {

            if (String.valueOf(current).matches("[\\u4e00-\\u9fa5]")) {
                tokenSet.add(String.valueOf(current));
            }
        }

        return tokenSet;
    }

    /**
     * 计算两个集合的 Jaccard 相似度。
     * Jaccard(A, B) = |A ∩ B| / |A ∪ B|
     * 值域 [0, 1]，1 表示完全相同，0 表示完全不同。
     */
    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0D;
        }

        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return union.isEmpty() ? 0D : (double) intersection.size() / (double) union.size();
    }

    /**
     * 调用大模型进行智能切块。
     * 使用提示词模板将源文本发给大模型，要求返回 JSON 数组格式的切分结果。
     * 如果大模型调用失败或返回无效结果，返回空列表（调用方会降级为语义切块）。
     */
    private List<String> llmSplit(ChatModel chatModel, String sourceText) {
        String prompt = promptTemplateService.render(PromptTemplateNames.DOCUMENT_LLM_SPLIT, Map.of(
            "sourceText", StrUtil.blankToDefault(sourceText, "")
        ));

        try {

            String content = ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();

            if (StrUtil.isBlank(content)) {
                return List.of();
            }
            // 从大模型返回的内容中提取 JSON 数组
            String jsonArray = extractJsonArray(content);
            if (StrUtil.isBlank(jsonArray)) {
                return List.of();
            }

            List<String> resultList = objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {
            });
            return resultList.stream().filter(StrUtil::isNotBlank).map(String::trim).toList();
        }
        catch (Exception exception) {
            log.warn("大模型智能切块失败，回退到语义切块", exception);
            return List.of();
        }
    }

    /**
     * 从大模型返回的内容中提取 JSON 数组。
     * 大模型可能在 JSON 数组前后添加额外文本，这里只提取 [ 到 ] 的部分。
     */
    private String extractJsonArray(String content) {

        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }

    /**
     * 清理 ChunkCandidate 列表：去除空值、去重。
     * 去重 key = canonicalPath || itemIndex || normalizedText
     */
    private List<ChunkCandidate> cleanupChunkList(List<ChunkCandidate> sourceList) {
        Map<String, ChunkCandidate> uniqueMap = new LinkedHashMap<>();
        for (ChunkCandidate candidate : sourceList) {
            if (candidate == null || StrUtil.isBlank(candidate.getText())) {
                continue;
            }
            String normalizedText = candidate.getText().trim();

            String uniqueKey = StrUtil.blankToDefault(candidate.getCanonicalPath(), candidate.getSectionPath())
                + "||" + candidate.getItemIndex()
                + "||" + normalizedText;
            uniqueMap.putIfAbsent(uniqueKey, cloneChunkCandidate(candidate, normalizedText));
        }
        return new ArrayList<>(uniqueMap.values());
    }

    /**
     * 清理 ParentBlockCandidate 列表：去除空值、去重。
     */
    private List<ParentBlockCandidate> cleanupParentBlockList(List<ParentBlockCandidate> sourceList) {
        Map<String, ParentBlockCandidate> uniqueMap = new LinkedHashMap<>();
        for (ParentBlockCandidate candidate : sourceList) {
            if (candidate == null || StrUtil.isBlank(candidate.getText())) {
                continue;
            }
            String normalizedText = candidate.getText().trim();
            String uniqueKey = StrUtil.blankToDefault(candidate.getCanonicalPath(), candidate.getSectionPath())
                + "||" + candidate.getItemIndex()
                + "||" + normalizedText;
            uniqueMap.putIfAbsent(uniqueKey, cloneParentBlockCandidate(candidate, normalizedText,
                candidate.getChildChunks() == null ? List.of() : new ArrayList<>(candidate.getChildChunks())));
        }
        return new ArrayList<>(uniqueMap.values());
    }

    /**
     * 将当前积累的文本 flush 为一个 ChunkCandidate。
     * 用于结构切块中，遇到标题时将之前积累的文本保存为一个 chunk。
     */
    private void flushChunk(List<ChunkCandidate> candidateList,
                            String currentSectionPath,
                            Integer sourceType,
                            StringBuilder currentChunk) {
        String text = currentChunk.toString().trim();
        if (StrUtil.isNotBlank(text)) {

            candidateList.add(new ChunkCandidate(
                currentSectionPath,
                null,
                null,
                "",
                null,
                text,
                sourceType == null ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : sourceType
            ));
        }
        currentChunk.setLength(0);
    }

    /**
     * 克隆 ChunkCandidate 并替换文本内容。
     * 保留原有的 sectionPath、structureNodeId、canonicalPath 等元数据。
     */
    private ChunkCandidate cloneChunkCandidate(ChunkCandidate source, String text) {
        if (source == null) {
            return new ChunkCandidate("", text, DocumentChunkSourceTypeEnum.ORIGINAL.getCode());
        }
        return new ChunkCandidate(
            source.getSectionPath(),
            source.getStructureNodeId(),
            source.getStructureNodeType(),
            StrUtil.blankToDefault(source.getCanonicalPath(), ""),
            source.getItemIndex(),
            text,
            source.getSourceType()
        );
    }

    /**
     * 克隆 ParentBlockCandidate 并替换文本和子块列表。
     */
    private ParentBlockCandidate cloneParentBlockCandidate(ParentBlockCandidate source,
                                                           String text,
                                                           List<ChunkCandidate> childChunks) {
        if (source == null) {
            return new ParentBlockCandidate("", text, DocumentChunkSourceTypeEnum.ORIGINAL.getCode(), childChunks);
        }
        return new ParentBlockCandidate(
            source.getSectionPath(),
            source.getStructureNodeId(),
            source.getStructureNodeType(),
            StrUtil.blankToDefault(source.getCanonicalPath(), ""),
            source.getItemIndex(),
            text,
            source.getSourceType(),
            childChunks
        );
    }

    /**
     * 组合基础章节路径和当前章节路径。
     * 例如：baseSectionPath = "第一章", currentSectionPath = "概述 > 介绍"
     * 结果："第一章 > 概述 > 介绍"
     */
    private String composeSectionPath(String baseSectionPath, String currentSectionPath) {
        String normalizedBase = StrUtil.blankToDefault(baseSectionPath, "").trim();
        String normalizedCurrent = StrUtil.blankToDefault(currentSectionPath, "").trim();
        if (StrUtil.isBlank(normalizedBase)) {
            return normalizedCurrent;
        }
        if (StrUtil.isBlank(normalizedCurrent)) {
            return normalizedBase;
        }
        return normalizedBase + " > " + normalizedCurrent;
    }

    /**
     * 根据步骤位置和策略类型解析角色。
     * 第一个步骤 -> PRIMARY（主要）
     * RECURSIVE -> FALLBACK（兜底）
     * SEMANTIC -> OPTIMIZE（优化）
     * LLM -> ENHANCE（增强）
     */
    private Integer resolveRole(int index, Integer strategyType) {
        if (index == 0) {
            return DocumentStrategyRoleEnum.PRIMARY.getCode();
        }
        if (DocumentStrategyTypeEnum.RECURSIVE.getCode().equals(strategyType)) {
            return DocumentStrategyRoleEnum.FALLBACK.getCode();
        }
        if (DocumentStrategyTypeEnum.SEMANTIC.getCode().equals(strategyType)) {
            return DocumentStrategyRoleEnum.OPTIMIZE.getCode();
        }
        if (DocumentStrategyTypeEnum.LLM.getCode().equals(strategyType)) {
            return DocumentStrategyRoleEnum.ENHANCE.getCode();
        }
        return DocumentStrategyRoleEnum.OPTIMIZE.getCode();
    }

}
