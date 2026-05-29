package ai.knowhub.document.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import ai.knowhub.document.dto.DocumentIndexBuildDto;
import ai.knowhub.document.dto.DocumentChunkQueryDto;
import ai.knowhub.document.dto.DocumentChunkDetailQueryDto;
import ai.knowhub.document.dto.DocumentDetailQueryDto;
import ai.knowhub.document.dto.DocumentDeleteDto;
import ai.knowhub.document.dto.DocumentPageQueryDto;
import ai.knowhub.document.dto.DocumentStrategyConfirmDto;
import ai.knowhub.document.dto.DocumentStrategyPlanQueryDto;
import ai.knowhub.document.dto.DocumentTaskLogQueryDto;
import ai.knowhub.document.dto.DocumentUploadDto;
import ai.knowhub.document.service.DocumentManageService;
import ai.knowhub.document.vo.DocumentIndexBuildVo;
import ai.knowhub.document.vo.DocumentChunkQueryVo;
import ai.knowhub.document.vo.DocumentChunkDetailVo;
import ai.knowhub.document.vo.DocumentListItemVo;
import ai.knowhub.document.vo.DocumentDeleteVo;
import ai.knowhub.document.vo.DocumentPageQueryVo;
import ai.knowhub.document.vo.DocumentStrategyConfirmVo;
import ai.knowhub.document.vo.DocumentStrategyPlanQueryVo;
import ai.knowhub.document.vo.DocumentTaskLogQueryVo;
import ai.knowhub.document.vo.DocumentUploadVo;
import ai.knowhub.common.ApiResponse;
import ai.knowhub.web.ApiVersion;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理控制器。
 *
 * 本类是文档管理模块的 REST API 入口，负责接收前端的文档管理请求，
 * 并委托给 DocumentManageService 服务层处理。
 *
 * 文档管理的完整生命周期：
 * 
 *   <b>上传</b>（upload）：用户上传文档文件，系统存储到 MinIO 并投递 Kafka 解析任务。
 *   <b>策略推荐</b>（queryStrategyPlan）：系统根据文档特征自动推荐切块策略。
 *   <b>策略确认</b>（confirmStrategy）：用户确认或调整切块策略。
 *   <b>索引构建</b>（buildIndex）：用户确认策略后，触发向量索引和关键词索引的构建。
 *   <b>查看结果</b>（queryDocumentChunks）：查看文档的切块结果。
 *   <b>删除</b>（deleteDocument）：删除文档及其所有关联数据。
 * 设计模式：
 * 
 *   <b>MVC 模式</b>：Controller 负责请求路由，Service 负责业务逻辑，Data 负责数据传输。
 *   <b>统一响应封装</b>：所有接口返回 ApiResponse 统一格式，便于前端统一处理。
 *   <b>DTO/VO 分离</b>：请求参数使用 DTO（Data Transfer Object），响应数据使用 VO（Value Object），
 *       解耦了 API 接口与内部数据模型。
 * 注解说明：
 * 
 *   RestController：标记为 RESTful 控制器，自动将返回值序列化为 JSON。
 *   RequestMapping：定义基础路径前缀 /manage/document。
 *   Operation：Swagger/OpenAPI 文档注解，描述接口功能。
 *   Valid：启用参数校验（基于 Jakarta Bean Validation）。
 * 
 */
@RestController
@RequestMapping(ApiVersion.V1_MANAGE_DOCUMENT)
public class DocumentManageController {

    /**
     * 文档管理服务，包含所有文档管理的业务逻辑。
     * 通过构造器注入，遵循 Spring 的依赖注入最佳实践。
     */
    private final DocumentManageService documentManageService;

    /**
     * 构造器注入文档管理服务。
     *
     * @param documentManageService 文档管理服务实例
     */
    public DocumentManageController(DocumentManageService documentManageService) {
        this.documentManageService = documentManageService;
    }

    /**
     * 上传文档并投递解析任务。
     *
     * 这是文档管理的入口接口。接收用户上传的文件和可选的元数据，
     * 将文件存储到 MinIO 对象存储中，然后通过 Kafka 投递异步解析任务。
     *
     * 注意：此接口只负责「接收文件」和「入队」，真正的文档解析（提取文本、
     * 识别结构、切块、向量化）会在 Kafka 消费者中异步完成。
     *
     * 使用 multipart/form-data 格式接收请求：
     * 
     *   file：上传的文档文件（必填）。
     *   meta：文档元数据（可选），如知识范围、业务分类、标签等。
     * @param file 上传的文档文件
     * @param dto  文档元数据（可选，为 null 时使用默认值）
     * @return 上传结果，包含文档 ID 和任务 ID
     */
    @Operation(summary = "上传文档并投递解析任务")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentUploadVo> upload(@RequestPart("file") MultipartFile file,
                                                @Valid @RequestPart(value = "meta", required = false) DocumentUploadDto dto) {

        // 上传接口只做接收和入队，真正的解析、切块、向量化会在 Kafka 异步任务中完成。
        return ApiResponse.ok(documentManageService.upload(file, dto == null ? new DocumentUploadDto() : dto));
    }

    /**
     * 分页查询文档列表。
     *
     * 支持按文档名称、状态、知识范围等条件进行分页查询。
     * 返回文档的基本信息列表，用于前端的文档管理页面展示。
     *
     * @param dto 分页查询条件（页码、每页大小、筛选条件等）
     * @return 分页结果，包含文档列表和总数
     */
    @Operation(summary = "分页查询文档列表")
    @PostMapping("/page/query")
    public ApiResponse<DocumentPageQueryVo> queryDocumentPage(@Valid @RequestBody DocumentPageQueryDto dto) {
        return ApiResponse.ok(documentManageService.queryDocumentPage(dto));
    }

    /**
     * 查询文档详情。
     *
     * 根据文档 ID 查询单个文档的完整信息，包括解析状态、策略状态、
     * 索引状态、切块数量等。
     *
     * @param dto 查询条件（包含文档 ID）
     * @return 文档详情信息
     */
    @Operation(summary = "查询文档详情")
    @PostMapping("/detail/query")
    public ApiResponse<DocumentListItemVo> queryDocumentDetail(@Valid @RequestBody DocumentDetailQueryDto dto) {
        return ApiResponse.ok(documentManageService.queryDocumentDetail(dto));
    }

    /**
     * 删除文档及其关联数据。
     *
     * 执行文档的级联删除操作，包括：
     * 
     *   MinIO 中的原始文件和解析文本。
     *   数据库中的文档记录、切块记录、结构节点、策略方案等。
     *   Elasticsearch 中的索引数据。
     *   pgvector 中的向量数据。
     *   Neo4j 中的图结构数据（如适用）。
     * @param dto 删除条件（包含文档 ID）
     * @return 删除结果
     */
    @Operation(summary = "删除文档及其关联数据")
    @PostMapping("/delete")
    public ApiResponse<DocumentDeleteVo> deleteDocument(@Valid @RequestBody DocumentDeleteDto dto) {
        return ApiResponse.ok(documentManageService.deleteDocument(dto));
    }

    /**
     * 查询文档策略推荐结果。
     *
     * 系统会根据文档的特征（长度、结构复杂度、内容类型等）
     * 自动推荐最优的切块策略。此接口查询推荐结果供用户查看和确认。
     *
     * @param dto 查询条件（包含文档 ID）
     * @return 策略推荐方案详情
     */
    @Operation(summary = "查询文档策略推荐结果")
    @PostMapping("/strategy/plan/query")
    public ApiResponse<DocumentStrategyPlanQueryVo> queryStrategyPlan(@Valid @RequestBody DocumentStrategyPlanQueryDto dto) {
        return ApiResponse.ok(documentManageService.queryStrategyPlan(dto));
    }

    /**
     * 确认文档策略方案。
     *
     * 用户查看策略推荐结果后，可以确认接受推荐方案，也可以调整策略参数。
     * 确认后系统会按照用户选择的策略执行文档切块。
     *
     * @param dto 策略确认信息（包含文档 ID、策略选择等）
     * @return 确认结果
     */
    @Operation(summary = "确认文档策略方案")
    @PostMapping("/strategy/confirm")
    public ApiResponse<DocumentStrategyConfirmVo> confirmStrategy(@Valid @RequestBody DocumentStrategyConfirmDto dto) {
        return ApiResponse.ok(documentManageService.confirmStrategy(dto));
    }

    /**
     * 执行文档索引构建。
     *
     * 在用户确认切块策略后，调用此接口触发索引构建流程。
     * 索引构建包括：
     * 
     *   将切块文本写入 Elasticsearch 关键词索引。
     *   将切块文本通过 Embedding 模型转换为向量，写入 pgvector。
     *   将文档结构信息写入 Elasticsearch 导航索引。
     *   将路由信息写入 Elasticsearch 知识路由索引。
     * 注意：必须先确认策略才能构建索引，避免用错误策略生成不可用的知识库。
     *
     * @param dto 索引构建请求（包含文档 ID）
     * @return 索引构建结果
     */
    @Operation(summary = "执行文档索引构建")
    @PostMapping("/index/build")
    public ApiResponse<DocumentIndexBuildVo> buildIndex(@Valid @RequestBody DocumentIndexBuildDto dto) {
        // 用户确认切块策略后才允许构建索引，避免用错误策略生成不可用的知识库。
        return ApiResponse.ok(documentManageService.buildIndex(dto));
    }

    /**
     * 查询文档 chunk 列表。
     *
     * 查询指定文档的所有切块（chunk）列表，用于前端展示文档的切块结果。
     * 用户可以通过此接口检查切块质量，判断是否需要调整切块策略。
     *
     * @param dto 查询条件（包含文档 ID、分页参数等）
     * @return 切块列表和分页信息
     */
    @Operation(summary = "查询文档 chunk 列表")
    @PostMapping("/chunk/query")
    public ApiResponse<DocumentChunkQueryVo> queryDocumentChunks(@Valid @RequestBody DocumentChunkQueryDto dto) {
        return ApiResponse.ok(documentManageService.queryDocumentChunks(dto));
    }

    /**
     * 查询单个文档 chunk 详情。
     *
     * 查询单个切块的详细信息，包括切块文本内容、所属章节路径、
     * 字符数、Token 数、向量化状态等。
     *
     * @param dto 查询条件（包含切块 ID 或文档 ID + 切块序号）
     * @return 切块详情信息
     */
    @Operation(summary = "查询单个文档 chunk 详情")
    @PostMapping("/chunk/detail/query")
    public ApiResponse<DocumentChunkDetailVo> queryDocumentChunkDetail(@Valid @RequestBody DocumentChunkDetailQueryDto dto) {
        return ApiResponse.ok(documentManageService.queryDocumentChunkDetail(dto));
    }

    /**
     * 查询任务执行日志。
     *
     * 查询文档解析和索引构建任务的执行日志，用于排查问题和监控任务进度。
     * 日志包含每个阶段的执行状态、耗时、错误信息等。
     *
     * @param dto 查询条件（包含文档 ID 或任务 ID）
     * @return 任务日志列表
     */
    @Operation(summary = "查询任务执行日志")
    @PostMapping("/task/log/query")
    public ApiResponse<DocumentTaskLogQueryVo> queryTaskLogs(@Valid @RequestBody DocumentTaskLogQueryDto dto) {
        return ApiResponse.ok(documentManageService.queryTaskLogs(dto));
    }

}
