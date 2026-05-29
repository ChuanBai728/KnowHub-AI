package ai.knowhub.document.service;

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
import ai.knowhub.document.vo.DocumentIndexBuildVo;
import ai.knowhub.document.vo.DocumentChunkQueryVo;
import ai.knowhub.document.vo.DocumentChunkDetailVo;
import ai.knowhub.document.vo.DocumentDeleteVo;
import ai.knowhub.document.vo.DocumentListItemVo;
import ai.knowhub.document.vo.DocumentPageQueryVo;
import ai.knowhub.document.vo.DocumentStrategyConfirmVo;
import ai.knowhub.document.vo.DocumentStrategyPlanQueryVo;
import ai.knowhub.document.vo.DocumentTaskLogQueryVo;
import ai.knowhub.document.vo.DocumentUploadVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 【文档管理服务接口】
 *
 * 作用：提供文档全生命周期管理的统一服务接口，是文档管理模块的核心门面（Facade）。
 * 涵盖文档的上传、查询、删除、策略确认、索引构建、分块查询、任务日志等完整操作。
 *
 * 架构角色：
 *   - 属于文档管理模块的"门面层（Facade Layer）"
 *   - 对上层（Controller）暴露简洁统一的 API
 *   - 对下层协调多个专业服务（存储、解析、策略、索引等）完成复杂业务
 *
 * 设计模式：
 *   - 门面模式（Facade Pattern）：将文档管理的多个子系统封装为统一接口
 *   - 数据传输对象模式（DTO/VO）：使用 DTO 接收请求参数，VO 返回响应数据，
 *     实现请求与响应的数据解耦
 *
 * 命名约定：
 *   - DTO（Data Transfer Object）：前端传入的请求参数对象
 *   - VO（Value Object）：返回给前端的返回值对象
 */
public interface DocumentManageService {

    /**
     * 上传文档
     *
     * 功能说明：
     *   - 接收用户上传的文件（支持 PDF、Word、Markdown 等格式）
     *   - 将原始文件存储到对象存储（如 MinIO）
     *   - 创建文档记录并触发异步解析流程
     *   - 返回文档的基本信息和上传结果
     *
     * @param file 上传的文件，通过 Spring 的 MultipartFile 接口接收
     * @param dto  文档上传的附加参数（如文档名称、知识域等）
     * @return 上传结果，包含文档ID、文件名、上传状态等信息
     */
    DocumentUploadVo upload(MultipartFile file, DocumentUploadDto dto);

    /**
     * 分页查询文档列表
     *
     * 功能说明：
     *   - 支持按条件分页查询文档列表
     *   - 可按文档名称、状态、知识域等条件过滤
     *   - 返回分页结果，包含总数和当前页数据
     *
     * @param dto 分页查询参数（页码、每页大小、过滤条件等）
     * @return 分页查询结果，包含文档列表和分页信息
     */
    DocumentPageQueryVo queryDocumentPage(DocumentPageQueryDto dto);

    /**
     * 查询文档详情
     *
     * @param dto 查询参数，包含文档ID等标识信息
     * @return 文档的详细信息，包含元数据、处理状态等
     */
    DocumentListItemVo queryDocumentDetail(DocumentDetailQueryDto dto);

    /**
     * 删除文档
     *
     * 功能说明：
     *   - 删除文档记录及其关联数据
     *   - 同时清理对象存储中的原始文件和解析文本
     *   - 清理向量数据库和关键词索引中的相关数据
     *
     * @param dto 删除参数，包含文档ID等标识信息
     * @return 删除操作的结果
     */
    DocumentDeleteVo deleteDocument(DocumentDeleteDto dto);

    /**
     * 查询文档的分块策略计划
     *
     * 功能说明：
     *   - 文档解析完成后，系统会推荐一套分块策略（Strategy Plan）
     *   - 策略包含分块方式、块大小、重叠度等参数
     *   - 用户可以查看并确认或修改策略后再进行索引构建
     *
     * @param dto 查询参数，包含文档ID
     * @return 策略计划的详细信息
     */
    DocumentStrategyPlanQueryVo queryStrategyPlan(DocumentStrategyPlanQueryDto dto);

    /**
     * 确认分块策略
     *
     * 功能说明：
     *   - 用户查看系统推荐的分块策略后，可以确认或自定义修改
     *   - 确认后的策略将用于后续的索引构建
     *   - 此方法保存用户的策略选择
     *
     * @param dto 确认参数，包含用户选择或修改后的策略配置
     * @return 策略确认结果
     */
    DocumentStrategyConfirmVo confirmStrategy(DocumentStrategyConfirmDto dto);

    /**
     * 触发索引构建
     *
     * 功能说明：
     *   - 用户确认策略后，调用此方法触发索引构建
     *   - 系统会根据确认的策略对文档进行分块、向量化、索引写入
     *   - 索引构建是异步执行的，此方法返回构建任务的初始状态
     *
     * @param dto 索引构建参数，包含文档ID和策略计划ID
     * @return 索引构建的初始结果
     */
    DocumentIndexBuildVo buildIndex(DocumentIndexBuildDto dto);

    /**
     * 查询文档的分块列表
     *
     * 功能说明：
     *   - 查看文档被分割成哪些文本块（Chunk）
     *   - 每个块包含文本内容、位置信息、所属章节等
     *   - 用于调试和验证分块效果
     *
     * @param dto 分块查询参数（文档ID、分页信息等）
     * @return 分块列表查询结果
     */
    DocumentChunkQueryVo queryDocumentChunks(DocumentChunkQueryDto dto);

    /**
     * 查询单个分块的详细信息
     *
     * @param dto 分块详情查询参数（分块ID等）
     * @return 分块的详细信息，包含完整文本、元数据等
     */
    DocumentChunkDetailVo queryDocumentChunkDetail(DocumentChunkDetailQueryDto dto);

    /**
     * 查询文档处理任务日志
     *
     * 功能说明：
     *   - 查看文档处理过程中产生的任务日志
     *   - 包含解析、索引构建等各阶段的执行记录
     *   - 用于排查问题和监控处理进度
     *
     * @param dto 任务日志查询参数（文档ID、任务ID等）
     * @return 任务日志列表
     */
    DocumentTaskLogQueryVo queryTaskLogs(DocumentTaskLogQueryDto dto);
}
