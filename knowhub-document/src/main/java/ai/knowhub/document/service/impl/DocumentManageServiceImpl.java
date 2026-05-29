package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentChunk;
import ai.knowhub.document.data.KnowHubDocumentParentBlock;
import ai.knowhub.document.data.KnowHubDocumentProfile;
import ai.knowhub.document.data.KnowHubDocumentStrategyPlan;
import ai.knowhub.document.data.KnowHubDocumentStrategyStep;
import ai.knowhub.document.data.KnowHubDocumentTask;
import ai.knowhub.document.data.KnowHubDocumentTaskLog;
import ai.knowhub.document.data.KnowHubTopicDocumentRelation;
import ai.knowhub.document.dto.DocumentChunkQueryDto;
import ai.knowhub.document.dto.DocumentChunkDetailQueryDto;
import ai.knowhub.document.dto.DocumentDeleteDto;
import ai.knowhub.document.dto.DocumentDetailQueryDto;
import ai.knowhub.document.dto.DocumentIndexBuildDto;
import ai.knowhub.document.dto.DocumentPageQueryDto;
import ai.knowhub.document.dto.DocumentStrategyConfirmDto;
import ai.knowhub.document.dto.DocumentStrategyPlanQueryDto;
import ai.knowhub.document.dto.DocumentStrategyStepItemDto;
import ai.knowhub.document.dto.DocumentTaskLogQueryDto;
import ai.knowhub.document.dto.DocumentUploadDto;
import ai.knowhub.document.mapper.KnowHubDocumentMapper;
import ai.knowhub.document.mapper.KnowHubDocumentChunkMapper;
import ai.knowhub.document.mapper.KnowHubDocumentParentBlockMapper;
import ai.knowhub.document.mapper.KnowHubDocumentProfileMapper;
import ai.knowhub.document.mapper.KnowHubDocumentStrategyPlanMapper;
import ai.knowhub.document.mapper.KnowHubDocumentStrategyStepMapper;
import ai.knowhub.document.mapper.KnowHubDocumentTaskLogMapper;
import ai.knowhub.document.mapper.KnowHubDocumentTaskMapper;
import ai.knowhub.document.mapper.KnowHubTopicDocumentRelationMapper;
import ai.knowhub.document.mq.DocumentKafkaProducer;
import ai.knowhub.document.mq.message.DocumentIndexBuildMessage;
import ai.knowhub.document.mq.message.DocumentParseRouteMessage;
import ai.knowhub.document.service.DocumentManageService;
import ai.knowhub.document.service.DocumentNavigationIndexService;
import ai.knowhub.document.service.DocumentStorageService;
import ai.knowhub.document.service.DocumentStructureGraphProjectionService;
import ai.knowhub.document.service.DocumentStructureNodeService;
import ai.knowhub.document.service.DocumentStrategyService;
import ai.knowhub.document.service.DocumentTaskLogService;
import ai.knowhub.document.service.DocumentVectorGateway;
import ai.knowhub.document.service.KnowledgeRouteIndexService;
import ai.knowhub.document.service.keyword.DocumentKeywordSearchGateway;
import ai.knowhub.document.support.StoredObjectInfo;
import ai.knowhub.document.vo.DocumentChunkItemVo;
import ai.knowhub.document.vo.DocumentChunkQueryVo;
import ai.knowhub.document.vo.DocumentChunkDetailVo;
import ai.knowhub.document.vo.DocumentDeleteVo;
import ai.knowhub.document.vo.DocumentIndexBuildVo;
import ai.knowhub.document.vo.DocumentListItemVo;
import ai.knowhub.document.vo.DocumentParentBlockItemVo;
import ai.knowhub.document.vo.DocumentPageQueryVo;
import ai.knowhub.document.vo.DocumentStrategyConfirmVo;
import ai.knowhub.document.vo.DocumentStrategyPipelineVo;
import ai.knowhub.document.vo.DocumentStrategyPlanQueryVo;
import ai.knowhub.document.vo.DocumentStrategyPlanVo;
import ai.knowhub.document.vo.DocumentStrategyStepVo;
import ai.knowhub.document.vo.DocumentTaskLogQueryVo;
import ai.knowhub.document.vo.DocumentTaskLogVo;
import ai.knowhub.document.vo.DocumentUploadVo;
import ai.knowhub.enums.BaseCode;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.enums.DocumentChunkSourceTypeEnum;
import ai.knowhub.enums.DocumentFileTypeEnum;
import ai.knowhub.enums.DocumentIndexStatusEnum;
import ai.knowhub.enums.DocumentLogLevelEnum;
import ai.knowhub.enums.DocumentManageCode;
import ai.knowhub.enums.DocumentOperatorTypeEnum;
import ai.knowhub.enums.DocumentParseStatusEnum;
import ai.knowhub.enums.DocumentPlanSourceEnum;
import ai.knowhub.enums.DocumentPlanStatusEnum;
import ai.knowhub.enums.DocumentStorageTypeEnum;
import ai.knowhub.enums.DocumentStrategyExecuteStatusEnum;
import ai.knowhub.enums.DocumentStrategyPipelineTypeEnum;
import ai.knowhub.enums.DocumentStrategyRoleEnum;
import ai.knowhub.enums.DocumentStrategySourceTypeEnum;
import ai.knowhub.enums.DocumentStrategyStatusEnum;
import ai.knowhub.enums.DocumentStrategyTypeEnum;
import ai.knowhub.enums.DocumentTaskEventTypeEnum;
import ai.knowhub.enums.DocumentTaskStageEnum;
import ai.knowhub.enums.DocumentTaskStatusEnum;
import ai.knowhub.enums.DocumentTaskTypeEnum;
import ai.knowhub.enums.DocumentTriggerSourceEnum;
import ai.knowhub.enums.DocumentVectorStatusEnum;
import ai.knowhub.exception.KnowHubFrameException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 【文档管理服务实现 - 核心业务入口】
 *
 * 这个类是 DocumentManageService 接口的核心实现，是整个文档管理模块的业务入口。
 * 它负责文档的完整生命周期管理：上传 -> 解析 -> 策略推荐 -> 策略确认 -> 索引构建 -> 查询 -> 删除。
 *
 * 核心职责：
 *   1. 文档上传（upload）：接收文件，存储到 MinIO，创建文档记录和解析任务，通过 Kafka 异步触发解析
 *   2. 文档查询（queryDocumentPage / queryDocumentDetail）：分页查询文档列表和详情
 *   3. 文档删除（deleteDocument）：级联清理对象存储、向量索引、关键词索引、导航索引和数据库记录
 *   4. 策略查询（queryStrategyPlan）：查看系统推荐的切块策略方案
 *   5. 策略确认（confirmStrategy）：用户确认或调整切块策略，支持创建新版本方案
 *   6. 索引构建（buildIndex）：触发文档的切块和向量化，通过 Kafka 异步执行
 *   7. 任务日志查询（queryTaskLogs）：查看任务处理过程中的日志
 *   8. Chunk 查询（queryDocumentChunks / queryDocumentChunkDetail）：查看切块结果
 *
 * 设计要点：
 *   - 异步处理：解析和索引构建都通过 Kafka 异步执行，HTTP 接口只负责创建任务和返回任务编号
 *   - 事务管理：文档和任务必须在同一个事务里创建，避免 Kafka 消息到了却找不到任务记录
 *   - 级联删除：删除文档时要同时清理所有相关数据（对象存储、向量索引、ES索引、数据库记录）
 *   - 策略版本化：每次用户调整策略都会创建新版本方案，旧方案标记为 DISCARDED
 *
 * 依赖的服务：
 *   - DocumentStorageService：MinIO 文件存储
 *   - DocumentStrategyService：切块策略推荐和执行
 *   - DocumentTaskLogService：任务日志记录
 *   - DocumentVectorGateway：pgvector 向量存储
 *   - DocumentKeywordSearchGateway：Elasticsearch 关键词索引（可选）
 *   - DocumentNavigationIndexService：ES 导航索引（可选）
 *   - DocumentStructureGraphProjectionService：Neo4j 图投影（可选）
 *   - KnowledgeRouteIndexService：知识路由索引（可选）
 *   - DocumentKafkaProducer：Kafka 消息生产者
 */
@Slf4j
@AllArgsConstructor
@Service
public class DocumentManageServiceImpl implements DocumentManageService {

    /** 文档 Mapper */
    private final KnowHubDocumentMapper documentMapper;

    /** 策略方案 Mapper */
    private final KnowHubDocumentStrategyPlanMapper planMapper;

    /** 策略步骤 Mapper */
    private final KnowHubDocumentStrategyStepMapper stepMapper;

    /** 文档任务 Mapper */
    private final KnowHubDocumentTaskMapper taskMapper;

    /** 任务日志 Mapper */
    private final KnowHubDocumentTaskLogMapper taskLogMapper;

    /** Chunk Mapper */
    private final KnowHubDocumentChunkMapper chunkMapper;

    /** 父块 Mapper */
    private final KnowHubDocumentParentBlockMapper parentBlockMapper;

    /** 文档画像 Mapper */
    private final KnowHubDocumentProfileMapper documentProfileMapper;

    /** 主题-文档关联 Mapper */
    private final KnowHubTopicDocumentRelationMapper topicDocumentRelationMapper;

    /** 文件存储服务（MinIO 实现） */
    private final DocumentStorageService storageService;

    /** 文档结构节点服务 */
    private final DocumentStructureNodeService structureNodeService;

    /** 切块策略服务 */
    private final DocumentStrategyService strategyService;

    /** 任务日志服务 */
    private final DocumentTaskLogService taskLogService;

    /** 向量存储网关（pgvector 实现） */
    private final DocumentVectorGateway vectorGateway;

    /** 关键词搜索网关的延迟提供者（Elasticsearch 实现，可能未启用） */
    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;

    /** 导航索引服务的延迟提供者（可能未启用） */
    private final ObjectProvider<DocumentNavigationIndexService> navigationIndexServiceProvider;

    /** Neo4j 结构图投影服务的延迟提供者（可能未启用） */
    private final ObjectProvider<DocumentStructureGraphProjectionService> graphProjectionServiceProvider;

    /** 知识路由索引服务的延迟提供者（可能未启用） */
    private final ObjectProvider<KnowledgeRouteIndexService> knowledgeRouteIndexServiceProvider;

    /** Kafka 消息生产者，用于发送解析和索引构建的异步消息 */
    private final DocumentKafkaProducer kafkaProducer;

    /** 编程式事务模板，用于在非注解场景下管理事务 */
    private final TransactionTemplate transactionTemplate;

    /** 百度 UID 生成器（基于百度 UID的分布式ID生成） */
    private final UidGenerator uidGenerator;

    /**
     * 上传文档。
     *
     * 流程：
     *   1. 校验文件是否为空、文件名是否合法、文件类型是否支持
     *   2. 将原始文件上传到 MinIO 对象存储
     *   3. 在数据库中创建文档主记录（状态为"解析中"）
     *   4. 创建解析任务记录（状态为"新建"）
     *   5. 记录任务日志
     *   6. 事务提交后，通过 Kafka 发送解析消息（异步触发解析流程）
     *
     * @param file 上传的文件
     * @param dto  上传参数（文档名称、知识范围、业务分类、标签等）
     * @return 上传结果（文档ID、任务ID、文档名称、各阶段状态）
     */
    @Override
    public DocumentUploadVo upload(MultipartFile file, DocumentUploadDto dto) {

        // 上传阶段只建立"文档主记录 + 解析任务"，不要在 HTTP 请求里同步做重解析。
        if (file == null || file.isEmpty()) {
            throw new KnowHubFrameException(DocumentManageCode.EMPTY_FILE_CONTENT.getCode(),
                DocumentManageCode.EMPTY_FILE_CONTENT.getMsg());
        }

        String originalFileName = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFileName)) {
            throw new KnowHubFrameException(DocumentManageCode.UNSUPPORTED_FILE_TYPE.getCode(),
                "上传文件缺少原始文件名，无法识别文件类型。");
        }

        // 根据文件扩展名判断文件类型（PDF、DOC、DOCX、MD、TXT、HTML）
        DocumentFileTypeEnum fileType = DocumentFileTypeEnum.fromFileName(originalFileName);
        if (fileType == null) {
            throw new KnowHubFrameException(DocumentManageCode.UNSUPPORTED_FILE_TYPE.getCode(),
                DocumentManageCode.UNSUPPORTED_FILE_TYPE.getMsg());
        }

        byte[] fileBytes = getFileBytes(file);
        Long documentId = uidGenerator.getUid();

        // 原始文件先进入对象存储，数据库只保存桶名、对象名、URL 和状态等元数据。
        StoredObjectInfo storedObjectInfo = storageService.uploadOriginalFile(
                documentId, originalFileName, fileBytes, file.getContentType());

        // 构建文档主记录
        KnowHubDocument document = new KnowHubDocument();
        document.setId(documentId);
        document.setDocumentName(StrUtil.isNotBlank(dto.getDocumentName()) ? dto.getDocumentName() : originalFileName);
        document.setOriginalFileName(originalFileName);
        document.setFileType(fileType.getCode());
        document.setMimeType(file.getContentType());
        document.setFileSize((long) fileBytes.length);
        document.setStorageType(DocumentStorageTypeEnum.MINIO.getCode());
        document.setBucketName(storedObjectInfo.getBucketName());
        document.setObjectName(storedObjectInfo.getObjectName());
        document.setObjectUrl(storedObjectInfo.getObjectUrl());
        document.setParseStatus(DocumentParseStatusEnum.PARSING.getCode());
        document.setStrategyStatus(DocumentStrategyStatusEnum.WAIT_RECOMMEND.getCode());
        document.setIndexStatus(DocumentIndexStatusEnum.WAIT_BUILD.getCode());
        document.setCharCount(0);
        document.setTokenCount(0);

        document.setKnowledgeScopeCode(StrUtil.trimToNull(dto.getKnowledgeScopeCode()));
        document.setKnowledgeScopeName(StrUtil.trimToNull(dto.getKnowledgeScopeName()));
        document.setBusinessCategory(StrUtil.trimToNull(dto.getBusinessCategory()));
        document.setDocumentTags(StrUtil.trimToNull(dto.getDocumentTags()));
        document.setStatus(BusinessStatus.YES.getCode());

        // 构建解析任务记录
        Long taskId = uidGenerator.getUid();
        KnowHubDocumentTask task = new KnowHubDocumentTask();
        task.setId(taskId);
        task.setDocumentId(documentId);
        task.setTaskType(DocumentTaskTypeEnum.PARSE_ROUTE.getCode());
        task.setTaskStatus(DocumentTaskStatusEnum.NEW.getCode());
        task.setCurrentStage(DocumentTaskStageEnum.FILE_UPLOAD.getCode());
        Long operatorId = parseOptionalLong(dto.getOperatorId());
        task.setTriggerSource(resolveTriggerSource(operatorId));
        task.setRetryCount(0);
        task.setStatus(BusinessStatus.YES.getCode());

        // 在同一个事务中创建文档和任务，避免 Kafka 消息到了却找不到任务记录
        DocumentUploadVo uploadVo = transactionTemplate.execute(status -> {
            // 文档和任务必须在同一个事务里创建，避免 Kafka 消息到了却找不到任务记录。
            documentMapper.insert(document);
            taskMapper.insert(task);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.FILE_UPLOAD.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                resolveOperatorType(operatorId),
                operatorId,
                "文件上传完成，已进入解析与策略推荐队列。",
                Map.of("originalFileName", originalFileName, "fileSize", fileBytes.length));

            return new DocumentUploadVo(documentId, taskId, document.getDocumentName(),
                document.getParseStatus(), document.getStrategyStatus(), document.getIndexStatus());
        });

        // 事务提交后再投递解析消息，后续由 DocumentKafkaConsumer 触发异步解析。
        kafkaProducer.sendParseRoute(new DocumentParseRouteMessage(documentId, taskId));

        return uploadVo;
    }

    /**
     * 分页查询文档列表。
     *
     * @param dto 查询参数（页码、每页大小、关键词）
     * @return 分页结果（包含文档列表和各种状态的中文描述）
     */
    @Override
    public DocumentPageQueryVo queryDocumentPage(DocumentPageQueryDto dto) {

        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        String keyword = StrUtil.isNotBlank(dto.getKeyword()) ? dto.getKeyword().trim() : null;

        Page<KnowHubDocument> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<KnowHubDocument> wrapper = new LambdaQueryWrapper<KnowHubDocument>()
            .eq(KnowHubDocument::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(KnowHubDocument::getEditTime, KnowHubDocument::getId);

        // 支持按文档名称或原始文件名模糊搜索
        if (keyword != null) {
            wrapper.and(query -> query.like(KnowHubDocument::getDocumentName, keyword)
                .or()
                .like(KnowHubDocument::getOriginalFileName, keyword));
        }

        IPage<KnowHubDocument> resultPage = documentMapper.selectPage(page, wrapper);
        List<KnowHubDocument> documentList = resultPage.getRecords();
        // 批量获取每个文档的最新任务信息（避免 N+1 查询）
        Map<Long, KnowHubDocumentTask> latestTaskMap = getLatestTaskMap(documentList);

        List<DocumentListItemVo> records = documentList.stream()
            .map(document -> toDocumentListItemVo(document, latestTaskMap.get(document.getId())))
            .toList();

        return new DocumentPageQueryVo(pageNo, pageSize, resultPage.getTotal(), records);
    }

    /**
     * 查询单个文档的详情。
     *
     * @param dto 查询参数（包含文档ID）
     * @return 文档详情
     */
    @Override
    public DocumentListItemVo queryDocumentDetail(DocumentDetailQueryDto dto) {
        KnowHubDocument document = getDocumentOrThrow(dto.getDocumentId());
        KnowHubDocumentTask latestTask = getLatestTask(document.getId());
        return toDocumentListItemVo(document, latestTask);
    }

    /**
     * 删除文档。
     *
     * 级联清理顺序：
     *   1. 检查是否有进行中的任务（有则拒绝删除）
     *   2. 删除 MinIO 中的原始文件和解析文本
     *   3. 删除 pgvector 中的向量数据
     *   4. 删除 Elasticsearch 中的关键词索引
     *   5. 删除 ES 中的导航索引
     *   6. 删除知识路由索引中的文档快照
     *   7. 删除 Neo4j 中的图投影
     *   7. 删除数据库中的所有相关记录（画像、主题关联、父块、chunk、结构节点、任务日志、策略步骤、任务、方案、文档）
     *
     * @param dto 删除参数（包含文档ID）
     * @return 删除结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDeleteVo deleteDocument(DocumentDeleteDto dto) {
        // 删除文档要同时清理对象存储、向量索引、关键词索引、导航索引和数据库记录。
        Long documentId = parseRequiredLong(dto.getDocumentId(), "文档id");
        KnowHubDocument document = getDocumentOrThrow(documentId);

        // 检查是否有进行中的任务
        long activeTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<KnowHubDocumentTask>()
            .eq(KnowHubDocumentTask::getDocumentId, documentId)
            .eq(KnowHubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .in(KnowHubDocumentTask::getTaskStatus, DocumentTaskStatusEnum.NEW.getCode(), DocumentTaskStatusEnum.RUNNING.getCode()));
        if (activeTaskCount > 0) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(),
                "当前文档存在进行中的任务，请等待任务结束后再删除。");
        }

        // 删除 MinIO 中的文件
        storageService.deleteObjects(List.of(document.getObjectName(), document.getParseTextPath()));
        // 删除 pgvector 中的向量数据
        vectorGateway.deleteByDocumentId(documentId);

        // 删除 Elasticsearch 关键词索引（可选）
        DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
        if (keywordSearchGateway != null) {
            log.info("删除文档关键词索引: documentId={}", documentId);
            keywordSearchGateway.deleteByDocumentId(documentId);
        }
        // 删除 ES 导航索引（可选）
        DocumentNavigationIndexService navigationIndexService = navigationIndexServiceProvider.getIfAvailable();
        if (navigationIndexService != null) {
            log.info("删除文档导航索引: documentId={}", documentId);
            navigationIndexService.deleteByDocumentId(documentId);
        }
        // 删除知识路由索引中的文档快照（可选）
        KnowledgeRouteIndexService knowledgeRouteIndexService = knowledgeRouteIndexServiceProvider.getIfAvailable();
        if (knowledgeRouteIndexService != null) {
            log.info("删除知识路由索引中的文档快照: documentId={}", documentId);
            knowledgeRouteIndexService.deleteDocumentRoute(documentId);
        }
        // 删除 Neo4j 结构图投影（可选）
        DocumentStructureGraphProjectionService graphProjectionService = graphProjectionServiceProvider.getIfAvailable();
        if (graphProjectionService != null && graphProjectionService.enabled()) {
            log.info("删除 Neo4j 文档结构图投影: documentId={}", documentId);
            graphProjectionService.deleteByDocumentId(documentId);
        }
        // 删除数据库中的所有相关记录（按依赖关系逆序删除）
        documentProfileMapper.delete(new LambdaQueryWrapper<KnowHubDocumentProfile>()
            .eq(KnowHubDocumentProfile::getDocumentId, documentId));
        topicDocumentRelationMapper.delete(new LambdaQueryWrapper<KnowHubTopicDocumentRelation>()
            .eq(KnowHubTopicDocumentRelation::getDocumentId, documentId));
        parentBlockMapper.delete(new LambdaQueryWrapper<KnowHubDocumentParentBlock>()
            .eq(KnowHubDocumentParentBlock::getDocumentId, documentId));
        chunkMapper.delete(new LambdaQueryWrapper<KnowHubDocumentChunk>()
            .eq(KnowHubDocumentChunk::getDocumentId, documentId));
        structureNodeService.deleteByDocumentId(documentId);
        taskLogMapper.delete(new LambdaQueryWrapper<KnowHubDocumentTaskLog>()
            .eq(KnowHubDocumentTaskLog::getDocumentId, documentId));
        stepMapper.delete(new LambdaQueryWrapper<KnowHubDocumentStrategyStep>()
            .eq(KnowHubDocumentStrategyStep::getDocumentId, documentId));
        taskMapper.delete(new LambdaQueryWrapper<KnowHubDocumentTask>()
            .eq(KnowHubDocumentTask::getDocumentId, documentId));
        planMapper.delete(new LambdaQueryWrapper<KnowHubDocumentStrategyPlan>()
            .eq(KnowHubDocumentStrategyPlan::getDocumentId, documentId));
        documentMapper.deleteById(documentId);

        return new DocumentDeleteVo(documentId, document.getDocumentName());
    }

    /**
     * 查询文档的策略方案。
     *
     * @param dto 查询参数（包含文档ID）
     * @return 策略方案信息（包含方案详情、步骤列表、各阶段状态）
     */
    @Override
    public DocumentStrategyPlanQueryVo queryStrategyPlan(DocumentStrategyPlanQueryDto dto) {

        KnowHubDocument document = getDocumentOrThrow(dto.getDocumentId());
        DocumentStrategyPlanVo planVo = null;
        boolean planReady = false;

        // 如果文档有当前生效的方案，加载方案详情和步骤
        if (document.getCurrentPlanId() != null) {
            KnowHubDocumentStrategyPlan plan = planMapper.selectById(document.getCurrentPlanId());
            if (plan != null && Objects.equals(plan.getStatus(), BusinessStatus.YES.getCode())) {
                List<KnowHubDocumentStrategyStep> stepList = listStepByPlanId(plan.getId());
                planVo = toPlanVo(plan, stepList);
                planReady = true;
            }
        }

        return new DocumentStrategyPlanQueryVo(
            document.getId(),
            document.getDocumentName(),
            document.getParseStatus(),
            enumMsg(DocumentParseStatusEnum.getRc(document.getParseStatus())),
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus())),
            document.getParseErrorMsg(),
            planReady,
            planVo
        );
    }

    /**
     * 确认（或调整）文档的切块策略。
     *
     * 流程：
     *   1. 校验文档状态（必须解析成功）和基础方案是否存在
     *   2. 对比用户请求的策略步骤和基础方案的步骤
     *   3. 如果用户没有调整：直接将基础方案标记为"已确认"
     *   4. 如果用户有调整：将基础方案标记为"已废弃"，创建新版本方案
     *   5. 更新文档的当前方案ID和策略状态
     *   6. 记录任务日志
     *
     * @param dto 确认参数（文档ID、基础方案ID、父块策略步骤、子块策略步骤、调整说明）
     * @return 确认结果（包含是否被规范化、最终的策略流水线）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentStrategyConfirmVo confirmStrategy(DocumentStrategyConfirmDto dto) {

        KnowHubDocument document = getDocumentOrThrow(dto.getDocumentId());
        if (!Objects.equals(document.getParseStatus(), DocumentParseStatusEnum.PARSE_SUCCESS.getCode())) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(), "当前文档还未完成解析，不能确认策略。");
        }

        if (!Objects.equals(document.getCurrentPlanId(), dto.getBasePlanId())) {
            throw new KnowHubFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(), "当前文档的基础方案不存在或已切换。");
        }

        KnowHubDocumentStrategyPlan basePlan = planMapper.selectById(dto.getBasePlanId());
        if (basePlan == null || !Objects.equals(basePlan.getStatus(), BusinessStatus.YES.getCode())) {
            throw new KnowHubFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(),
                DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getMsg());
        }

        // 获取基础方案的步骤列表
        List<KnowHubDocumentStrategyStep> baseStepList = listStepByPlanId(basePlan.getId());
        // 提取用户请求的父块和子块策略类型列表
        List<Integer> requestParentTypeList = dto.getParentSteps().stream()
            .sorted(Comparator.comparing(item -> item.getStepNo() == null ? Integer.MAX_VALUE : item.getStepNo()))
            .map(DocumentStrategyStepItemDto::getStrategyType)
            .filter(Objects::nonNull)
            .toList();
        List<Integer> requestChildTypeList = dto.getChildSteps().stream()
            .sorted(Comparator.comparing(item -> item.getStepNo() == null ? Integer.MAX_VALUE : item.getStepNo()))
            .map(DocumentStrategyStepItemDto::getStrategyType)
            .filter(Objects::nonNull)
            .toList();

        // 规范化步骤（去重、校验策略类型有效性）
        List<KnowHubDocumentStrategyStep> normalizedStepList = strategyService.normalizeSteps(
            basePlan, baseStepList, requestParentTypeList, requestChildTypeList, dto.getDocumentId());

        List<Integer> normalizedParentTypeList = extractPipelineTypes(normalizedStepList, DocumentStrategyPipelineTypeEnum.PARENT);
        List<Integer> normalizedChildTypeList = extractPipelineTypes(normalizedStepList, DocumentStrategyPipelineTypeEnum.CHILD);

        if (normalizedParentTypeList.isEmpty()) {
            throw new KnowHubFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(), "父块流水线不能为空。");
        }
        if (normalizedChildTypeList.isEmpty()) {
            throw new KnowHubFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(), "子块流水线不能为空。");
        }

        if (normalizedStepList.isEmpty()) {
            throw new KnowHubFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(),
                DocumentManageCode.STRATEGY_STEP_EMPTY.getMsg());
        }

        // 对比基础方案和用户请求的差异
        List<Integer> baseParentTypeList = extractPipelineTypes(baseStepList, DocumentStrategyPipelineTypeEnum.PARENT);
        List<Integer> baseChildTypeList = extractPipelineTypes(baseStepList, DocumentStrategyPipelineTypeEnum.CHILD);
        List<Integer> requestDistinctParentTypeList = new LinkedHashSet<>(requestParentTypeList).stream().toList();
        List<Integer> requestDistinctChildTypeList = new LinkedHashSet<>(requestChildTypeList).stream().toList();

        // 判断用户请求是否被规范化（去重或过滤了无效类型）
        boolean normalized = !requestDistinctParentTypeList.equals(normalizedParentTypeList)
            || !requestDistinctChildTypeList.equals(normalizedChildTypeList);

        // 判断用户是否调整了策略（与基础方案不同）
        boolean changed = !baseParentTypeList.equals(normalizedParentTypeList)
            || !baseChildTypeList.equals(normalizedChildTypeList);

        Long targetPlanId;
        Integer targetPlanVersion;
        List<KnowHubDocumentStrategyStep> targetStepList;

        if (!changed) {
            // 用户没有调整，直接确认基础方案
            basePlan.setPlanStatus(DocumentPlanStatusEnum.CONFIRMED.getCode());
            basePlan.setPlanSource(basePlan.getPlanSource() == null ? DocumentPlanSourceEnum.SYSTEM_RECOMMEND.getCode() : basePlan.getPlanSource());
            basePlan.setAdjustNote(dto.getAdjustNote());
            basePlan.setConfirmUserId(dto.getOperatorId());
            basePlan.setConfirmTime(new Date());
            planMapper.updateById(basePlan);
            targetPlanId = basePlan.getId();
            targetPlanVersion = basePlan.getPlanVersion();
            targetStepList = baseStepList;
        } else {
            // 用户调整了策略，废弃旧方案，创建新版本方案
            basePlan.setPlanStatus(DocumentPlanStatusEnum.DISCARDED.getCode());
            planMapper.updateById(basePlan);

            Long newPlanId = uidGenerator.getUid();
            Integer newPlanVersion = getNextPlanVersion(document.getId());
            KnowHubDocumentStrategyPlan newPlan = new KnowHubDocumentStrategyPlan();
            newPlan.setId(newPlanId);
            newPlan.setDocumentId(document.getId());
            newPlan.setPlanVersion(newPlanVersion);

            newPlan.setPlanSource(DocumentPlanSourceEnum.USER_ADJUST.getCode());
            newPlan.setPlanStatus(DocumentPlanStatusEnum.CONFIRMED.getCode());
            newPlan.setStrategyCount(normalizedStepList.size());
            newPlan.setStrategySnapshot(buildStrategySnapshot(normalizedStepList));
            newPlan.setRecommendReason(basePlan.getRecommendReason());
            newPlan.setAdjustNote(dto.getAdjustNote());
            newPlan.setConfirmUserId(dto.getOperatorId());
            newPlan.setConfirmTime(new Date());
            newPlan.setStatus(BusinessStatus.YES.getCode());
            planMapper.insert(newPlan);

            // 为新方案创建步骤记录
            for (KnowHubDocumentStrategyStep step : normalizedStepList) {
                step.setId(uidGenerator.getUid());
                step.setPlanId(newPlanId);
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }

            targetPlanId = newPlanId;
            targetPlanVersion = newPlanVersion;
            targetStepList = normalizedStepList;
        }

        // 更新文档的当前方案ID和策略状态
        document.setCurrentPlanId(targetPlanId);
        document.setStrategyStatus(DocumentStrategyStatusEnum.CONFIRMED.getCode());
        documentMapper.updateById(document);

        // 记录任务日志
        KnowHubDocumentTask latestParseTask = getLatestTask(document.getId(), DocumentTaskTypeEnum.PARSE_ROUTE.getCode());
        if (latestParseTask != null) {

            latestParseTask.setCurrentStage(DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode());
            taskMapper.updateById(latestParseTask);

            if (changed) {

                taskLogService.saveLog(latestParseTask.getId(), document.getId(),
                    DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode(),
                    DocumentTaskEventTypeEnum.USER_ADJUST.getCode(),
                    DocumentLogLevelEnum.INFO.getCode(),
                    resolveOperatorType(parseOptionalLong(dto.getOperatorId())),
                    parseOptionalLong(dto.getOperatorId()),
                    "用户调整了系统推荐策略。",
                    detail("parentStrategyTypes", normalizedParentTypeList,
                        "childStrategyTypes", normalizedChildTypeList,
                        "adjustNote", dto.getAdjustNote()));
            }

            taskLogService.saveLog(latestParseTask.getId(), document.getId(),
                DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode(),
                DocumentTaskEventTypeEnum.USER_CONFIRM.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                    resolveOperatorType(parseOptionalLong(dto.getOperatorId())),
                    parseOptionalLong(dto.getOperatorId()),
                    "用户已确认最终策略方案。",
                Map.of("planId", targetPlanId,
                    "parentStrategyTypes", normalizedParentTypeList,
                    "childStrategyTypes", normalizedChildTypeList));
        }

        return new DocumentStrategyConfirmVo(
            document.getId(),
            targetPlanId,
            targetPlanVersion,
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            normalized,
            toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, targetStepList),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, targetStepList)
        );
    }

    /**
     * 触发文档的索引构建。
     *
     * 前置条件：文档解析成功 + 策略已确认。
     * 流程：
     *   1. 校验文档状态和方案一致性
     *   2. 检查是否有正在运行的索引构建任务
     *   3. 创建索引构建任务记录
     *   4. 更新文档索引状态为"构建中"
     *   5. 通过 Kafka 发送索引构建消息（异步执行）
     *
     * @param dto 索引构建参数（文档ID、方案ID、操作人ID）
     * @return 索引构建任务信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentIndexBuildVo buildIndex(DocumentIndexBuildDto dto) {

        // 索引构建的前置条件是"解析成功 + 策略已确认"，否则无法知道该怎么切块。
        KnowHubDocument document = getDocumentOrThrow(dto.getDocumentId());
        if (!Objects.equals(document.getParseStatus(), DocumentParseStatusEnum.PARSE_SUCCESS.getCode())
            || !Objects.equals(document.getStrategyStatus(), DocumentStrategyStatusEnum.CONFIRMED.getCode())) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(), "当前文档尚未完成【解析成功 + 策略确认】，不能构建索引。");
        }

        if (!Objects.equals(document.getCurrentPlanId(), dto.getPlanId())) {
            throw new KnowHubFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(), "当前文档的生效方案与请求方案不一致。");
        }

        // 检查是否有正在运行的索引构建任务
        long runningTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<KnowHubDocumentTask>()
            .eq(KnowHubDocumentTask::getDocumentId, dto.getDocumentId())
            .eq(KnowHubDocumentTask::getTaskType, DocumentTaskTypeEnum.BUILD_INDEX.getCode())
            .in(KnowHubDocumentTask::getTaskStatus, DocumentTaskStatusEnum.NEW.getCode(), DocumentTaskStatusEnum.RUNNING.getCode())
            .eq(KnowHubDocumentTask::getStatus, BusinessStatus.YES.getCode()));
        if (runningTaskCount > 0) {
            throw new KnowHubFrameException(DocumentManageCode.INDEX_TASK_RUNNING.getCode(),
                DocumentManageCode.INDEX_TASK_RUNNING.getMsg());
        }

        KnowHubDocumentStrategyPlan plan = planMapper.selectById(dto.getPlanId());
        if (plan == null || !Objects.equals(plan.getStatus(), BusinessStatus.YES.getCode())) {
            throw new KnowHubFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(),
                DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getMsg());
        }

        // 创建索引构建任务
        Long taskId = uidGenerator.getUid();
        KnowHubDocumentTask task = new KnowHubDocumentTask();
        task.setId(taskId);
        task.setDocumentId(document.getId());
        task.setPlanId(dto.getPlanId());
        task.setTaskType(DocumentTaskTypeEnum.BUILD_INDEX.getCode());
        task.setTaskStatus(DocumentTaskStatusEnum.NEW.getCode());
        task.setCurrentStage(DocumentTaskStageEnum.CHUNK_EXECUTE.getCode());
        Long operatorId = parseOptionalLong(dto.getOperatorId());
        task.setTriggerSource(resolveTriggerSource(operatorId));
        task.setStrategySnapshot(plan.getStrategySnapshot());
        task.setRetryCount(0);
        task.setStatus(BusinessStatus.YES.getCode());
        taskMapper.insert(task);

        // 更新文档索引状态为"构建中"
        document.setIndexStatus(DocumentIndexStatusEnum.BUILDING.getCode());
        documentMapper.updateById(document);

        taskLogService.saveLog(taskId, document.getId(),
            DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
            DocumentTaskEventTypeEnum.START.getCode(),
            DocumentLogLevelEnum.INFO.getCode(),
            resolveOperatorType(operatorId),
            operatorId,
            "索引构建任务已创建，等待异步执行。",
            Map.of("planId", dto.getPlanId(), "strategySnapshot", plan.getStrategySnapshot()));

        // 和解析一样，索引构建也交给 Kafka 异步执行，HTTP 接口只返回任务编号。
        kafkaProducer.sendIndexBuild(new DocumentIndexBuildMessage(document.getId(), taskId, dto.getPlanId()));

        return new DocumentIndexBuildVo(
            document.getId(),
            taskId,
            task.getTaskType(),
            enumMsg(DocumentTaskTypeEnum.getRc(task.getTaskType())),
            task.getTaskStatus(),
            enumMsg(DocumentTaskStatusEnum.getRc(task.getTaskStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus()))
        );
    }

    /**
     * 查询任务日志（分页）。
     *
     * @param dto 查询参数（任务ID、页码、每页大小）
     * @return 任务日志分页结果
     */
    @Override
    public DocumentTaskLogQueryVo queryTaskLogs(DocumentTaskLogQueryDto dto) {

        KnowHubDocumentTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "任务不存在。");
        }

        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 20 : dto.getPageSize();
        Page<KnowHubDocumentTaskLog> page = new Page<>(pageNo, pageSize);

        IPage<KnowHubDocumentTaskLog> resultPage = taskLogMapper.selectPage(page,
            new LambdaQueryWrapper<KnowHubDocumentTaskLog>()
                .eq(KnowHubDocumentTaskLog::getTaskId, dto.getTaskId())
                .eq(KnowHubDocumentTaskLog::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(KnowHubDocumentTaskLog::getCreateTime, KnowHubDocumentTaskLog::getId));

        List<DocumentTaskLogVo> logVoList = resultPage.getRecords().stream()
            .map(this::toTaskLogVo)
            .toList();

        return new DocumentTaskLogQueryVo(
            task.getId(),
            task.getDocumentId(),
            task.getTaskType(),
            enumMsg(DocumentTaskTypeEnum.getRc(task.getTaskType())),
            task.getTaskStatus(),
            enumMsg(DocumentTaskStatusEnum.getRc(task.getTaskStatus())),
            task.getCurrentStage(),
            enumMsg(DocumentTaskStageEnum.getRc(task.getCurrentStage())),
            task.getStartTime(),
            task.getFinishTime(),
            task.getCostMillis(),
            task.getErrorCode(),
            task.getErrorMsg(),
            resultPage.getTotal(),
            logVoList
        );
    }

    /**
     * 分页查询文档的 chunk 列表。
     *
     * @param dto 查询参数（文档ID、任务ID、页码、每页大小）
     * @return chunk 分页结果
     */
    @Override
    public DocumentChunkQueryVo queryDocumentChunks(DocumentChunkQueryDto dto) {
        KnowHubDocument document = getDocumentOrThrow(dto.getDocumentId());
        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 20 : dto.getPageSize();

        // 解析有效的任务ID：优先用请求参数，其次用文档的 lastIndexTaskId，最后查最新的构建任务
        Long effectiveTaskId = resolveChunkTaskId(document, dto.getTaskId());
        if (effectiveTaskId == null) {
            return new DocumentChunkQueryVo(document.getId(), null, document.getCurrentPlanId(), pageNo, pageSize, 0L, List.of());
        }

        KnowHubDocumentTask task = taskMapper.selectById(effectiveTaskId);
        if (task == null
            || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())
            || !Objects.equals(task.getDocumentId(), document.getId())) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "切块任务不存在。");
        }

        Page<KnowHubDocumentChunk> page = new Page<>(pageNo, pageSize);
        IPage<KnowHubDocumentChunk> resultPage = chunkMapper.selectPage(page,
            new LambdaQueryWrapper<KnowHubDocumentChunk>()
                .eq(KnowHubDocumentChunk::getDocumentId, document.getId())
                .eq(KnowHubDocumentChunk::getTaskId, effectiveTaskId)
                .eq(KnowHubDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(KnowHubDocumentChunk::getChunkNo, KnowHubDocumentChunk::getId));

        // 批量加载父块信息（避免 N+1 查询）
        Map<Long, KnowHubDocumentParentBlock> parentBlockMap = listParentBlockMap(
            resultPage.getRecords().stream()
                .map(KnowHubDocumentChunk::getParentBlockId)
                .filter(Objects::nonNull)
                .toList()
        );

        List<DocumentChunkItemVo> records = resultPage.getRecords().stream()
            .map(chunk -> toDocumentChunkItemVo(chunk, parentBlockMap.get(chunk.getParentBlockId())))
            .toList();

        return new DocumentChunkQueryVo(
            document.getId(),
            effectiveTaskId,
            task.getPlanId(),
            pageNo,
            pageSize,
            resultPage.getTotal(),
            records
        );
    }

    /**
     * 查询单个 chunk 的详情（包含父块信息和兄弟 chunk 列表）。
     *
     * @param dto 查询参数（文档ID、任务ID、chunkID）
     * @return chunk 详情
     */
    @Override
    public DocumentChunkDetailVo queryDocumentChunkDetail(DocumentChunkDetailQueryDto dto) {
        KnowHubDocument document = getDocumentOrThrow(dto.getDocumentId());
        Long effectiveTaskId = resolveChunkTaskId(document, dto.getTaskId());
        if (effectiveTaskId == null) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "当前文档还没有可查看的 chunk 详情。");
        }

        KnowHubDocumentTask task = taskMapper.selectById(effectiveTaskId);
        if (task == null
            || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())
            || !Objects.equals(task.getDocumentId(), document.getId())) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "切块任务不存在。");
        }

        // 查询指定 chunk
        KnowHubDocumentChunk chunk = chunkMapper.selectOne(new LambdaQueryWrapper<KnowHubDocumentChunk>()
            .eq(KnowHubDocumentChunk::getId, dto.getChunkId())
            .eq(KnowHubDocumentChunk::getDocumentId, document.getId())
            .eq(KnowHubDocumentChunk::getTaskId, effectiveTaskId)
            .eq(KnowHubDocumentChunk::getStatus, BusinessStatus.YES.getCode())
            .last("limit 1"));
        if (chunk == null) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "chunk 详情不存在。");
        }

        // 查询所属父块
        KnowHubDocumentParentBlock parentBlock = chunk.getParentBlockId() == null
            ? null
            : parentBlockMapper.selectOne(new LambdaQueryWrapper<KnowHubDocumentParentBlock>()
                .eq(KnowHubDocumentParentBlock::getId, chunk.getParentBlockId())
                .eq(KnowHubDocumentParentBlock::getDocumentId, document.getId())
                .eq(KnowHubDocumentParentBlock::getTaskId, effectiveTaskId)
                .eq(KnowHubDocumentParentBlock::getStatus, BusinessStatus.YES.getCode())
                .last("limit 1"));

        // 查询同一父块下的所有兄弟 chunk
        List<KnowHubDocumentChunk> siblingChunkList = chunk.getParentBlockId() == null
            ? List.of(chunk)
            : chunkMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentChunk>()
                .eq(KnowHubDocumentChunk::getDocumentId, document.getId())
                .eq(KnowHubDocumentChunk::getTaskId, effectiveTaskId)
                .eq(KnowHubDocumentChunk::getParentBlockId, chunk.getParentBlockId())
                .eq(KnowHubDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(KnowHubDocumentChunk::getChunkNo, KnowHubDocumentChunk::getId));

        return new DocumentChunkDetailVo(
            document.getId(),
            effectiveTaskId,
            task.getPlanId(),
            toDocumentChunkItemVo(chunk, parentBlock),
            toDocumentParentBlockItemVo(parentBlock),
            siblingChunkList.stream()
                .map(item -> toDocumentChunkItemVo(item, parentBlock))
                .toList()
        );
    }

    // ========== 私有辅助方法 ==========

    /**
     * 根据文档ID获取文档记录，不存在或已删除时抛出异常。
     */
    private KnowHubDocument getDocumentOrThrow(Long documentId) {

        KnowHubDocument document = documentMapper.selectById(documentId);
        if (document == null || !Objects.equals(document.getStatus(), BusinessStatus.YES.getCode())) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(),
                DocumentManageCode.DOCUMENT_NOT_FOUND.getMsg());
        }
        return document;
    }

    /**
     * 获取指定方案的步骤列表，按流水线类型和步骤编号排序。
     * 排序规则：父块流水线优先，然后按 stepNo 升序。
     */
    private List<KnowHubDocumentStrategyStep> listStepByPlanId(Long planId) {
        List<KnowHubDocumentStrategyStep> stepList = stepMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentStrategyStep>()
            .eq(KnowHubDocumentStrategyStep::getPlanId, planId)
            .eq(KnowHubDocumentStrategyStep::getStatus, BusinessStatus.YES.getCode()));
        return stepList.stream()
            .sorted(Comparator
                .comparingInt((KnowHubDocumentStrategyStep step) -> pipelineOrder(step.getPipelineType()))
                .thenComparing(KnowHubDocumentStrategyStep::getStepNo)
                .thenComparing(KnowHubDocumentStrategyStep::getId))
            .toList();
    }

    /**
     * 获取文档的下一个方案版本号。
     */
    private Integer getNextPlanVersion(Long documentId) {

        KnowHubDocumentStrategyPlan latestPlan = planMapper.selectOne(new LambdaQueryWrapper<KnowHubDocumentStrategyPlan>()
            .eq(KnowHubDocumentStrategyPlan::getDocumentId, documentId)
            .eq(KnowHubDocumentStrategyPlan::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(KnowHubDocumentStrategyPlan::getPlanVersion)
            .last("limit 1"));
        return latestPlan == null ? 1 : latestPlan.getPlanVersion() + 1;
    }

    /**
     * 获取指定文档指定类型的最新任务。
     */
    private KnowHubDocumentTask getLatestTask(Long documentId, Integer taskType) {

        return taskMapper.selectOne(new LambdaQueryWrapper<KnowHubDocumentTask>()
            .eq(KnowHubDocumentTask::getDocumentId, documentId)
            .eq(KnowHubDocumentTask::getTaskType, taskType)
            .eq(KnowHubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(KnowHubDocumentTask::getId)
            .last("limit 1"));
    }

    /**
     * 获取指定文档的最新任务（不限类型）。
     */
    private KnowHubDocumentTask getLatestTask(Long documentId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<KnowHubDocumentTask>()
            .eq(KnowHubDocumentTask::getDocumentId, documentId)
            .eq(KnowHubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(KnowHubDocumentTask::getId)
            .last("limit 1"));
    }

    /**
     * 批量获取文档列表中每个文档的最新任务（避免 N+1 查询）。
     * 使用 LinkedHashMap 保证每个文档ID只保留第一个（最新的）任务。
     */
    private Map<Long, KnowHubDocumentTask> getLatestTaskMap(List<KnowHubDocument> documentList) {
        if (documentList == null || documentList.isEmpty()) {
            return Map.of();
        }

        Set<Long> documentIdSet = documentList.stream()
            .map(KnowHubDocument::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (documentIdSet.isEmpty()) {
            return Map.of();
        }

        List<KnowHubDocumentTask> taskList = taskMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentTask>()
            .in(KnowHubDocumentTask::getDocumentId, documentIdSet)
            .eq(KnowHubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(KnowHubDocumentTask::getId));

        Map<Long, KnowHubDocumentTask> latestTaskMap = new LinkedHashMap<>();
        for (KnowHubDocumentTask task : taskList) {
            latestTaskMap.putIfAbsent(task.getDocumentId(), task);
        }
        return latestTaskMap;
    }

    /**
     * 解析有效的 chunk 查询任务ID。
     * 优先级：请求参数 > document.lastIndexTaskId > 最新构建任务
     */
    private Long resolveChunkTaskId(KnowHubDocument document, Long requestedTaskId) {
        if (requestedTaskId != null) {
            return requestedTaskId;
        }
        if (document.getLastIndexTaskId() != null) {
            return document.getLastIndexTaskId();
        }
        KnowHubDocumentTask latestBuildTask = getLatestTask(document.getId(), DocumentTaskTypeEnum.BUILD_INDEX.getCode());
        return latestBuildTask == null ? null : latestBuildTask.getId();
    }

    /**
     * 将文档实体和最新任务转为列表项 VO。
     * 包含文档的所有状态枚举的中文描述。
     */
    private DocumentListItemVo toDocumentListItemVo(KnowHubDocument document, KnowHubDocumentTask latestTask) {
        return new DocumentListItemVo(
            document.getId(),
            document.getDocumentName(),
            document.getOriginalFileName(),
            document.getFileType(),
            enumMsg(DocumentFileTypeEnum.getRc(document.getFileType())),
            document.getFileSize(),
            document.getCharCount(),
            document.getTokenCount(),
            document.getParseStatus(),
            enumMsg(DocumentParseStatusEnum.getRc(document.getParseStatus())),
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus())),
            document.getParseErrorMsg(),
            document.getKnowledgeScopeCode(),
            document.getKnowledgeScopeName(),
            document.getBusinessCategory(),
            document.getDocumentTags(),
            document.getCurrentPlanId(),
            document.getLastIndexTaskId(),
            latestTask == null ? null : latestTask.getId(),
            latestTask == null ? null : latestTask.getTaskType(),
            latestTask == null ? "" : enumMsg(DocumentTaskTypeEnum.getRc(latestTask.getTaskType())),
            latestTask == null ? null : latestTask.getTaskStatus(),
            latestTask == null ? "" : enumMsg(DocumentTaskStatusEnum.getRc(latestTask.getTaskStatus())),
            document.getCreateTime(),
            document.getEditTime()
        );
    }

    /**
     * 将 chunk 实体和父块信息转为列表项 VO。
     */
    private DocumentChunkItemVo toDocumentChunkItemVo(KnowHubDocumentChunk chunk,
                                                     KnowHubDocumentParentBlock parentBlock) {
        return new DocumentChunkItemVo(
            chunk.getId(),
            chunk.getParentBlockId(),
            parentBlock == null ? null : parentBlock.getParentNo(),
            parentBlock == null ? null : parentBlock.getChildCount(),
            parentBlock == null ? null : parentBlock.getStartChunkNo(),
            parentBlock == null ? null : parentBlock.getEndChunkNo(),
            chunk.getChunkNo(),
            chunk.getSectionPath(),
            chunk.getSourceType(),
            enumMsg(DocumentChunkSourceTypeEnum.getRc(chunk.getSourceType())),
            chunk.getCharCount(),
            chunk.getTokenCount(),
            chunk.getVectorStatus(),
            enumMsg(DocumentVectorStatusEnum.getRc(chunk.getVectorStatus())),
            chunk.getChunkText()
        );
    }

    /**
     * 将父块实体转为 VO。
     */
    private DocumentParentBlockItemVo toDocumentParentBlockItemVo(KnowHubDocumentParentBlock parentBlock) {
        if (parentBlock == null) {
            return null;
        }
        return new DocumentParentBlockItemVo(
            parentBlock.getId(),
            parentBlock.getParentNo(),
            parentBlock.getSectionPath(),
            parentBlock.getSourceType(),
            enumMsg(DocumentChunkSourceTypeEnum.getRc(parentBlock.getSourceType())),
            parentBlock.getCharCount(),
            parentBlock.getTokenCount(),
            parentBlock.getChildCount(),
            parentBlock.getStartChunkNo(),
            parentBlock.getEndChunkNo(),
            parentBlock.getParentText()
        );
    }

    /**
     * 批量加载父块信息，返回 ID -> 父块 的映射。
     */
    private Map<Long, KnowHubDocumentParentBlock> listParentBlockMap(List<Long> parentBlockIds) {
        if (parentBlockIds == null || parentBlockIds.isEmpty()) {
            return Map.of();
        }
        return parentBlockMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentParentBlock>()
                .in(KnowHubDocumentParentBlock::getId, parentBlockIds)
                .eq(KnowHubDocumentParentBlock::getStatus, BusinessStatus.YES.getCode()))
            .stream()
            .collect(Collectors.toMap(
                KnowHubDocumentParentBlock::getId,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    /**
     * 将策略方案实体和步骤列表转为 VO。
     */
    private DocumentStrategyPlanVo toPlanVo(KnowHubDocumentStrategyPlan plan, List<KnowHubDocumentStrategyStep> stepList) {
        return new DocumentStrategyPlanVo(
            plan.getId(),
            plan.getPlanVersion(),
            plan.getPlanSource(),
            enumMsg(DocumentPlanSourceEnum.getRc(plan.getPlanSource())),
            plan.getPlanStatus(),
            enumMsg(DocumentPlanStatusEnum.getRc(plan.getPlanStatus())),
            plan.getStrategySnapshot(),
            plan.getRecommendReason(),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, stepList),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, stepList)
        );
    }

    /**
     * 将策略步骤列表转为 VO 列表。
     */
    private List<DocumentStrategyStepVo> toStepVoList(List<KnowHubDocumentStrategyStep> stepList) {

        return stepList.stream()
            .sorted(Comparator
                .comparingInt((KnowHubDocumentStrategyStep step) -> pipelineOrder(step.getPipelineType()))
                .thenComparing(KnowHubDocumentStrategyStep::getStepNo)
                .thenComparing(KnowHubDocumentStrategyStep::getId))
            .map(step -> new DocumentStrategyStepVo(
                step.getStepNo(),
                step.getPipelineType(),
                enumMsg(DocumentStrategyPipelineTypeEnum.getRc(step.getPipelineType())),
                step.getStrategyType(),
                enumMsg(DocumentStrategyTypeEnum.getRc(step.getStrategyType())),
                step.getStrategyRole(),
                enumMsg(DocumentStrategyRoleEnum.getRc(step.getStrategyRole())),
                step.getSourceType(),
                enumMsg(DocumentStrategySourceTypeEnum.getRc(step.getSourceType())),
                step.getExecuteStatus(),
                enumMsg(DocumentStrategyExecuteStatusEnum.getRc(step.getExecuteStatus())),
                step.getRecommendReason()
            ))
            .toList();
    }

    /**
     * 将指定流水线类型的步骤转为流水线 VO。
     * 包含流水线类型、描述、策略快照和步骤列表。
     */
    private DocumentStrategyPipelineVo toPipelineVo(DocumentStrategyPipelineTypeEnum pipelineType,
                                                    List<KnowHubDocumentStrategyStep> stepList) {
        List<KnowHubDocumentStrategyStep> pipelineSteps = stepList.stream()
            .filter(step -> pipelineType.getCode().equalsIgnoreCase(
                StrUtil.blankToDefault(step.getPipelineType(), DocumentStrategyPipelineTypeEnum.CHILD.getCode())
            ))
            .sorted(Comparator.comparingInt(KnowHubDocumentStrategyStep::getStepNo))
            .toList();
        return new DocumentStrategyPipelineVo(
            pipelineType.getCode(),
            pipelineType.getMsg(),
            pipelineSteps.stream().map(step -> String.valueOf(step.getStrategyType())).collect(Collectors.joining(",")),
            toStepVoList(pipelineSteps)
        );
    }

    /**
     * 从步骤列表中提取指定流水线类型的策略类型列表。
     */
    private List<Integer> extractPipelineTypes(List<KnowHubDocumentStrategyStep> stepList,
                                               DocumentStrategyPipelineTypeEnum pipelineType) {
        return stepList.stream()
            .filter(step -> pipelineType.getCode().equalsIgnoreCase(
                StrUtil.blankToDefault(step.getPipelineType(), DocumentStrategyPipelineTypeEnum.CHILD.getCode())
            ))
            .sorted(Comparator.comparingInt(KnowHubDocumentStrategyStep::getStepNo))
            .map(KnowHubDocumentStrategyStep::getStrategyType)
            .toList();
    }

    /**
     * 构建策略快照字符串。
     * 格式："PARENT:1,2;CHILD:3,4,1"
     */
    private String buildStrategySnapshot(List<KnowHubDocumentStrategyStep> stepList) {
        return "PARENT:" + toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, stepList).getStrategySnapshot()
            + ";CHILD:" + toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, stepList).getStrategySnapshot();
    }

    /**
     * 流水线排序权重：PARENT = 0，CHILD = 1。
     */
    private int pipelineOrder(String pipelineType) {
        return DocumentStrategyPipelineTypeEnum.PARENT.getCode().equalsIgnoreCase(
            StrUtil.blankToDefault(pipelineType, "")
        ) ? 0 : 1;
    }

    /**
     * 将任务日志实体转为 VO。
     */
    private DocumentTaskLogVo toTaskLogVo(KnowHubDocumentTaskLog logRecord) {
        return new DocumentTaskLogVo(
            logRecord.getId(),
            logRecord.getStageType(),
            enumMsg(DocumentTaskStageEnum.getRc(logRecord.getStageType())),
            logRecord.getEventType(),
            enumMsg(DocumentTaskEventTypeEnum.getRc(logRecord.getEventType())),
            logRecord.getLogLevel(),
            enumMsg(DocumentLogLevelEnum.getRc(logRecord.getLogLevel())),
            logRecord.getContent(),
            logRecord.getDetailJson(),
            logRecord.getCreateTime()
        );
    }

    /**
     * 判断操作人类型：有操作人ID为用户操作，否则为系统操作。
     */
    private Integer resolveOperatorType(Long operatorId) {

        return operatorId == null ? DocumentOperatorTypeEnum.SYSTEM.getCode() : DocumentOperatorTypeEnum.USER.getCode();
    }

    /**
     * 判断触发来源：有操作人ID为用户触发，否则为系统触发。
     */
    private Integer resolveTriggerSource(Long operatorId) {

        return operatorId == null ? DocumentTriggerSourceEnum.SYSTEM.getCode() : DocumentTriggerSourceEnum.USER.getCode();
    }

    /**
     * 安全解析可选的 Long 参数（String 类型）。
     * 空值或非法格式返回 null。
     */
    private Long parseOptionalLong(String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            return null;
        }
        try {
            Long value = Long.valueOf(rawValue.trim());
            return value > 0 ? value : null;
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 安全解析可选的 Long 参数（Long 类型）。
     * null 或非正数返回 null。
     */
    private Long parseOptionalLong(Long rawValue) {
        return rawValue == null || rawValue <= 0 ? null : rawValue;
    }

    /**
     * 解析必填的 Long 参数，不合法时抛出异常。
     */
    private Long parseRequiredLong(String rawValue, String fieldName) {
        if (StrUtil.isBlank(rawValue)) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), fieldName + "不能为空。");
        }

        try {

            Long value = Long.valueOf(rawValue.trim());
            if (value <= 0) {
                throw new NumberFormatException("id must be positive");
            }
            return value;
        }
        catch (NumberFormatException exception) {
            throw new KnowHubFrameException(BaseCode.PARAMETER_ERROR.getCode(), fieldName + "格式不正确。");
        }
    }

    /**
     * 获取枚举的中文描述信息。
     * 支持所有文档管理相关的枚举类型。
     */
    private String enumMsg(Object enumObject) {
        if (enumObject == null) {
            return "";
        }
        if (enumObject instanceof DocumentParseStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentFileTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentIndexStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentPlanSourceEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentPlanStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyRoleEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategySourceTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyExecuteStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskStageEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskEventTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentLogLevelEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentChunkSourceTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentVectorStatusEnum value) {
            return value.getMsg();
        }
        return "";
    }

    /**
     * 安全读取上传文件的字节内容。
     */
    private byte[] getFileBytes(MultipartFile file) {
        try {

            return file.getBytes();
        }
        catch (IOException exception) {
            throw new KnowHubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                "读取上传文件内容失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 构建详情 Map（键值对交替传入）。
     * 用于任务日志的 detailJson 字段。
     */
    private Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> detailMap = new LinkedHashMap<>();

        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            detailMap.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return detailMap;
    }
}
