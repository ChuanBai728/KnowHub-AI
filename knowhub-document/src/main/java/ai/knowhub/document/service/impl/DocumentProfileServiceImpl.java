package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentProfile;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.mapper.KnowHubDocumentMapper;
import ai.knowhub.document.mapper.KnowHubDocumentProfileMapper;
import ai.knowhub.document.mapper.KnowHubDocumentStructureNodeMapper;
import ai.knowhub.document.service.DocumentProfileService;
import ai.knowhub.document.service.DocumentStorageService;
import ai.knowhub.document.support.DocumentAnalysisResult;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 【文档画像服务实现】
 *
 * 这个类负责为每个文档生成"画像"（Profile），即对文档的自动化特征描述。
 * 文档画像是知识路由（Knowledge Route）的重要输入，帮助系统判断用户问题应该交给哪个文档回答。
 *
 * 画像包含以下维度：
 *   - documentSummary：文档摘要（自动拼接文档名 + 章节标题 + 文本摘要）
 *   - documentType：文档类型（faq / troubleshooting / rule / spec / manual / intro）
 *   - coreTopics：核心主题列表（从章节标题中提取）
 *   - exampleQuestions：示例问题（根据文档类型和主题自动生成）
 *   - graphFriendly：是否适合图查询（有列表项或 2+ 章节时为 true）
 *   - supportsGraphOutline：是否支持图大纲（2+ 章节）
 *   - supportsItemLookup：是否支持条目查找（有 STEP 或 LIST_ITEM 节点）
 *   - knowledgeScopeCode：知识范围编码（operation_rule / robot_strategy / deployment 等）
 *   - businessCategory：业务分类
 *   - documentTags：文档标签
 *
 * 画像生成时机：
 *   - 文档解析完成时（由 DocumentAsyncProcessServiceImpl 调用）
 *   - 用户手动重新生成时（通过 KnowledgeManageServiceImpl 的 regenerateProfile 接口）
 *
 * 元数据回填（backfill）：
 *   画像生成后，如果文档本身缺少 knowledgeScopeCode、businessCategory、documentTags 等字段，
 *   会自动从画像结果回填到文档主表，避免重复推断。
 */
@Slf4j
@AllArgsConstructor
@Service
public class DocumentProfileServiceImpl implements DocumentProfileService {

    /** 画像状态：生成成功 */
    private static final int PROFILE_STATUS_SUCCESS = 2;

    /** 文档主表 Mapper */
    private final KnowHubDocumentMapper documentMapper;

    /** 文档画像 Mapper */
    private final KnowHubDocumentProfileMapper documentProfileMapper;

    /** 结构节点 Mapper */
    private final KnowHubDocumentStructureNodeMapper structureNodeMapper;

    /** 对象存储服务，用于下载解析文本 */
    private final DocumentStorageService storageService;

    /** UID 生成器 */
    private final UidGenerator uidGenerator;

    /**
     * 生成或更新文档画像
     *
     * @param documentId     文档ID
     * @param analysisResult 文档解析结果（包含解析文本、结构节点等）
     * @param structureNodes 结构节点列表
     * @return 生成/更新后的画像实体
     */
    @Override
    public KnowHubDocumentProfile generateProfile(Long documentId,
                                                     DocumentAnalysisResult analysisResult,
                                                     List<KnowHubDocumentStructureNode> structureNodes) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        KnowHubDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        String parsedText = analysisResult == null ? "" : StrUtil.blankToDefault(analysisResult.getParsedText(), "");
        List<KnowHubDocumentStructureNode> safeNodes = structureNodes == null ? List.of() : structureNodes;
        // 构建画像草稿（基于规则推断，不依赖大模型）
        DocumentProfileDraft draft = buildDraft(document, parsedText, safeNodes);

        // 查询是否已有画像记录
        KnowHubDocumentProfile profile = documentProfileMapper.selectOne(new LambdaQueryWrapper<KnowHubDocumentProfile>()
            .eq(KnowHubDocumentProfile::getDocumentId, documentId)
            .eq(KnowHubDocumentProfile::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1"));
        boolean creating = profile == null;
        if (creating) {
            // 新建画像
            profile = new KnowHubDocumentProfile();
            profile.setId(uidGenerator.getUid());
            profile.setDocumentId(documentId);
            profile.setProfileVersion(1);
            profile.setStatus(BusinessStatus.YES.getCode());
        }
        else {
            // 更新画像版本号
            profile.setProfileVersion(Optional.ofNullable(profile.getProfileVersion()).orElse(0) + 1);
        }
        // 填充画像字段
        profile.setDocumentSummary(draft.documentSummary());
        profile.setDocumentType(draft.documentType());
        profile.setCoreTopics(joinJsonLikeArray(draft.coreTopics()));
        profile.setExampleQuestions(joinJsonLikeArray(draft.exampleQuestions()));
        profile.setGraphFriendly(draft.graphFriendly() ? 1 : 0);
        profile.setSupportsGraphOutline(draft.supportsGraphOutline() ? 1 : 0);
        profile.setSupportsItemLookup(draft.supportsItemLookup() ? 1 : 0);
        profile.setSupportsGraphAssist(draft.supportsGraphAssist() ? 1 : 0);
        profile.setProfileSource("auto");
        profile.setProfileStatus(PROFILE_STATUS_SUCCESS);
        profile.setErrorMsg(null);
        if (creating) {
            documentProfileMapper.insert(profile);
        }
        else {
            documentProfileMapper.updateById(profile);
        }

        // 回填文档元数据
        backfillDocumentMetadata(document, draft);
        log.info("文档画像生成完成: documentId={}, documentType={}, graphFriendly={}, supportsItemLookup={}, scopeCode='{}', businessCategory='{}', tags='{}'",
            documentId,
            draft.documentType(),
            draft.graphFriendly(),
            draft.supportsItemLookup(),
            draft.knowledgeScopeCode(),
            draft.businessCategory(),
            draft.documentTags());
        return profile;
    }

    /**
     * 根据文档ID查询画像
     * @param documentId 文档ID
     * @return 画像（Optional 包装）
     */
    @Override
    public Optional<KnowHubDocumentProfile> getByDocumentId(Long documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(documentProfileMapper.selectOne(new LambdaQueryWrapper<KnowHubDocumentProfile>()
            .eq(KnowHubDocumentProfile::getDocumentId, documentId)
            .eq(KnowHubDocumentProfile::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1")));
    }

    /**
     * 重新生成文档画像（从存储中重新读取解析文本和结构节点）
     * @param documentId 文档ID
     * @return 新生成的画像
     */
    @Override
    public KnowHubDocumentProfile regenerateProfile(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        KnowHubDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        // 从 MinIO 下载解析文本
        String parsedText = StrUtil.isBlank(document.getParseTextPath()) ? "" : storageService.downloadText(document.getParseTextPath());
        // 从数据库读取结构节点
        List<KnowHubDocumentStructureNode> structureNodes = structureNodeMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentStructureNode>()
            .eq(KnowHubDocumentStructureNode::getDocumentId, documentId)
            .eq(KnowHubDocumentStructureNode::getStatus, BusinessStatus.YES.getCode())
            .orderByAsc(KnowHubDocumentStructureNode::getNodeNo, KnowHubDocumentStructureNode::getId));
        DocumentAnalysisResult analysisResult = new DocumentAnalysisResult();
        analysisResult.setParsedText(parsedText);
        return generateProfile(documentId, analysisResult, structureNodes);
    }

    /**
     * 批量重新生成文档画像
     * @param documentIds 文档ID集合
     * @return 画像列表
     */
    @Override
    public List<KnowHubDocumentProfile> batchRegenerateProfiles(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        List<KnowHubDocumentProfile> profiles = new ArrayList<>();
        for (Long documentId : documentIds) {
            if (documentId == null) {
                continue;
            }
            profiles.add(regenerateProfile(documentId));
        }
        return profiles;
    }

    /**
     * 构建画像草稿（基于规则推断，不调用大模型）
     * 分析文档的结构节点、文本内容，推断出文档类型、主题、是否适合图查询等属性。
     */
    private DocumentProfileDraft buildDraft(KnowHubDocument document,
                                            String parsedText,
                                            List<KnowHubDocumentStructureNode> structureNodes) {
        // 提取章节标题
        List<String> sectionTitles = extractSectionTitles(structureNodes);
        // 是否有列表项或步骤节点
        boolean supportsItemLookup = structureNodes.stream().anyMatch(node -> node != null
            && (DocumentStructureNodeTypeEnum.STEP.getCode().equals(node.getNodeType())
            || DocumentStructureNodeTypeEnum.LIST_ITEM.getCode().equals(node.getNodeType())));
        // 是否支持图大纲（至少 2 个章节）
        boolean supportsGraphOutline = sectionTitles.size() >= 2;
        // 是否适合图查询
        boolean graphFriendly = supportsItemLookup || supportsGraphOutline;
        // 推断文档类型
        String documentType = inferDocumentType(document, parsedText, sectionTitles, supportsItemLookup);
        // 构建核心主题
        List<String> coreTopics = buildCoreTopics(document, sectionTitles);
        // 构建示例问题
        List<String> exampleQuestions = buildExampleQuestions(documentType, coreTopics);
        // 构建摘要
        String summary = buildSummary(document, sectionTitles, parsedText);
        // 推断知识范围
        String knowledgeScopeCode = inferKnowledgeScopeCode(document, sectionTitles, parsedText);
        String knowledgeScopeName = inferKnowledgeScopeName(knowledgeScopeCode);
        // 推断业务分类
        String businessCategory = inferBusinessCategory(documentType, parsedText);
        // 构建标签
        String documentTags = buildDocumentTags(document, knowledgeScopeCode, documentType, coreTopics);
        return new DocumentProfileDraft(
            summary,
            documentType,
            coreTopics,
            exampleQuestions,
            graphFriendly,
            supportsGraphOutline,
            supportsItemLookup,
            true,
            knowledgeScopeCode,
            knowledgeScopeName,
            businessCategory,
            documentTags
        );
    }

    /**
     * 回填文档元数据：如果文档缺少某些字段，从画像草稿中补充
     */
    private void backfillDocumentMetadata(KnowHubDocument document, DocumentProfileDraft draft) {
        boolean changed = false;
        if (StrUtil.isBlank(document.getKnowledgeScopeCode()) && StrUtil.isNotBlank(draft.knowledgeScopeCode())) {
            document.setKnowledgeScopeCode(draft.knowledgeScopeCode());
            changed = true;
        }
        if (StrUtil.isBlank(document.getKnowledgeScopeName()) && StrUtil.isNotBlank(draft.knowledgeScopeName())) {
            document.setKnowledgeScopeName(draft.knowledgeScopeName());
            changed = true;
        }
        if (StrUtil.isBlank(document.getBusinessCategory()) && StrUtil.isNotBlank(draft.businessCategory())) {
            document.setBusinessCategory(draft.businessCategory());
            changed = true;
        }
        if (StrUtil.isBlank(document.getDocumentTags()) && StrUtil.isNotBlank(draft.documentTags())) {
            document.setDocumentTags(draft.documentTags());
            changed = true;
        }
        if (changed) {
            documentMapper.updateById(document);
        }
    }

    /**
     * 从结构节点中提取章节标题（最多 8 个，去重）
     */
    private List<String> extractSectionTitles(List<KnowHubDocumentStructureNode> structureNodes) {
        if (CollUtil.isEmpty(structureNodes)) {
            return List.of();
        }
        return structureNodes.stream()
            .filter(node -> node != null && DocumentStructureNodeTypeEnum.SECTION.getCode().equals(node.getNodeType()))
            .map(KnowHubDocumentStructureNode::getTitle)
            .filter(StrUtil::isNotBlank)
            .map(String::trim)
            .distinct()
            .limit(8)
            .toList();
    }

    /**
     * 推断文档类型（基于关键词匹配规则）
     * - faq：常见问题
     * - troubleshooting：故障排查
     * - rule：规则/制度
     * - spec：规格/参数
     * - manual：手册/指南/部署
     * - intro：默认介绍类
     */
    private String inferDocumentType(KnowHubDocument document,
                                     String parsedText,
                                     List<String> sectionTitles,
                                     boolean supportsItemLookup) {
        String combined = combinedText(document, parsedText, sectionTitles);
        if (combined.contains("faq") || combined.contains("常见问题")) {
            return "faq";
        }
        if (combined.contains("故障") || combined.contains("排查") || combined.contains("检查顺序")) {
            return "troubleshooting";
        }
        if (combined.contains("规则") || combined.contains("制度")) {
            return "rule";
        }
        if (combined.contains("规格") || combined.contains("参数")) {
            return "spec";
        }
        if (supportsItemLookup || combined.contains("手册") || combined.contains("指南") || combined.contains("部署")) {
            return "manual";
        }
        return "intro";
    }

    /**
     * 构建核心主题列表（从章节标题中提取，最多 6 个）
     */
    private List<String> buildCoreTopics(KnowHubDocument document, List<String> sectionTitles) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        sectionTitles.stream().limit(6).forEach(title -> addTopic(topics, stripSectionCode(title)));
        addTopic(topics, stripFileExtension(document.getDocumentName()));
        return new ArrayList<>(topics).stream().filter(StrUtil::isNotBlank).limit(6).toList();
    }

    private void addTopic(Set<String> topics, String topic) {
        String normalized = StrUtil.blankToDefault(topic, "").trim();
        if (normalized.isBlank()) {
            return;
        }
        topics.add(normalized);
    }

    /**
     * 根据文档类型和核心主题构建示例问题
     */
    private List<String> buildExampleQuestions(String documentType, List<String> coreTopics) {
        List<String> examples = new ArrayList<>();
        for (String topic : coreTopics) {
            if ("troubleshooting".equals(documentType)) {
                examples.add(topic + "的可能原因有哪些？");
            }
            else if ("manual".equals(documentType)) {
                examples.add(topic + "的步骤是什么？");
            }
            else if ("rule".equals(documentType)) {
                examples.add(topic + "有哪些规则？");
            }
            else {
                examples.add(topic + "是什么意思？");
            }
        }
        return examples.stream().distinct().limit(6).toList();
    }

    /**
     * 构建文档摘要（拼接文档名 + 章节标题 + 文本摘录）
     */
    private String buildSummary(KnowHubDocument document, List<String> sectionTitles, String parsedText) {
        StringBuilder builder = new StringBuilder();
        builder.append("文档《").append(StrUtil.blankToDefault(document.getDocumentName(), "未命名文档")).append("》");
        if (!sectionTitles.isEmpty()) {
            builder.append("主要涵盖：").append(String.join("、", sectionTitles.stream().limit(4).toList())).append("。");
        }
        String excerpt = StrUtil.blankToDefault(parsedText, "").replaceAll("\\s+", " ").trim();
        if (excerpt.length() > 180) {
            excerpt = excerpt.substring(0, 180);
        }
        if (StrUtil.isNotBlank(excerpt)) {
            builder.append("摘要：").append(excerpt);
        }
        return builder.toString().trim();
    }

    /**
     * 推断知识范围编码（基于关键词匹配）
     */
    private String inferKnowledgeScopeCode(KnowHubDocument document,
                                           List<String> sectionTitles,
                                           String parsedText) {
        String combined = combinedText(document, parsedText, sectionTitles);
        if (containsAny(combined, "上线观察", "值班规则", "观察时长", "运营")) {
            return "operation_rule";
        }
        if (containsAny(combined, "机器人", "知识召回", "意图识别", "策略设计")) {
            return "robot_strategy";
        }
        if (containsAny(combined, "安装", "部署", "默认密码", "访问地址")) {
            return "deployment";
        }
        if (containsAny(combined, "故障", "排查", "异常", "检查顺序")) {
            return "troubleshooting";
        }
        if (containsAny(combined, "产品简介", "核心特性", "技术规格", "产品概述")) {
            return "product";
        }
        return "general_document";
    }

    /**
     * 将知识范围编码转为中文名称
     */
    private String inferKnowledgeScopeName(String scopeCode) {
        return switch (StrUtil.blankToDefault(scopeCode, "")) {
            case "operation_rule" -> "运营规则";
            case "robot_strategy" -> "机器人策略";
            case "deployment" -> "安装部署";
            case "troubleshooting" -> "故障排查";
            case "product" -> "产品资料";
            default -> "通用文档";
        };
    }

    /**
     * 推断业务分类
     */
    private String inferBusinessCategory(String documentType, String parsedText) {
        if ("troubleshooting".equals(documentType)) {
            return "故障排查";
        }
        if ("rule".equals(documentType)) {
            return "规则";
        }
        if ("spec".equals(documentType)) {
            return "规格说明";
        }
        if ("manual".equals(documentType)) {
            return containsAny(parsedText.toLowerCase(Locale.ROOT), "步骤", "操作", "部署")
                ? "操作手册"
                : "手册";
        }
        return "介绍";
    }

    /**
     * 构建文档标签（合并已有标签 + 知识范围 + 文档类型 + 核心主题）
     */
    private String buildDocumentTags(KnowHubDocument document,
                                     String knowledgeScopeCode,
                                     String documentType,
                                     List<String> coreTopics) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(document.getDocumentTags())) {
            tags.addAll(List.of(document.getDocumentTags().split(",")));
        }
        addTag(tags, knowledgeScopeCode);
        addTag(tags, documentType);
        coreTopics.stream().limit(4).forEach(topic -> addTag(tags, topic));
        return tags.stream()
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .limit(8)
            .collect(Collectors.joining(","));
    }

    private void addTag(Set<String> tags, String tag) {
        String normalized = StrUtil.blankToDefault(tag, "").trim();
        if (normalized.isBlank()) {
            return;
        }
        tags.add(normalized);
    }

    /**
     * 检查文本中是否包含任意一个关键词（不区分大小写）
     */
    private boolean containsAny(String text, String... values) {
        String normalized = StrUtil.blankToDefault(text, "").toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (normalized.contains(StrUtil.blankToDefault(value, "").toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并文档名、原始文件名、章节标题和解析文本为一个全文本（用于关键词匹配）
     */
    private String combinedText(KnowHubDocument document,
                                String parsedText,
                                List<String> sectionTitles) {
        return (StrUtil.blankToDefault(document.getDocumentName(), "") + " "
            + StrUtil.blankToDefault(document.getOriginalFileName(), "") + " "
            + String.join(" ", sectionTitles) + " "
            + StrUtil.blankToDefault(parsedText, ""))
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 去除章节标题中的序号前缀（如 "第一章"、"1.2.3" 等）
     */
    private String stripSectionCode(String title) {
        String normalized = StrUtil.blankToDefault(title, "").trim();
        return normalized.replaceFirst("^(第[一二三四五六七八九十百0-9]+[章节条部分]\\s*)|(\\d+(?:\\.\\d+)+\\s*)", "").trim();
    }

    /**
     * 去除文件名中的扩展名
     */
    private String stripFileExtension(String fileName) {
        String normalized = StrUtil.blankToDefault(fileName, "").trim();
        int index = normalized.lastIndexOf('.');
        return index > 0 ? normalized.substring(0, index) : normalized;
    }

    /**
     * 将字符串列表转为 JSON 数组格式的字符串
     * 例如：["topic1", "topic2"]
     */
    private String joinJsonLikeArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
            .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
            .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * 【内部记录类】文档画像草稿
     * 用于在 buildDraft 方法中传递中间结果
     */
    private record DocumentProfileDraft(
        String documentSummary,
        String documentType,
        List<String> coreTopics,
        List<String> exampleQuestions,
        boolean graphFriendly,
        boolean supportsGraphOutline,
        boolean supportsItemLookup,
        boolean supportsGraphAssist,
        String knowledgeScopeCode,
        String knowledgeScopeName,
        String businessCategory,
        String documentTags
    ) {
    }
}
