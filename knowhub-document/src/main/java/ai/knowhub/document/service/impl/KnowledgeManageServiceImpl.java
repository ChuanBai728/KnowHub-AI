package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.hutool.core.util.StrUtil;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentProfile;
import ai.knowhub.document.data.KnowHubKnowledgeScopeNode;
import ai.knowhub.document.data.KnowHubKnowledgeTopicNode;
import ai.knowhub.document.data.KnowHubTopicDocumentRelation;
import ai.knowhub.document.dto.DocumentProfileBatchRegenerateDto;
import ai.knowhub.document.dto.DocumentProfileDetailQueryDto;
import ai.knowhub.document.dto.DocumentProfileRegenerateDto;
import ai.knowhub.document.dto.KnowledgeRouteTraceQueryDto;
import ai.knowhub.document.dto.KnowledgeScopeDeleteDto;
import ai.knowhub.document.dto.KnowledgeScopeSaveDto;
import ai.knowhub.document.dto.KnowledgeTopicDeleteDto;
import ai.knowhub.document.dto.KnowledgeTopicQueryDto;
import ai.knowhub.document.dto.KnowledgeTopicSaveDto;
import ai.knowhub.document.dto.TopicDocumentRelationListQueryDto;
import ai.knowhub.document.dto.TopicDocumentRelationRemoveDto;
import ai.knowhub.document.dto.TopicDocumentRelationSaveDto;
import ai.knowhub.document.mapper.KnowHubDocumentMapper;
import ai.knowhub.document.mapper.KnowHubKnowledgeScopeNodeMapper;
import ai.knowhub.document.mapper.KnowHubKnowledgeTopicNodeMapper;
import ai.knowhub.document.mapper.KnowHubKnowledgeRouteTraceMapper;
import ai.knowhub.document.mapper.KnowHubTopicDocumentRelationMapper;
import ai.knowhub.document.service.DocumentProfileService;
import ai.knowhub.document.service.KnowledgeManageService;
import ai.knowhub.document.vo.DocumentProfileVo;
import ai.knowhub.document.vo.KnowledgeRouteTraceItemVo;
import ai.knowhub.document.vo.KnowledgeRouteTracePageVo;
import ai.knowhub.document.vo.KnowledgeScopeItemVo;
import ai.knowhub.document.vo.KnowledgeTopicItemVo;
import ai.knowhub.document.vo.TopicDocumentRelationItemVo;
import ai.knowhub.enums.BaseCode;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 【知识管理服务实现】
 *
 * 这个类是知识管理模块的核心服务，提供知识范围（Scope）、知识主题（Topic）、
 * 主题-文档关联关系和文档画像的 CRUD 操作。
 *
 * 知识管理的数据模型：
 *
 *   知识范围（Scope）
 *     ├── 知识主题（Topic）
 *     │     ├── 主题-文档关联（TopicDocumentRelation）
 *     │     └── 主题-文档关联（TopicDocumentRelation）
 *     └── 知识主题（Topic）
 *
 * 知识范围（Scope）：
 *   - 代表一个知识领域，如"运营规则"、"安装部署"、"故障排查"等
 *   - 通过 scopeCode 唯一标识
 *   - 支持层级关系（parentScopeCode）
 *   - 包含描述、别名、示例等丰富元数据
 *
 * 知识主题（Topic）：
 *   - 属于某个知识范围，代表一个具体的知识主题
 *   - 通过 topicCode 唯一标识
 *   - 包含描述、别名、示例、回答形态（answerShape）、执行偏好（executionPreference）
 *
 * 主题-文档关联（TopicDocumentRelation）：
 *   - 建立主题和文档之间的多对多关系
 *   - 包含关联分数（relationScore）和关联来源（relationSource）
 *
 * 文档画像（DocumentProfile）：
 *   - 由 DocumentProfileService 生成和管理
 *   - 本类提供查询和重新生成的入口
 *
 * 知识路由追踪（KnowledgeRouteTrace）：
 *   - 记录每次知识路由的决策过程和结果
 *   - 支持分页查询，用于分析路由质量
 *
 * 所有删除操作采用"软删除"策略（将 status 设为 NO），不物理删除数据。
 */
@AllArgsConstructor
@Service
public class KnowledgeManageServiceImpl implements KnowledgeManageService {

    /** 知识范围 Mapper */
    private final KnowHubKnowledgeScopeNodeMapper scopeNodeMapper;

    /** 知识主题 Mapper */
    private final KnowHubKnowledgeTopicNodeMapper topicNodeMapper;

    /** 主题-文档关联 Mapper */
    private final KnowHubTopicDocumentRelationMapper topicDocumentRelationMapper;

    /** 知识路由追踪 Mapper */
    private final KnowHubKnowledgeRouteTraceMapper knowledgeRouteTraceMapper;

    /** 文档 Mapper */
    private final KnowHubDocumentMapper documentMapper;

    /** 文档画像服务 */
    private final DocumentProfileService documentProfileService;

    /** UID 生成器 */
    private final UidGenerator uidGenerator;

    /**
     * 创建或更新知识范围。
     * 使用 upsert 逻辑：根据 scopeCode 查询是否存在，存在则更新，不存在则创建。
     */
    @Override
    public KnowledgeScopeItemVo saveScope(KnowledgeScopeSaveDto dto) {
        validateScope(dto);
        KnowHubKnowledgeScopeNode entity = scopeNodeMapper.selectOne(new LambdaQueryWrapper<KnowHubKnowledgeScopeNode>()
            .eq(KnowHubKnowledgeScopeNode::getScopeCode, dto.getScopeCode().trim())
            .eq(KnowHubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1"));
        if (entity == null) {
            entity = new KnowHubKnowledgeScopeNode();
            entity.setId(uidGenerator.getUid());
            entity.setStatus(BusinessStatus.YES.getCode());
            entity.setScopeCode(dto.getScopeCode().trim());
        }
        entity.setScopeName(safeText(dto.getScopeName()));
        entity.setParentScopeCode(safeText(dto.getParentScopeCode()));
        entity.setDescription(safeText(dto.getDescription()));
        entity.setAliases(safeText(dto.getAliases()));
        entity.setExamples(safeText(dto.getExamples()));
        entity.setSortOrder(parseInteger(dto.getSortOrder(), 0));
        if (entity.getCreateTime() == null) {
            scopeNodeMapper.insert(entity);
        }
        else {
            scopeNodeMapper.updateById(entity);
        }
        return toScopeVo(entity);
    }

    /**
     * 软删除知识范围（将 status 设为 NO）
     */
    @Override
    public boolean deleteScope(KnowledgeScopeDeleteDto dto) {
        String scopeCode = safeText(dto.getScopeCode());
        if (scopeCode.isBlank()) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), "scopeCode 不能为空。");
        }
        return scopeNodeMapper.update(null, new LambdaUpdateWrapper<KnowHubKnowledgeScopeNode>()
            .eq(KnowHubKnowledgeScopeNode::getScopeCode, scopeCode)
            .eq(KnowHubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode())
            .set(KnowHubKnowledgeScopeNode::getStatus, BusinessStatus.NO.getCode())) > 0;
    }

    /**
     * 查询所有有效的知识范围（按 sortOrder 和 ID 排序）
     */
    @Override
    public List<KnowledgeScopeItemVo> listScopes() {
        return scopeNodeMapper.selectList(new LambdaQueryWrapper<KnowHubKnowledgeScopeNode>()
                .eq(KnowHubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(KnowHubKnowledgeScopeNode::getSortOrder, KnowHubKnowledgeScopeNode::getId))
            .stream()
            .map(this::toScopeVo)
            .toList();
    }

    /**
     * 创建或更新知识主题
     */
    @Override
    public KnowledgeTopicItemVo saveTopic(KnowledgeTopicSaveDto dto) {
        validateTopic(dto);
        KnowHubKnowledgeTopicNode entity = topicNodeMapper.selectOne(new LambdaQueryWrapper<KnowHubKnowledgeTopicNode>()
            .eq(KnowHubKnowledgeTopicNode::getTopicCode, dto.getTopicCode().trim())
            .eq(KnowHubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1"));
        if (entity == null) {
            entity = new KnowHubKnowledgeTopicNode();
            entity.setId(uidGenerator.getUid());
            entity.setStatus(BusinessStatus.YES.getCode());
            entity.setTopicCode(dto.getTopicCode().trim());
        }
        entity.setTopicName(safeText(dto.getTopicName()));
        entity.setScopeCode(safeText(dto.getScopeCode()));
        entity.setDescription(safeText(dto.getDescription()));
        entity.setAliases(safeText(dto.getAliases()));
        entity.setExamples(safeText(dto.getExamples()));
        entity.setAnswerShape(safeText(dto.getAnswerShape()));
        entity.setExecutionPreference(safeText(dto.getExecutionPreference()));
        entity.setSortOrder(parseInteger(dto.getSortOrder(), 0));
        if (entity.getCreateTime() == null) {
            topicNodeMapper.insert(entity);
        }
        else {
            topicNodeMapper.updateById(entity);
        }
        return toTopicVo(entity);
    }

    /**
     * 软删除知识主题
     */
    @Override
    public boolean deleteTopic(KnowledgeTopicDeleteDto dto) {
        String topicCode = safeText(dto.getTopicCode());
        if (topicCode.isBlank()) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), "topicCode 不能为空。");
        }
        return topicNodeMapper.update(null, new LambdaUpdateWrapper<KnowHubKnowledgeTopicNode>()
            .eq(KnowHubKnowledgeTopicNode::getTopicCode, topicCode)
            .eq(KnowHubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode())
            .set(KnowHubKnowledgeTopicNode::getStatus, BusinessStatus.NO.getCode())) > 0;
    }

    /**
     * 查询知识主题列表（可选按 scopeCode 过滤）
     */
    @Override
    public List<KnowledgeTopicItemVo> listTopics(KnowledgeTopicQueryDto dto) {
        String scopeCode = dto == null ? "" : safeText(dto.getScopeCode());
        LambdaQueryWrapper<KnowHubKnowledgeTopicNode> wrapper = new LambdaQueryWrapper<KnowHubKnowledgeTopicNode>()
            .eq(KnowHubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode())
            .orderByAsc(KnowHubKnowledgeTopicNode::getSortOrder, KnowHubKnowledgeTopicNode::getId);
        if (scopeCode != null && !scopeCode.isBlank()) {
            wrapper.eq(KnowHubKnowledgeTopicNode::getScopeCode, scopeCode);
        }
        return topicNodeMapper.selectList(wrapper).stream().map(this::toTopicVo).toList();
    }

    /**
     * 查询文档画像详情
     */
    @Override
    public DocumentProfileVo queryProfile(DocumentProfileDetailQueryDto dto) {
        Long documentId = parseRequiredLong(dto == null ? null : dto.getDocumentId(), "documentId");
        KnowHubDocumentProfile profile = documentProfileService.getByDocumentId(documentId)
            .orElseThrow(() -> new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), "文档画像不存在。"));
        return toProfileVo(profile);
    }

    /**
     * 重新生成单个文档的画像
     */
    @Override
    public DocumentProfileVo regenerateProfile(DocumentProfileRegenerateDto dto) {
        Long documentId = parseRequiredLong(dto == null ? null : dto.getDocumentId(), "documentId");
        return toProfileVo(documentProfileService.regenerateProfile(documentId));
    }

    /**
     * 批量重新生成文档画像
     */
    @Override
    public List<DocumentProfileVo> batchRegenerateProfiles(DocumentProfileBatchRegenerateDto dto) {
        List<Long> documentIds = dto == null || dto.getDocumentIds() == null
            ? List.of()
            : dto.getDocumentIds().stream().map(value -> parseRequiredLong(value, "documentId")).toList();
        return documentProfileService.batchRegenerateProfiles(documentIds).stream()
            .map(this::toProfileVo)
            .toList();
    }

    /**
     * 查询主题-文档关联列表（可选按 topicCode 过滤）
     */
    @Override
    public List<TopicDocumentRelationItemVo> listTopicDocuments(TopicDocumentRelationListQueryDto dto) {
        String topicCode = dto == null ? "" : safeText(dto.getTopicCode());
        LambdaQueryWrapper<KnowHubTopicDocumentRelation> wrapper = new LambdaQueryWrapper<KnowHubTopicDocumentRelation>()
            .eq(KnowHubTopicDocumentRelation::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(KnowHubTopicDocumentRelation::getRelationScore, KnowHubTopicDocumentRelation::getId);
        if (topicCode != null && !topicCode.isBlank()) {
            wrapper.eq(KnowHubTopicDocumentRelation::getTopicCode, topicCode);
        }
        return topicDocumentRelationMapper.selectList(wrapper).stream()
            .map(this::toRelationVo)
            .toList();
    }

    /**
     * 创建或更新主题-文档关联
     */
    @Override
    public TopicDocumentRelationItemVo saveTopicDocumentRelation(TopicDocumentRelationSaveDto dto) {
        String topicCode = safeText(dto.getTopicCode());
        Long documentId = parseRequiredLong(dto.getDocumentId(), "documentId");
        if (topicCode.isBlank()) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), "topicCode 不能为空。");
        }
        KnowHubTopicDocumentRelation relation = topicDocumentRelationMapper.selectOne(new LambdaQueryWrapper<KnowHubTopicDocumentRelation>()
            .eq(KnowHubTopicDocumentRelation::getTopicCode, topicCode)
            .eq(KnowHubTopicDocumentRelation::getDocumentId, documentId)
            .eq(KnowHubTopicDocumentRelation::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1"));
        if (relation == null) {
            relation = new KnowHubTopicDocumentRelation();
            relation.setId(uidGenerator.getUid());
            relation.setTopicCode(topicCode);
            relation.setDocumentId(documentId);
            relation.setStatus(BusinessStatus.YES.getCode());
        }
        relation.setRelationScore(parseDecimal(dto.getRelationScore(), BigDecimal.ZERO));
        relation.setRelationSource(firstNonBlank(dto.getRelationSource(), "manual"));
        relation.setReason(safeText(dto.getReason()));
        if (relation.getCreateTime() == null) {
            topicDocumentRelationMapper.insert(relation);
        }
        else {
            topicDocumentRelationMapper.updateById(relation);
        }
        return toRelationVo(relation);
    }

    /**
     * 软删除主题-文档关联
     */
    @Override
    public boolean removeTopicDocumentRelation(TopicDocumentRelationRemoveDto dto) {
        String topicCode = safeText(dto.getTopicCode());
        Long documentId = parseRequiredLong(dto.getDocumentId(), "documentId");
        if (topicCode.isBlank()) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), "topicCode 不能为空。");
        }
        return topicDocumentRelationMapper.update(null, new LambdaUpdateWrapper<KnowHubTopicDocumentRelation>()
            .eq(KnowHubTopicDocumentRelation::getTopicCode, topicCode)
            .eq(KnowHubTopicDocumentRelation::getDocumentId, documentId)
            .eq(KnowHubTopicDocumentRelation::getStatus, BusinessStatus.YES.getCode())
            .set(KnowHubTopicDocumentRelation::getStatus, BusinessStatus.NO.getCode())) > 0;
    }

    /**
     * 分页查询知识路由追踪记录
     */
    @Override
    public KnowledgeRouteTracePageVo queryRouteTracePage(KnowledgeRouteTraceQueryDto dto) {
        int pageNo = parseInteger(dto == null ? null : dto.getPageNo(), 1);
        int pageSize = parseInteger(dto == null ? null : dto.getPageSize(), 20);
        String conversationId = dto == null ? "" : safeText(dto.getConversationId());
        String mode = dto == null ? "" : safeText(dto.getMode());
        String routeStatus = dto == null ? "" : safeText(dto.getRouteStatus());
        LambdaQueryWrapper<ai.knowhub.document.data.KnowHubKnowledgeRouteTrace> wrapper =
            new LambdaQueryWrapper<ai.knowhub.document.data.KnowHubKnowledgeRouteTrace>()
                .eq(ai.knowhub.document.data.KnowHubKnowledgeRouteTrace::getStatus, BusinessStatus.YES.getCode())
                .orderByDesc(ai.knowhub.document.data.KnowHubKnowledgeRouteTrace::getCreateTime,
                    ai.knowhub.document.data.KnowHubKnowledgeRouteTrace::getId);
        if (StrUtil.isNotBlank(conversationId)) {
            wrapper.eq(ai.knowhub.document.data.KnowHubKnowledgeRouteTrace::getConversationId, conversationId);
        }
        if (StrUtil.isNotBlank(mode)) {
            wrapper.eq(ai.knowhub.document.data.KnowHubKnowledgeRouteTrace::getMode, mode);
        }
        if (StrUtil.isNotBlank(routeStatus)) {
            Integer parsedStatus = parseInteger(routeStatus, -1);
            if (parsedStatus > 0) {
                wrapper.eq(ai.knowhub.document.data.KnowHubKnowledgeRouteTrace::getRouteStatus, parsedStatus);
            }
        }
        long total = knowledgeRouteTraceMapper.selectCount(wrapper);
        List<KnowledgeRouteTraceItemVo> records = knowledgeRouteTraceMapper.selectList(wrapper.last("LIMIT " + ((long) (pageNo - 1) * pageSize) + "," + pageSize))
            .stream()
            .map(item -> new KnowledgeRouteTraceItemVo(
                String.valueOf(item.getId()),
                safeText(item.getConversationId()),
                item.getExchangeId() == null ? "" : String.valueOf(item.getExchangeId()),
                safeText(item.getQuestion()),
                safeText(item.getRewriteQuestion()),
                safeText(item.getMode()),
                safeText(item.getTopScopesJson()),
                safeText(item.getTopTopicsJson()),
                safeText(item.getTopDocumentsJson()),
                item.getSelectedDocumentId() == null ? "" : String.valueOf(item.getSelectedDocumentId()),
                item.getHitSelectedDocument() == null ? "" : String.valueOf(item.getHitSelectedDocument()),
                item.getConfidence() == null ? "0.0000" : item.getConfidence().toPlainString(),
                item.getRouteStatus() == null ? "" : String.valueOf(item.getRouteStatus()),
                safeText(item.getErrorMsg()),
                item.getCreateTime() == null ? "" : String.valueOf(item.getCreateTime().getTime())
            ))
            .toList();
        long totalPages = total <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new KnowledgeRouteTracePageVo(
            String.valueOf(pageNo),
            String.valueOf(pageSize),
            String.valueOf(total),
            String.valueOf(totalPages),
            records
        );
    }

    // ========== 私有辅助方法 ==========

    private void validateScope(KnowledgeScopeSaveDto dto) {
        if (dto == null || safeText(dto.getScopeCode()).isBlank() || safeText(dto.getScopeName()).isBlank()) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), "scopeCode 和 scopeName 不能为空。");
        }
    }

    private void validateTopic(KnowledgeTopicSaveDto dto) {
        if (dto == null || safeText(dto.getTopicCode()).isBlank() || safeText(dto.getTopicName()).isBlank() || safeText(dto.getScopeCode()).isBlank()) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), "topicCode、topicName、scopeCode 不能为空。");
        }
    }

    /** 将知识范围实体转为 VO */
    private KnowledgeScopeItemVo toScopeVo(KnowHubKnowledgeScopeNode node) {
        return new KnowledgeScopeItemVo(
            String.valueOf(node.getId()),
            safeText(node.getScopeCode()),
            safeText(node.getScopeName()),
            safeText(node.getParentScopeCode()),
            safeText(node.getDescription()),
            safeText(node.getAliases()),
            safeText(node.getExamples()),
            String.valueOf(Optional.ofNullable(node.getSortOrder()).orElse(0))
        );
    }

    /** 将知识主题实体转为 VO */
    private KnowledgeTopicItemVo toTopicVo(KnowHubKnowledgeTopicNode node) {
        return new KnowledgeTopicItemVo(
            String.valueOf(node.getId()),
            safeText(node.getTopicCode()),
            safeText(node.getTopicName()),
            safeText(node.getScopeCode()),
            safeText(node.getDescription()),
            safeText(node.getAliases()),
            safeText(node.getExamples()),
            safeText(node.getAnswerShape()),
            safeText(node.getExecutionPreference()),
            String.valueOf(Optional.ofNullable(node.getSortOrder()).orElse(0))
        );
    }

    /** 将文档画像实体转为 VO */
    private DocumentProfileVo toProfileVo(KnowHubDocumentProfile profile) {
        return new DocumentProfileVo(
            String.valueOf(profile.getDocumentId()),
            safeText(profile.getDocumentSummary()),
            safeText(profile.getDocumentType()),
            safeText(profile.getCoreTopics()),
            safeText(profile.getExampleQuestions()),
            String.valueOf(Optional.ofNullable(profile.getGraphFriendly()).orElse(0)),
            String.valueOf(Optional.ofNullable(profile.getSupportsGraphOutline()).orElse(0)),
            String.valueOf(Optional.ofNullable(profile.getSupportsItemLookup()).orElse(0)),
            String.valueOf(Optional.ofNullable(profile.getSupportsGraphAssist()).orElse(0)),
            safeText(profile.getProfileSource()),
            String.valueOf(Optional.ofNullable(profile.getProfileStatus()).orElse(0)),
            safeText(profile.getErrorMsg())
        );
    }

    /** 将主题-文档关联实体转为 VO（会查询关联的文档信息） */
    private TopicDocumentRelationItemVo toRelationVo(KnowHubTopicDocumentRelation relation) {
        KnowHubDocument document = documentMapper.selectById(relation.getDocumentId());
        return new TopicDocumentRelationItemVo(
            safeText(relation.getTopicCode()),
            String.valueOf(relation.getDocumentId()),
            document == null ? "" : safeText(document.getDocumentName()),
            document == null ? "" : safeText(document.getKnowledgeScopeCode()),
            document == null ? "" : safeText(document.getKnowledgeScopeName()),
            document == null ? "" : safeText(document.getBusinessCategory()),
            document == null ? "" : safeText(document.getDocumentTags()),
            relation.getRelationScore() == null ? "0.0000" : relation.getRelationScore().toPlainString(),
            safeText(relation.getRelationSource()),
            safeText(relation.getReason())
        );
    }

    private Long parseRequiredLong(String rawValue, String fieldName) {
        if (StrUtil.isBlank(rawValue)) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), fieldName + "不能为空。");
        }
        try {
            Long value = Long.valueOf(rawValue.trim());
            if (value <= 0) {
                throw new NumberFormatException("must be positive");
            }
            return value;
        }
        catch (NumberFormatException exception) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), fieldName + "格式非法。");
        }
    }

    private Integer parseInteger(String rawValue, Integer fallback) {
        if (StrUtil.isBlank(rawValue)) {
            return fallback;
        }
        try {
            return Integer.valueOf(rawValue.trim());
        }
        catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private BigDecimal parseDecimal(String rawValue, BigDecimal fallback) {
        if (StrUtil.isBlank(rawValue)) {
            return fallback;
        }
        try {
            return new BigDecimal(rawValue.trim());
        }
        catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (StrUtil.isNotBlank(primary)) {
            return primary.trim();
        }
        return StrUtil.blankToDefault(fallback, "");
    }
}
