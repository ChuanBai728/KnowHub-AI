package ai.knowhub.document.service;

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
import ai.knowhub.document.vo.DocumentProfileVo;
import ai.knowhub.document.vo.KnowledgeRouteTracePageVo;
import ai.knowhub.document.vo.KnowledgeScopeItemVo;
import ai.knowhub.document.vo.KnowledgeTopicItemVo;
import ai.knowhub.document.vo.TopicDocumentRelationItemVo;

import java.util.List;

/**
 * 【知识管理服务接口】
 *
 * 作用：提供知识域管理的统一服务接口，管理知识域（Scope）、主题（Topic）、
 * 文档画像（Profile）和知识路由（Route）等核心知识管理功能。
 *
 * 架构角色：
 *   - 属于知识管理模块的"门面层（Facade Layer）"
 *   - 对上层（Controller）暴露统一的知识管理 API
 *   - 对下层协调知识域、主题、文档画像、路由追踪等多个子系统
 *
 * 核心概念：
 *   - 知识域（Scope）：知识的顶层分类，如"技术文档"、"产品手册"、"FAQ"
 *   - 主题（Topic）：知识域下的细分主题，如"Spring Boot 配置"、"数据库优化"
 *   - 文档画像（Profile）：文档的摘要和元信息，用于知识管理
 *   - 主题-文档关联：一个主题可以关联多个文档，实现知识的组织和管理
 *   - 路由追踪（Route Trace）：记录知识路由的决策过程，用于分析和优化
 *
 * 设计模式：门面模式（Facade Pattern）
 */
public interface KnowledgeManageService {

    /**
     * 保存知识域
     *
     * @param dto 知识域保存参数（名称、描述等）
     * @return 保存后的知识域信息
     */
    KnowledgeScopeItemVo saveScope(KnowledgeScopeSaveDto dto);

    /**
     * 删除知识域
     *
     * @param dto 删除参数（知识域ID等）
     * @return true 表示删除成功
     */
    boolean deleteScope(KnowledgeScopeDeleteDto dto);

    /**
     * 列出所有知识域
     *
     * @return 知识域列表
     */
    List<KnowledgeScopeItemVo> listScopes();

    /**
     * 保存主题
     *
     * @param dto 主题保存参数（名称、所属知识域等）
     * @return 保存后的主题信息
     */
    KnowledgeTopicItemVo saveTopic(KnowledgeTopicSaveDto dto);

    /**
     * 删除主题
     *
     * @param dto 删除参数（主题ID等）
     * @return true 表示删除成功
     */
    boolean deleteTopic(KnowledgeTopicDeleteDto dto);

    /**
     * 查询主题列表
     *
     * @param dto 查询参数（知识域ID、关键词等）
     * @return 主题列表
     */
    List<KnowledgeTopicItemVo> listTopics(KnowledgeTopicQueryDto dto);

    /**
     * 查询文档画像详情
     *
     * @param dto 查询参数（文档ID等）
     * @return 文档画像信息
     */
    DocumentProfileVo queryProfile(DocumentProfileDetailQueryDto dto);

    /**
     * 重新生成单个文档的画像
     *
     * @param dto 重新生成参数（文档ID等）
     * @return 重新生成后的文档画像
     */
    DocumentProfileVo regenerateProfile(DocumentProfileRegenerateDto dto);

    /**
     * 批量重新生成文档画像
     *
     * @param dto 批量重新生成参数（文档ID列表等）
     * @return 重新生成后的文档画像列表
     */
    List<DocumentProfileVo> batchRegenerateProfiles(DocumentProfileBatchRegenerateDto dto);

    /**
     * 查询主题关联的文档列表
     *
     * @param dto 查询参数（主题ID等）
     * @return 主题-文档关联信息列表
     */
    List<TopicDocumentRelationItemVo> listTopicDocuments(TopicDocumentRelationListQueryDto dto);

    /**
     * 保存主题与文档的关联关系
     *
     * @param dto 关联保存参数（主题ID、文档ID等）
     * @return 保存后的关联信息
     */
    TopicDocumentRelationItemVo saveTopicDocumentRelation(TopicDocumentRelationSaveDto dto);

    /**
     * 移除主题与文档的关联关系
     *
     * @param dto 移除参数（关联ID等）
     * @return true 表示移除成功
     */
    boolean removeTopicDocumentRelation(TopicDocumentRelationRemoveDto dto);

    /**
     * 分页查询知识路由追踪记录
     *
     * 功能说明：
     *   - 查看知识路由的决策历史
     *   - 用于分析路由策略的效果和优化路由算法
     *
     * @param dto 查询参数（分页信息、过滤条件等）
     * @return 路由追踪的分页结果
     */
    KnowledgeRouteTracePageVo queryRouteTracePage(KnowledgeRouteTraceQueryDto dto);
}
