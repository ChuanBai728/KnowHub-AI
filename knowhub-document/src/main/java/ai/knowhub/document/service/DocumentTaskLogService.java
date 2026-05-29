package ai.knowhub.document.service;

/**
 * 【文档任务日志服务接口】
 *
 * 作用：记录文档处理过程中各阶段的任务日志，用于追踪任务执行状态、排查问题和审计。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"日志记录"层
 *   - 被解析、索引构建等各环节调用，记录关键事件
 *   - 日志数据可供前端展示任务进度，也可用于运维监控
 *
 * 核心概念：
 *   - 任务日志：记录文档处理任务在某个时间点的执行事件
 *   - 阶段类型（Stage Type）：标识日志所属的处理阶段（解析、索引构建等）
 *   - 事件类型（Event Type）：标识事件的性质（开始、完成、错误等）
 *   - 日志级别（Log Level）：标识日志的严重程度（INFO、WARN、ERROR 等）
 *   - 操作者类型（Operator Type）：标识触发操作的主体（系统、用户等）
 */
public interface DocumentTaskLogService {

    /**
     * 保存任务日志
     *
     * 功能说明：
     *   - 记录文档处理任务的执行日志
     *   - 支持丰富的上下文信息，便于问题追踪
     *   - 日志与任务和文档关联，支持按任务或文档查询
     *
     * @param taskId       任务ID，关联到具体的处理任务
     * @param documentId   文档ID，关联到具体的文档
     * @param stageType    阶段类型，标识日志所属的处理阶段
     *                     （如：1=解析阶段，2=索引构建阶段）
     * @param eventType    事件类型，标识事件的性质
     *                     （如：1=开始，2=进行中，3=完成，4=失败）
     * @param logLevel     日志级别
     *                     （如：1=INFO，2=WARN，3=ERROR）
     * @param operatorType 操作者类型，标识谁触发了此操作
     *                     （如：1=系统自动，2=用户手动）
     * @param operatorId   操作者ID，具体的操作者标识（用户ID等）
     * @param content      日志内容，人类可读的事件描述
     * @param detail       日志详情，可以是任意对象（通常为 Map 或 JSON），
     *                     包含更详细的上下文信息
     */
    void saveLog(Long taskId,
                 Long documentId,
                 Integer stageType,
                 Integer eventType,
                 Integer logLevel,
                 Integer operatorType,
                 Long operatorId,
                 String content,
                 Object detail);
}
