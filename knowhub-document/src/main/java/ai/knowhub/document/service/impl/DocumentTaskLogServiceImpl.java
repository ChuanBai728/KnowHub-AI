package ai.knowhub.document.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.knowhub.document.data.KnowHubDocumentTaskLog;
import ai.knowhub.document.mapper.KnowHubDocumentTaskLogMapper;
import ai.knowhub.document.service.DocumentTaskLogService;
import ai.knowhub.enums.BusinessStatus;
import org.springframework.stereotype.Service;

/**
 * 【文档任务日志服务实现】
 *
 * 这个类负责记录文档处理过程中每个阶段的操作日志。
 *
 * 设计目的：
 *   - 追踪文档处理的每一步操作（上传、解析、策略推荐、索引构建等）
 *   - 记录操作的阶段（stageType）、事件类型（eventType）、日志级别（logLevel）
 *   - 记录操作者信息（系统自动 or 用户手动操作）
 *   - 通过 detailJson 存储结构化的操作详情（JSON 格式）
 *
 * 日志字段说明：
 *   - stageType：处理阶段（如 FILE_UPLOAD、CONTENT_PARSE、STRATEGY_ROUTE 等）
 *   - eventType：事件类型（如 START、COMPLETE、FAILED、USER_CONFIRM 等）
 *   - logLevel：日志级别（INFO、WARN、ERROR 等）
 *   - operatorType：操作者类型（SYSTEM=系统自动、USER=用户手动）
 *   - operatorId：操作者ID（用户操作时为用户ID，系统操作时为 null）
 *   - content：日志内容的文本描述
 *   - detail：详情对象，会被序列化为 JSON 存储
 *
 * 使用场景：
 *   - DocumentAsyncProcessServiceImpl 在解析和索引构建的每个阶段记录日志
 *   - DocumentManageServiceImpl 在用户确认策略时记录日志
 *   - 前端通过 queryTaskLogs 接口展示任务的处理进度和历史
 */
@AllArgsConstructor
@Service
public class DocumentTaskLogServiceImpl implements DocumentTaskLogService {

    /** 任务日志 Mapper */
    private final KnowHubDocumentTaskLogMapper taskLogMapper;

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    /** UID 生成器 */
    private final UidGenerator uidGenerator;

    /**
     * 保存一条任务日志
     *
     * @param taskId       任务ID
     * @param documentId   文档ID
     * @param stageType    处理阶段类型（DocumentTaskStageEnum）
     * @param eventType    事件类型（DocumentTaskEventTypeEnum）
     * @param logLevel     日志级别（DocumentLogLevelEnum）
     * @param operatorType 操作者类型（DocumentOperatorTypeEnum）
     * @param operatorId   操作者ID（可为 null）
     * @param content      日志内容文本
     * @param detail       详情对象（会被序列化为 JSON）
     */
    @Override
    public void saveLog(Long taskId,
                        Long documentId,
                        Integer stageType,
                        Integer eventType,
                        Integer logLevel,
                        Integer operatorType,
                        Long operatorId,
                        String content,
                        Object detail) {
        KnowHubDocumentTaskLog log = new KnowHubDocumentTaskLog();
        log.setId(uidGenerator.getUid());
        log.setTaskId(taskId);
        log.setDocumentId(documentId);
        log.setStageType(stageType);
        log.setEventType(eventType);
        log.setLogLevel(logLevel);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setContent(content);
        log.setDetailJson(toJson(detail));
        log.setStatus(BusinessStatus.YES.getCode());
        taskLogMapper.insert(log);
    }

    /**
     * 将对象序列化为 JSON 字符串。
     * 序列化失败时降级为 toString()，确保日志不会因为序列化问题而丢失。
     *
     * @param detail 待序列化的对象
     * @return JSON 字符串，detail 为 null 时返回 null
     */
    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        }
        catch (JsonProcessingException exception) {
            return String.valueOf(detail);
        }
    }
}
