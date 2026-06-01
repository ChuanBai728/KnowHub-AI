package ai.knowhub.document.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
import ai.knowhub.document.service.KnowledgeManageService;
import ai.knowhub.document.vo.DocumentProfileVo;
import ai.knowhub.document.vo.KnowledgeRouteTracePageVo;
import ai.knowhub.document.vo.KnowledgeScopeItemVo;
import ai.knowhub.document.vo.KnowledgeTopicItemVo;
import ai.knowhub.document.vo.TopicDocumentRelationItemVo;
import ai.knowhub.common.ApiResponse;
import ai.knowhub.web.ApiVersion;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识管理控制器。
 *
 * 本类是知识管理模块的 REST API 入口，负责管理知识范围（Scope）、
 * 知识主题（Topic）、文档画像（Profile）以及知识路由追踪等。
 *
 * 知识管理的核心概念：
 * 
 *   <b>知识范围（Scope）</b>：知识的顶层分类，如「财务」「技术」「人力资源」等。
 *       每个范围下可以包含多个主题。
 *   <b>知识主题（Topic）</b>：知识范围下的细分主题，如「财务」范围下的「报销流程」「税务政策」等。
 *       每个主题关联一组文档。
 *   <b>文档画像（Profile）</b>：使用 LLM 对文档进行分析后生成的结构化摘要，
 *       包含文档类型、核心主题、示例问题等，用于优化 RAG 检索的路由准确性。
 *   <b>主题文档关联</b>：建立主题与文档之间的多对多关系，决定 RAG 检索时
 *       哪些文档属于哪个主题。
 *   <b>知识路由追踪</b>：记录每次用户提问时系统如何将问题路由到特定的知识范围和主题，
 *       用于分析路由准确率和优化路由策略。
 * 设计模式：
 * 
 *   <b>MVC 模式</b>：Controller 负责请求路由，Service 负责业务逻辑。
 *   <b>统一响应封装</b>：所有接口返回 ApiResponse 统一格式。
 *   <b>DTO/VO 分离</b>：请求使用 DTO，响应使用 VO。
 * 
 */
@RestController
@RequestMapping(ApiVersion.V1_MANAGE_KNOWLEDGE)
public class KnowledgeManageController {

    /**
     * 知识管理服务，包含所有知识管理的业务逻辑。
     */
    private final KnowledgeManageService knowledgeManageService;

    /**
     * 构造器注入知识管理服务。
     *
     * @param knowledgeManageService 知识管理服务实例
     */
    public KnowledgeManageController(KnowledgeManageService knowledgeManageService) {
        this.knowledgeManageService = knowledgeManageService;
    }

    /**
     * 保存知识范围节点。
     *
     * 创建或更新一个知识范围。知识范围是知识体系的顶层分类。
     * 如果 DTO 中包含 ID，则执行更新操作；否则执行新增操作。
     *
     * @param dto 知识范围保存信息（名称、编码、描述、别名等）
     * @return 保存后的知识范围信息
     */
    @Operation(summary = "保存知识范围节点")
    @PostMapping("/scope/save")
    public ApiResponse<KnowledgeScopeItemVo> saveScope(@Valid @RequestBody KnowledgeScopeSaveDto dto) {
        return ApiResponse.ok(knowledgeManageService.saveScope(dto));
    }

    /**
     * 删除知识范围节点。
     *
     * 删除指定的知识范围。如果该范围下还有主题或文档关联，
     * 可能会拒绝删除或级联删除（取决于业务规则）。
     *
     * @param dto 删除条件（包含范围 ID 或编码）
     * @return 删除是否成功
     */
    @Operation(summary = "删除知识范围节点")
    @PostMapping("/scope/delete")
    public ApiResponse<Boolean> deleteScope(@Valid @RequestBody KnowledgeScopeDeleteDto dto) {
        return ApiResponse.ok(knowledgeManageService.deleteScope(dto));
    }

    /**
     * 查询知识范围列表。
     *
     * 返回所有知识范围的列表，用于前端的知识范围管理页面和下拉选择。
     *
     * @return 知识范围列表
     */
    @Operation(summary = "查询知识范围列表")
    @PostMapping("/scope/list")
    public ApiResponse<List<KnowledgeScopeItemVo>> listScopes() {
        return ApiResponse.ok(knowledgeManageService.listScopes());
    }

    /**
     * 保存知识主题节点。
     *
     * 创建或更新一个知识主题。知识主题归属于某个知识范围。
     * 主题包含名称、描述、别名、示例问题等信息，用于优化 RAG 路由。
     *
     * @param dto 知识主题保存信息
     * @return 保存后的知识主题信息
     */
    @Operation(summary = "保存知识主题节点")
    @PostMapping("/topic/save")
    public ApiResponse<KnowledgeTopicItemVo> saveTopic(@Valid @RequestBody KnowledgeTopicSaveDto dto) {
        return ApiResponse.ok(knowledgeManageService.saveTopic(dto));
    }

    /**
     * 删除知识主题节点。
     *
     * @param dto 删除条件（包含主题 ID 或编码）
     * @return 删除是否成功
     */
    @Operation(summary = "删除知识主题节点")
    @PostMapping("/topic/delete")
    public ApiResponse<Boolean> deleteTopic(@Valid @RequestBody KnowledgeTopicDeleteDto dto) {
        return ApiResponse.ok(knowledgeManageService.deleteTopic(dto));
    }

    /**
     * 查询知识主题列表。
     *
     * 支持按知识范围筛选主题列表。如果不传筛选条件，则返回所有主题。
     *
     * @param dto 查询条件（可选，可按范围编码筛选）
     * @return 知识主题列表
     */
    @Operation(summary = "查询知识主题列表")
    @PostMapping("/topic/list")
    public ApiResponse<List<KnowledgeTopicItemVo>> listTopics(@RequestBody(required = false) KnowledgeTopicQueryDto dto) {
        return ApiResponse.ok(knowledgeManageService.listTopics(dto == null ? new KnowledgeTopicQueryDto() : dto));
    }

    /**
     * 查询文档画像详情。
     *
     * 文档画像是使用 LLM 对文档进行分析后生成的结构化摘要。
     * 它包含文档的核心主题、类型、示例问题等信息，
     * 用于在知识路由阶段更准确地将用户问题映射到相关文档。
     *
     * @param dto 查询条件（包含文档 ID）
     * @return 文档画像详情
     */
    @Operation(summary = "查询文档画像详情")
    @PostMapping("/document/profile/detail")
    public ApiResponse<DocumentProfileVo> queryProfile(@Valid @RequestBody DocumentProfileDetailQueryDto dto) {
        return ApiResponse.ok(knowledgeManageService.queryProfile(dto));
    }

    /**
     * 重新生成文档画像。
     *
     * 当文档内容更新或画像质量不高时，可以重新调用 LLM 生成文档画像。
     *
     * @param dto 重新生成请求（包含文档 ID）
     * @return 新生成的文档画像
     */
    @Operation(summary = "重新生成文档画像")
    @PostMapping("/document/profile/regenerate")
    public ApiResponse<DocumentProfileVo> regenerateProfile(@Valid @RequestBody DocumentProfileRegenerateDto dto) {
        return ApiResponse.ok(knowledgeManageService.regenerateProfile(dto));
    }

    /**
     * 批量重新生成文档画像。
     *
     * 批量为多个文档重新生成画像，适用于大批量文档导入后统一生成画像的场景。
     *
     * @param dto 批量重新生成请求（包含文档 ID 列表）
     * @return 新生成的文档画像列表
     */
    @Operation(summary = "批量重新生成文档画像")
    @PostMapping("/document/profile/batch/regenerate")
    public ApiResponse<List<DocumentProfileVo>> batchRegenerateProfiles(@Valid @RequestBody DocumentProfileBatchRegenerateDto dto) {
        return ApiResponse.ok(knowledgeManageService.batchRegenerateProfiles(dto));
    }

    /**
     * 查询主题文档关联。
     *
     * 查询指定主题下的所有关联文档，或查询所有主题文档关联关系。
     * 用于前端管理主题与文档的绑定关系。
     *
     * @param dto 查询条件（可选，可按主题编码筛选）
     * @return 主题文档关联列表
     */
    @Operation(summary = "查询主题文档关联")
    @PostMapping("/topic/document/list")
    public ApiResponse<List<TopicDocumentRelationItemVo>> listTopicDocuments(@RequestBody(required = false) TopicDocumentRelationListQueryDto dto) {
        return ApiResponse.ok(knowledgeManageService.listTopicDocuments(dto == null ? new TopicDocumentRelationListQueryDto() : dto));
    }

    /**
     * 保存主题文档关联。
     *
     * 建立主题与文档之间的关联关系，可以附带关联分数和关联来源信息。
     * 关联关系决定了 RAG 检索时哪些文档属于哪个主题。
     *
     * @param dto 关联保存信息（主题编码、文档 ID、关联分数等）
     * @return 保存后的关联信息
     */
    @Operation(summary = "保存主题文档关联")
    @PostMapping("/topic/document/save")
    public ApiResponse<TopicDocumentRelationItemVo> saveTopicDocumentRelation(@Valid @RequestBody TopicDocumentRelationSaveDto dto) {
        return ApiResponse.ok(knowledgeManageService.saveTopicDocumentRelation(dto));
    }

    /**
     * 移除主题文档关联。
     *
     * 解除主题与文档之间的关联关系。
     *
     * @param dto 移除条件（主题编码、文档 ID）
     * @return 移除是否成功
     */
    @Operation(summary = "移除主题文档关联")
    @PostMapping("/topic/document/remove")
    public ApiResponse<Boolean> removeTopicDocumentRelation(@Valid @RequestBody TopicDocumentRelationRemoveDto dto) {
        return ApiResponse.ok(knowledgeManageService.removeTopicDocumentRelation(dto));
    }

    /**
     * 分页查询知识路由追踪。
     *
     * 知识路由追踪记录了每次用户提问时系统的路由决策过程，
     * 包括问题原文、改写后的问题、命中的知识范围、主题、文档等信息。
     *
     * 通过分析路由追踪数据，可以：
     * 
     *   评估路由策略的准确性。
     *   发现路由失败的案例并优化。
     *   统计各知识范围和主题的访问频率。
     * @param dto 查询条件（可选，可按时间范围、路由状态等筛选）
     * @return 路由追踪分页结果
     */
    @Operation(summary = "分页查询知识路由追踪")
    @PostMapping("/route/trace/page/query")
    public ApiResponse<KnowledgeRouteTracePageVo> queryRouteTracePage(@RequestBody(required = false) KnowledgeRouteTraceQueryDto dto) {
        return ApiResponse.ok(knowledgeManageService.queryRouteTracePage(dto == null ? new KnowledgeRouteTraceQueryDto() : dto));
    }
}
