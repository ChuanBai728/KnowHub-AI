package ai.knowhub.document.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentChunk;
import ai.knowhub.document.data.KnowHubDocumentParentBlock;
import ai.knowhub.document.data.KnowHubDocumentStrategyPlan;
import ai.knowhub.document.data.KnowHubDocumentStrategyStep;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.data.KnowHubDocumentTask;
import ai.knowhub.document.mapper.KnowHubDocumentChunkMapper;
import ai.knowhub.document.mapper.KnowHubDocumentMapper;
import ai.knowhub.document.mapper.KnowHubDocumentParentBlockMapper;
import ai.knowhub.document.mapper.KnowHubDocumentStrategyPlanMapper;
import ai.knowhub.document.mapper.KnowHubDocumentStrategyStepMapper;
import ai.knowhub.document.mapper.KnowHubDocumentTaskMapper;
import ai.knowhub.document.service.DocumentAsyncProcessService;
import ai.knowhub.document.service.DocumentNavigationIndexService;
import ai.knowhub.document.service.DocumentParserService;
import ai.knowhub.document.service.DocumentProfileService;
import ai.knowhub.document.service.DocumentStorageService;
import ai.knowhub.document.service.DocumentStrategyService;
import ai.knowhub.document.service.DocumentStructureGraphProjectionService;
import ai.knowhub.document.service.DocumentStructureNodeService;
import ai.knowhub.document.service.DocumentTaskLogService;
import ai.knowhub.document.service.DocumentVectorGateway;
import ai.knowhub.document.service.keyword.DocumentKeywordSearchGateway;
import ai.knowhub.document.support.ChunkCandidate;
import ai.knowhub.document.support.DocumentAnalysisResult;
import ai.knowhub.document.support.DocumentStrategyPlanDraft;
import ai.knowhub.document.support.DocumentStrategyStepDraft;
import ai.knowhub.document.support.ParentBlockCandidate;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.enums.DocumentChunkSourceTypeEnum;
import ai.knowhub.enums.DocumentFileTypeEnum;
import ai.knowhub.enums.DocumentIndexStatusEnum;
import ai.knowhub.enums.DocumentLogLevelEnum;
import ai.knowhub.enums.DocumentOperatorTypeEnum;
import ai.knowhub.enums.DocumentParseStatusEnum;
import ai.knowhub.enums.DocumentPlanSourceEnum;
import ai.knowhub.enums.DocumentPlanStatusEnum;
import ai.knowhub.enums.DocumentStrategyExecuteStatusEnum;
import ai.knowhub.enums.DocumentStrategyPipelineTypeEnum;
import ai.knowhub.enums.DocumentStrategyStatusEnum;
import ai.knowhub.enums.DocumentTaskEventTypeEnum;
import ai.knowhub.enums.DocumentTaskStageEnum;
import ai.knowhub.enums.DocumentTaskStatusEnum;
import ai.knowhub.enums.DocumentVectorStatusEnum;
import ai.knowhub.enums.DocumentVectorStoreTypeEnum;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【文档异步处理服务 - 核心异步编排引擎】
 *
 * 设计模式：模板方法模式 + 异步消息驱动
 *
 * 这个类是文档处理流水线的"异步执行引擎"，由 Kafka 消费者触发调用。
 * 它不直接暴露给 HTTP 接口，而是通过 Kafka 消息队列异步执行两个核心任务：
 *
 * 任务一：handleParseRoute（解析与策略推荐）
 *   文件上传 --> Kafka 消息 --> 本方法
 *   流程：下载文件 --> Tika 解析文本 --> 提取结构节点 --> 生成文档画像
 *        --> 推荐切块策略 --> 创建策略方案和步骤 --> 更新文档状态
 *
 * 任务二：handleIndexBuild（索引构建）
 *   用户确认策略 --> Kafka 消息 --> 本方法
 *   流程：执行切块流水线（父子块） --> 生成 parent block 和 chunk 实体
 *        --> 向量化（embedding） --> 关键词索引 --> 更新文档状态
 *
 * 为什么用异步：
 *   - 文档解析和向量化是 CPU/IO 密集型操作，可能耗时数秒到数十秒
 *   - HTTP 接口需要快速响应，不能让用户等待
 *   - Kafka 提供了可靠的异步投递和重试机制
 *
 * 父子块（Parent-Child）设计：
 *   - 父块（ParentBlock）：较大的文本单元，保留完整上下文，用于生成回答时的引用
 *   - 子块（Chunk）：较小的文本单元，用于向量检索的精确召回
 *   - 一个父块包含多个子块，检索命中子块后可以"提升"到父块获取更多上下文
 *
 * 依赖的关键组件：
 *   - DocumentParserService：Tika 文档解析，将 PDF/Word/Markdown 转为纯文本
 *   - DocumentStrategyService：策略推荐和切块执行（结构切块、递归切块、语义切块、LLM 切块）
 *   - DocumentVectorGateway：向量化并写入 pgvector
 *   - DocumentStructureNodeService：结构节点管理（标题、章节、列表项等）
 *   - DocumentNavigationIndexService：导航索引同步到 Elasticsearch
 *   - DocumentStructureGraphProjectionService：结构图投影到 Neo4j
 */
@Slf4j
@AllArgsConstructor
@Service
public class DocumentAsyncProcessServiceImpl implements DocumentAsyncProcessService {

    /** 文档主表 Mapper */
    private final KnowHubDocumentMapper documentMapper;

    /** 策略方案 Mapper */
    private final KnowHubDocumentStrategyPlanMapper planMapper;

    /** 策略步骤 Mapper */
    private final KnowHubDocumentStrategyStepMapper stepMapper;

    /** 任务 Mapper */
    private final KnowHubDocumentTaskMapper taskMapper;

    /** 父块 Mapper */
    private final KnowHubDocumentParentBlockMapper parentBlockMapper;

    /** 子块（chunk）Mapper */
    private final KnowHubDocumentChunkMapper chunkMapper;

    /** 对象存储服务（MinIO），用于下载原始文件和上传解析文本 */
    private final DocumentStorageService storageService;

    /** 文档解析服务（Tika），负责将文件转为文本和结构节点 */
    private final DocumentParserService parserService;

    /** 文档策略服务，负责策略推荐和切块流水线执行 */
    private final DocumentStrategyService strategyService;

    /** 结构节点服务，管理文档的标题、章节、列表项等结构信息 */
    private final DocumentStructureNodeService structureNodeService;

    /** 任务日志服务，记录处理过程中的每一步操作 */
    private final DocumentTaskLogService taskLogService;

    /** 向量化网关，负责将 chunk 文本转为 embedding 并写入 pgvector */
    private final DocumentVectorGateway vectorGateway;

    /** 关键词搜索网关（可选），用于 Elasticsearch 关键词索引 */
    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;

    /** 导航索引服务（可选），用于 Elasticsearch 结构导航索引 */
    private final ObjectProvider<DocumentNavigationIndexService> navigationIndexServiceProvider;

    /** Neo4j 结构图投影服务（可选） */
    private final ObjectProvider<DocumentStructureGraphProjectionService> graphProjectionServiceProvider;

    /** 文档画像服务，生成文档摘要、类型、核心主题等画像信息 */
    private final DocumentProfileService documentProfileService;

    /** UID 生成器（百度 UidGenerator），用于生成全局唯一 ID */
    @Resource
    private UidGenerator uidGenerator;

    /**
     * 【任务一】处理文档解析和策略推荐路由
     *
     * 由 Kafka 消费者触发，消息内容包含 documentId 和 taskId。
     * 本方法是整个文档处理流水线的起点。
     *
     * 执行流程：
     *   1. 从数据库重新读取文档和任务（Kafka 消息触发时需要确认数据仍存在）
     *   2. 更新任务状态为"运行中"，更新文档解析状态为"解析中"
     *   3. 从 MinIO 下载原始文件
     *   4. 调用 Tika 解析器将文件转为纯文本 + 结构节点
     *   5. 将解析文本上传到 MinIO 存储
     *   6. 将结构节点写入数据库（替换旧节点）
     *   7. 同步导航产物：ES 导航索引
     *   8. 生成文档画像（摘要、类型、核心主题等）
     *   9. 调用策略推荐服务，生成切块策略方案
     *   10. 将策略方案和步骤写入数据库
     *   11. 更新文档状态为"解析成功 + 策略已推荐"
     *
     * @param documentId 文档ID
     * @param taskId     任务ID
     */
    @Override

    public void handleParseRoute(Long documentId, Long taskId) {

        // 解析任务从 Kafka 触发，所以第一步必须重新读取数据库，确认文档和任务仍然存在。
        KnowHubDocument document = documentMapper.selectById(documentId);
        KnowHubDocumentTask task = taskMapper.selectById(taskId);
        if (document == null || task == null) {
            log.warn("解析任务对应的文档或任务不存在，documentId={}, taskId={}", documentId, taskId);
            return;
        }

        Date startTime = new Date();
        try {

            // 更新任务状态为"运行中"，当前阶段为"内容解析"
            task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
            task.setCurrentStage(DocumentTaskStageEnum.CONTENT_PARSE.getCode());
            task.setStartTime(startTime);
            taskMapper.updateById(task);

            // 更新文档解析状态为"解析中"
            document.setParseStatus(DocumentParseStatusEnum.PARSING.getCode());
            documentMapper.updateById(document);

            // 记录日志：开始解析
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始解析文档内容。",
                Map.of("objectName", document.getObjectName()));

            // 从 MinIO 下载原始文件的字节内容
            byte[] fileBytes = storageService.downloadObject(document.getObjectName());
            // Parser 会把 PDF、Word、Markdown 等文件统一转换成文本和结构节点，后续策略推荐依赖这些结果。
            DocumentAnalysisResult analysisResult = parserService.parse(fileBytes, document.getOriginalFileName(),
                document.getMimeType(), DocumentFileTypeEnum.getRc(document.getFileType()));

            // 将解析后的纯文本上传到 MinIO，返回存储路径
            String parseTextPath = storageService.uploadParsedText(documentId, analysisResult.getParsedText());

            // 结构节点是图结构、章节导航和精确定位的基础，例如标题、条款、列表项等。
            List<KnowHubDocumentStructureNode> structureNodes = structureNodeService.replaceDocumentNodes(
                documentId,
                taskId,
                analysisResult.getStructureNodes()
            );
            int structureNodeCount = structureNodes.size();
            // 同步导航产物：ES 导航索引
            syncNavigationArtifacts(documentId, taskId, structureNodes);
            // 生成文档画像（摘要、类型、核心主题、是否适合图查询等）
            documentProfileService.generateProfile(documentId, analysisResult, structureNodes);

            // 记录日志：解析完成
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "文档解析完成。",
                Map.of(
                    "charCount", analysisResult.getCharCount(),
                    "tokenCount", analysisResult.getTokenCount(),
                    "structureLevel", analysisResult.getStructureLevel(),
                    "contentQualityLevel", analysisResult.getContentQualityLevel(),
                    "structureNodeCount", structureNodeCount
                ));

            // 进入"策略推荐"阶段
            task.setCurrentStage(DocumentTaskStageEnum.STRATEGY_ROUTE.getCode());
            taskMapper.updateById(task);

            // 策略推荐会根据文档结构和内容质量，决定父块、子块等切分方式。
            DocumentStrategyPlanDraft planDraft = strategyService.recommendStrategy(document, analysisResult);
            Long planId = uidGenerator.getUid();
            int planVersion = getNextPlanVersion(documentId);

            // 创建策略方案记录
            KnowHubDocumentStrategyPlan plan = new KnowHubDocumentStrategyPlan();
            plan.setId(planId);
            plan.setDocumentId(documentId);
            plan.setPlanVersion(planVersion);
            plan.setPlanSource(DocumentPlanSourceEnum.SYSTEM_RECOMMEND.getCode());
            plan.setPlanStatus(DocumentPlanStatusEnum.WAIT_CONFIRM.getCode());
            plan.setStrategyCount(planDraft.getParentSteps().size() + planDraft.getChildSteps().size());
            plan.setStrategySnapshot(planDraft.getStrategySnapshot());
            plan.setRecommendReason(planDraft.getRecommendReason());
            plan.setStatus(BusinessStatus.YES.getCode());
            planMapper.insert(plan);

            // 创建父块策略步骤
            for (int index = 0; index < planDraft.getParentSteps().size(); index++) {
                DocumentStrategyStepDraft draft = planDraft.getParentSteps().get(index);
                KnowHubDocumentStrategyStep step = new KnowHubDocumentStrategyStep();
                step.setId(uidGenerator.getUid());
                step.setPlanId(planId);
                step.setDocumentId(documentId);
                step.setPipelineType(draft.getPipelineType());
                step.setStepNo(index + 1);
                step.setStrategyType(draft.getStrategyType());
                step.setStrategyRole(draft.getStrategyRole());
                step.setSourceType(draft.getSourceType());
                step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
                step.setRecommendReason(draft.getRecommendReason());
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }
            // 创建子块策略步骤
            for (int index = 0; index < planDraft.getChildSteps().size(); index++) {
                DocumentStrategyStepDraft draft = planDraft.getChildSteps().get(index);
                KnowHubDocumentStrategyStep step = new KnowHubDocumentStrategyStep();
                step.setId(uidGenerator.getUid());
                step.setPlanId(planId);
                step.setDocumentId(documentId);
                step.setPipelineType(draft.getPipelineType());
                step.setStepNo(index + 1);
                step.setStrategyType(draft.getStrategyType());
                step.setStrategyRole(draft.getStrategyRole());
                step.setSourceType(draft.getSourceType());
                step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
                step.setRecommendReason(draft.getRecommendReason());
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }

            // 更新文档的解析状态、策略状态和各种统计信息
            document.setParseStatus(DocumentParseStatusEnum.PARSE_SUCCESS.getCode());
            document.setStrategyStatus(DocumentStrategyStatusEnum.RECOMMENDED.getCode());
            document.setCharCount(analysisResult.getCharCount());
            document.setTokenCount(analysisResult.getTokenCount());
            document.setStructureLevel(analysisResult.getStructureLevel());
            document.setContentQualityLevel(analysisResult.getContentQualityLevel());
            document.setParseTextPath(parseTextPath);
            document.setParseErrorMsg(null);
            document.setCurrentPlanId(planId);
            document.setLastParseTaskId(taskId);
            document.setStructureNodeCount(structureNodeCount);
            documentMapper.updateById(document);

            // 标记任务成功完成
            finishTaskSuccess(task, DocumentTaskStageEnum.STRATEGY_ROUTE.getCode(), startTime);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.STRATEGY_ROUTE.getCode(),
                DocumentTaskEventTypeEnum.RECOMMEND_STRATEGY.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "系统已生成推荐策略。",
                detail("planId", planId,
                    "strategySnapshot", planDraft.getStrategySnapshot(),
                    "parentStepCount", planDraft.getParentSteps().size(),
                    "childStepCount", planDraft.getChildSteps().size(),
                    "structureNodeCount", structureNodeCount,
                    "recommendReason", planDraft.getRecommendReason()));
        }
        catch (Exception exception) {
            // 异常处理：标记解析失败，记录错误日志
            log.error("异步解析文档失败，documentId={}, taskId={}", documentId, taskId, exception);

            document.setParseStatus(DocumentParseStatusEnum.PARSE_FAILED.getCode());
            document.setParseErrorMsg(exception.getMessage());
            documentMapper.updateById(document);

            failTask(task, startTime, exception, DocumentTaskStageEnum.CONTENT_PARSE.getCode());
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.FAILED.getCode(),
                DocumentLogLevelEnum.ERROR.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "文档解析失败。",
                detail("error", exception.getMessage()));
        }
    }

    /**
     * 【任务二】处理索引构建
     *
     * 由 Kafka 消费者触发，消息内容包含 documentId、taskId 和 planId。
     * 前置条件：文档已完成解析，策略已被用户确认。
     *
     * 执行流程：
     *   1. 校验文档、任务、策略方案三份数据都存在
     *   2. 下载之前解析好的纯文本
     *   3. 执行切块流水线（父块 + 子块）
     *   4. 生成 parent block 和 chunk 实体，写入数据库
     *   5. 调用向量化网关，将 chunk 文本转为 embedding 写入 pgvector
     *   6. 同步关键词索引（Elasticsearch）
     *   7. 更新文档索引状态为"构建成功"
     *
     * @param documentId 文档ID
     * @param taskId     任务ID
     * @param planId     策略方案ID
     */
    @Override
    public void handleIndexBuild(Long documentId, Long taskId, Long planId) {

        // 索引构建任务需要文档、任务、策略方案三份数据，缺任何一个都不能继续。
        KnowHubDocument document = documentMapper.selectById(documentId);
        KnowHubDocumentTask task = taskMapper.selectById(taskId);
        KnowHubDocumentStrategyPlan plan = planMapper.selectById(planId);
        if (document == null || task == null || plan == null) {
            log.warn("索引任务对应的数据不存在，documentId={}, taskId={}, planId={}", documentId, taskId, planId);
            return;
        }

        Date startTime = new Date();

        // 获取策略方案下的所有步骤（按流水线类型和步骤号排序）
        List<KnowHubDocumentStrategyStep> stepList = listSteps(planId);
        try {

            // 更新任务状态为"运行中"，当前阶段为"切块执行"
            task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
            task.setCurrentStage(DocumentTaskStageEnum.CHUNK_EXECUTE.getCode());
            task.setStartTime(startTime);
            taskMapper.updateById(task);

            // 更新文档索引状态为"构建中"
            document.setIndexStatus(DocumentIndexStatusEnum.BUILDING.getCode());
            documentMapper.updateById(document);

            // 更新所有策略步骤的执行状态为"执行中"
            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTING.getCode());

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始执行切块流水线。",
                Map.of("strategySnapshot", plan.getStrategySnapshot()));

            // 下载之前解析阶段上传的纯文本
            String parsedText = storageService.downloadText(document.getParseTextPath());

            // 父块提供较完整的上下文，子块用于精确召回；这是本项目 RAG 质量的关键设计。
            List<ParentBlockCandidate> parentBlockCandidateList = strategyService.buildParentBlocks(document, plan, stepList, parsedText);

            // 标记策略步骤执行成功
            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTE_SUCCESS.getCode());

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "切块执行完成。",
                Map.of(
                    "parentCount", parentBlockCandidateList.size(),
                    "childCount", countChildCandidates(parentBlockCandidateList)
                ));

            // 进入"切块后处理"阶段
            task.setCurrentStage(DocumentTaskStageEnum.CHUNK_POST_PROCESS.getCode());
            taskMapper.updateById(task);

            // 过滤掉空白的父块和子块
            List<ParentBlockCandidate> finalParentBlockList = parentBlockCandidateList.stream()
                .filter(item -> item != null
                    && StrUtil.isNotBlank(item.getText())
                    && item.getChildChunks() != null
                    && item.getChildChunks().stream().anyMatch(child -> StrUtil.isNotBlank(child.getText())))
                .toList();

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_POST_PROCESS.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "切块后处理完成。",
                Map.of(
                    "parentCount", finalParentBlockList.size(),
                    "childCount", countChildCandidates(finalParentBlockList)
                ));

            // 构建父子块的数据库实体
            ParentChildEntityBundle entityBundle = buildParentChildEntities(documentId, taskId, planId, finalParentBlockList);
            List<KnowHubDocumentParentBlock> parentBlockEntityList = entityBundle.parentBlocks();
            List<KnowHubDocumentChunk> chunkEntityList = entityBundle.childChunks();

            // 批量写入父块和子块到数据库
            for (KnowHubDocumentParentBlock parentBlock : parentBlockEntityList) {
                parentBlockMapper.insert(parentBlock);
            }
            for (KnowHubDocumentChunk chunk : chunkEntityList) {
                chunkMapper.insert(chunk);
            }

            // 进入"向量化"阶段
            task.setCurrentStage(DocumentTaskStageEnum.VECTORIZE.getCode());
            taskMapper.updateById(task);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.VECTORIZE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始执行向量化。",
                detail("chunkCount", chunkEntityList.size(),
                    "embeddingBatchSize", DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "embeddingBatchCount",
                    (chunkEntityList.size() + DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT - 1)
                        / DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "vectorStoreType", DocumentVectorStoreTypeEnum.PG_VECTOR.getMsg(),
                    "parentCount", parentBlockEntityList.size()));

            // 向量化会把每个子块转成 embedding，之后用户问题才能通过语义相似度找回它们。
            vectorGateway.vectorize(chunkEntityList);

            // 如果关键词搜索网关可用，同步建立关键词索引
            DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
            if (keywordSearchGateway != null) {
                // 关键词索引用来补足向量检索对专有名词、编号和配置项不敏感的问题。
                keywordSearchGateway.indexChunks(chunkEntityList);
            }

            // 更新 chunk 的向量化状态到数据库
            for (KnowHubDocumentChunk chunk : chunkEntityList) {
                chunkMapper.updateById(chunk);
            }

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.VECTORIZE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "向量化完成。",
                detail("chunkCount", chunkEntityList.size(),
                    "embeddingBatchSize", DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "embeddingBatchCount",
                    (chunkEntityList.size() + DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT - 1)
                        / DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "vectorStoreType", DocumentVectorStoreTypeEnum.PG_VECTOR.getMsg(),
                    "parentCount", parentBlockEntityList.size()));

            // 进入"存储完成"阶段
            task.setCurrentStage(DocumentTaskStageEnum.STORE_COMPLETE.getCode());
            taskMapper.updateById(task);

            // 更新策略方案状态为"已执行"
            plan.setPlanStatus(DocumentPlanStatusEnum.EXECUTED.getCode());
            planMapper.updateById(plan);

            // 更新文档索引状态为"构建成功"
            document.setIndexStatus(DocumentIndexStatusEnum.BUILD_SUCCESS.getCode());
            document.setLastIndexTaskId(taskId);
            documentMapper.updateById(document);

            // 标记任务成功完成
            finishTaskSuccess(task, DocumentTaskStageEnum.STORE_COMPLETE.getCode(), startTime);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.STORE_COMPLETE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "索引构建完成。",
                Map.of("taskId", taskId, "chunkCount", chunkEntityList.size(), "parentCount", parentBlockEntityList.size()));
        }
        catch (Exception exception) {
            // 异常处理：标记索引构建失败，回滚 chunk 向量化状态
            log.error("异步构建索引失败，documentId={}, taskId={}, planId={}", documentId, taskId, planId, exception);

            document.setIndexStatus(DocumentIndexStatusEnum.BUILD_FAILED.getCode());
            documentMapper.updateById(document);

            // 将该任务的所有 chunk 标记为向量化失败
            chunkMapper.update(null, new LambdaUpdateWrapper<KnowHubDocumentChunk>()
                .eq(KnowHubDocumentChunk::getTaskId, taskId)
                .eq(KnowHubDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .set(KnowHubDocumentChunk::getVectorStatus, DocumentVectorStatusEnum.VECTOR_FAILED.getCode())
                .set(KnowHubDocumentChunk::getVectorStoreType, DocumentVectorStoreTypeEnum.PG_VECTOR.getCode()));

            // 标记策略步骤执行失败
            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTE_FAILED.getCode());
            failTask(task, startTime, exception, task.getCurrentStage());
            taskLogService.saveLog(taskId, documentId,
                task.getCurrentStage(),
                DocumentTaskEventTypeEnum.FAILED.getCode(),
                DocumentLogLevelEnum.ERROR.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "索引构建失败。",
                detail("error", exception.getMessage()));
        }
    }

    /**
     * 将切块候选列表转换为数据库实体（parent block + chunk）。
     *
     * 构建逻辑：
     *   - 遍历每个 ParentBlockCandidate，创建 KnowHubDocumentParentBlock 实体
     *   - 遍历每个父块下的子块候选，创建 KnowHubDocumentChunk 实体
     *   - 子块的 chunkNo 是全局递增的（跨父块连续编号）
     *   - 每个子块记录其所属父块的 ID
     *
     * @param documentId            文档ID
     * @param taskId                任务ID
     * @param planId                策略方案ID
     * @param parentBlockCandidateList 父块候选列表
     * @return 包含父块实体列表和子块实体列表的 bundle
     */
    private ParentChildEntityBundle buildParentChildEntities(Long documentId,
                                                             Long taskId,
                                                             Long planId,
                                                             List<ParentBlockCandidate> parentBlockCandidateList) {
        List<KnowHubDocumentParentBlock> parentBlockEntityList = new ArrayList<>();
        List<KnowHubDocumentChunk> chunkEntityList = new ArrayList<>();
        int globalChunkNo = 1;

        for (int parentIndex = 0; parentIndex < parentBlockCandidateList.size(); parentIndex++) {
            ParentBlockCandidate parentCandidate = parentBlockCandidateList.get(parentIndex);
            if (parentCandidate == null || StrUtil.isBlank(parentCandidate.getText())) {
                continue;
            }

            // 创建父块实体
            KnowHubDocumentParentBlock parentBlock = new KnowHubDocumentParentBlock();
            parentBlock.setId(uidGenerator.getUid());
            parentBlock.setDocumentId(documentId);
            parentBlock.setTaskId(taskId);
            parentBlock.setPlanId(planId);
            parentBlock.setParentNo(parentIndex + 1);
            parentBlock.setSourceType(parentCandidate.getSourceType() == null
                ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : parentCandidate.getSourceType());
            parentBlock.setSectionPath(parentCandidate.getSectionPath());
            parentBlock.setStructureNodeId(parentCandidate.getStructureNodeId());
            parentBlock.setStructureNodeType(parentCandidate.getStructureNodeType());
            parentBlock.setCanonicalPath(parentCandidate.getCanonicalPath());
            parentBlock.setItemIndex(parentCandidate.getItemIndex());
            parentBlock.setParentText(parentCandidate.getText().trim());
            parentBlock.setCharCount(parentCandidate.getText().length());
            parentBlock.setTokenCount(estimateTokenCount(parentCandidate.getText()));
            parentBlock.setStatus(BusinessStatus.YES.getCode());

            // 记录该父块下子块的起始编号
            int startChunkNo = globalChunkNo;
            int childCount = 0;
            for (ChunkCandidate childCandidate : parentCandidate.getChildChunks()) {
                if (childCandidate == null || StrUtil.isBlank(childCandidate.getText())) {
                    continue;
                }
                // 创建子块（chunk）实体
                KnowHubDocumentChunk chunk = new KnowHubDocumentChunk();
                chunk.setId(uidGenerator.getUid());
                chunk.setDocumentId(documentId);
                chunk.setTaskId(taskId);
                chunk.setPlanId(planId);
                chunk.setParentBlockId(parentBlock.getId());
                chunk.setChunkNo(globalChunkNo++);
                chunk.setSourceType(childCandidate.getSourceType() == null
                    ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : childCandidate.getSourceType());
                chunk.setSectionPath(StrUtil.blankToDefault(childCandidate.getSectionPath(), parentCandidate.getSectionPath()));
                chunk.setStructureNodeId(childCandidate.getStructureNodeId());
                chunk.setStructureNodeType(childCandidate.getStructureNodeType());
                chunk.setCanonicalPath(childCandidate.getCanonicalPath());
                chunk.setItemIndex(childCandidate.getItemIndex());
                chunk.setChunkText(childCandidate.getText().trim());
                chunk.setCharCount(childCandidate.getText().length());

                chunk.setTokenCount(estimateTokenCount(childCandidate.getText()));
                chunk.setVectorStatus(DocumentVectorStatusEnum.WAIT_VECTOR.getCode());
                chunk.setVectorStoreType(DocumentVectorStoreTypeEnum.PG_VECTOR.getCode());
                chunk.setStatus(BusinessStatus.YES.getCode());
                chunkEntityList.add(chunk);
                childCount++;
            }

            // 更新父块的子块数量和编号范围
            parentBlock.setChildCount(childCount);
            parentBlock.setStartChunkNo(childCount == 0 ? null : startChunkNo);
            parentBlock.setEndChunkNo(childCount == 0 ? null : globalChunkNo - 1);
            parentBlockEntityList.add(parentBlock);
        }

        return new ParentChildEntityBundle(parentBlockEntityList, chunkEntityList);
    }

    /**
     * 统计所有父块候选中有效子块的总数
     */
    private int countChildCandidates(List<ParentBlockCandidate> parentBlockCandidateList) {
        if (parentBlockCandidateList == null || parentBlockCandidateList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ParentBlockCandidate candidate : parentBlockCandidateList) {
            if (candidate == null || candidate.getChildChunks() == null) {
                continue;
            }
            count += (int) candidate.getChildChunks().stream()
                .filter(child -> child != null && StrUtil.isNotBlank(child.getText()))
                .count();
        }
        return count;
    }

    /**
     * 批量更新策略步骤的执行状态
     * @param planId        策略方案ID
     * @param executeStatus 目标执行状态
     */
    private void updateStepExecuteStatus(Long planId, Integer executeStatus) {

        stepMapper.update(null, new LambdaUpdateWrapper<KnowHubDocumentStrategyStep>()
            .eq(KnowHubDocumentStrategyStep::getPlanId, planId)
            .eq(KnowHubDocumentStrategyStep::getStatus, BusinessStatus.YES.getCode())
            .set(KnowHubDocumentStrategyStep::getExecuteStatus, executeStatus));
    }

    /**
     * 获取策略方案下的所有步骤，按流水线类型和步骤号排序
     * @param planId 策略方案ID
     * @return 排序后的步骤列表
     */
    private List<KnowHubDocumentStrategyStep> listSteps(Long planId) {
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
     * 流水线类型排序优先级：父块（PARENT）排在子块（CHILD）前面
     */
    private int pipelineOrder(String pipelineType) {
        return DocumentStrategyPipelineTypeEnum.PARENT.getCode().equalsIgnoreCase(
            StrUtil.blankToDefault(pipelineType, "")
        ) ? 0 : 1;
    }

    /**
     * 获取文档的下一个策略方案版本号
     * @param documentId 文档ID
     * @return 新版本号（现有最大版本号 + 1）
     */
    private int getNextPlanVersion(Long documentId) {

        List<KnowHubDocumentStrategyPlan> planList = planMapper.selectList(new LambdaQueryWrapper<KnowHubDocumentStrategyPlan>()
            .eq(KnowHubDocumentStrategyPlan::getDocumentId, documentId)
            .eq(KnowHubDocumentStrategyPlan::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(KnowHubDocumentStrategyPlan::getPlanVersion)
            .last("limit 1"));
        return planList.isEmpty() ? 1 : planList.get(0).getPlanVersion() + 1;
    }

    /**
     * 标记任务成功完成：设置完成时间、耗时、清空错误信息
     */
    private void finishTaskSuccess(KnowHubDocumentTask task, Integer stage, Date startTime) {

        Date finishTime = new Date();
        task.setTaskStatus(DocumentTaskStatusEnum.SUCCESS.getCode());
        task.setCurrentStage(stage);
        task.setFinishTime(finishTime);
        task.setCostMillis(finishTime.getTime() - startTime.getTime());
        task.setErrorCode(null);
        task.setErrorMsg(null);
        taskMapper.updateById(task);
    }

    /**
     * 同步导航产物：ES 导航索引
     *
     * 这两个操作都是可选的（通过 ObjectProvider 判断 Bean 是否存在），
     * 如果服务未启用则跳过，不影响主流程。
     */
    private void syncNavigationArtifacts(Long documentId,
                                         Long parseTaskId,
                                         List<KnowHubDocumentStructureNode> structureNodes) {
        log.info("开始同步导航产物: documentId={}, parseTaskId={}, structureNodeCount={}",
            documentId,
            parseTaskId,
            structureNodes == null ? 0 : structureNodes.size());
        // 同步 Elasticsearch 导航索引
        DocumentNavigationIndexService navigationIndexService = navigationIndexServiceProvider.getIfAvailable();
        if (navigationIndexService != null) {
            log.info("同步导航 ES 索引: documentId={}, parseTaskId={}", documentId, parseTaskId);
            navigationIndexService.reindexDocumentNodes(documentId, parseTaskId, structureNodes);
        }
        else {
            log.info("跳过导航 ES 索引同步，因为服务未启用: documentId={}, parseTaskId={}", documentId, parseTaskId);
        }

        DocumentStructureGraphProjectionService graphProjectionService = graphProjectionServiceProvider.getIfAvailable();
        if (graphProjectionService != null && graphProjectionService.enabled()) {
            log.info("同步 Neo4j 文档结构图投影: documentId={}, parseTaskId={}", documentId, parseTaskId);
            graphProjectionService.projectToGraph(documentId, parseTaskId);
        }
        else {
            log.info("跳过 Neo4j 文档结构图投影，因为服务未启用: documentId={}, parseTaskId={}", documentId, parseTaskId);
        }
    }

    /**
     * 标记任务失败：设置完成时间、耗时、错误码和错误信息
     */
    private void failTask(KnowHubDocumentTask task, Date startTime, Exception exception, Integer currentStage) {

        Date finishTime = new Date();
        task.setTaskStatus(DocumentTaskStatusEnum.FAILED.getCode());
        task.setCurrentStage(currentStage);
        task.setFinishTime(finishTime);
        task.setCostMillis(finishTime.getTime() - startTime.getTime());
        task.setErrorCode("TASK_FAILED");
        task.setErrorMsg(exception.getMessage());
        taskMapper.updateById(task);
    }

    /**
     * 估算文本的 token 数量（粗略算法）。
     * 简化规则：中文字符 1 字 = 1 token，英文单词 1 词 = 1 token，其他字符每 4 个算 1 token。
     *
     * @param text 输入文本
     * @return 估算的 token 数
     */
    private int estimateTokenCount(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        int chineseCount = 0;
        int englishCount = 0;

        // 统计中文字符数
        for (char current : text.toCharArray()) {
            if (String.valueOf(current).matches("[\\u4e00-\\u9fa5]")) {
                chineseCount++;
            }
        }

        // 统计英文单词数
        for (String word : text.split("\\s+")) {
            if (word.matches(".*[A-Za-z].*")) {
                englishCount++;
            }
        }

        return chineseCount + englishCount + Math.max(1, (text.length() - chineseCount) / 4);
    }

    /**
     * 构建键值对形式的详情 Map，用于任务日志的 detailJson 字段。
     * 参数以 key-value 对的形式传入：detail("key1", value1, "key2", value2)
     */
    private Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> detailMap = new LinkedHashMap<>();

        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            detailMap.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return detailMap;
    }

    /**
     * 【内部记录类】父子块实体打包结果
     * 使用 Java Record（Java 16+ 特性），自动提供构造器、getter 和 equals/hashCode。
     */
    private record ParentChildEntityBundle(
        List<KnowHubDocumentParentBlock> parentBlocks,
        List<KnowHubDocumentChunk> childChunks
    ) {
    }
}
