package ai.knowhub.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

/**
 * 文档任务日志实体类。
 *
 * 对应数据库表 knowhub_document_task_log，记录文档任务执行过程中的详细日志。
 *
 * 任务日志的作用：
 * 
 *   <b>问题排查</b>：当任务执行失败时，通过日志可以定位失败的具体原因和阶段。
 *   <b>进度监控</b>：用户可以查看任务的实时执行进度。
 *   <b>审计追踪</b>：记录每个阶段的操作详情，便于事后审计。
 *   <b>性能分析</b>：通过日志中的时间信息，分析各阶段的耗时。
 * 日志分类：
 * 
 *   按阶段（stageType）：解析阶段、切块阶段、索引阶段等。
 *   按事件（eventType）：开始、完成、失败、跳过等。
 *   按级别（logLevel）：INFO、WARN、ERROR 等。
 *   按操作者（operatorType）：系统自动执行、用户手动操作等。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document_task_log")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocumentTaskLog extends BaseTableData {

    /**
     * 日志主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 关联的任务 ID。
     * 关联到 KnowHubDocumentTask 表。
     */
    private Long taskId;

    /**
     * 关联的文档 ID。
     * 关联到 KnowHubDocument 表。
     * 冗余存储文档 ID，便于直接查询某个文档的所有任务日志。
     */
    private Long documentId;

    /**
     * 阶段类型枚举值。
     * 标识此日志属于哪个执行阶段，如解析、切块、向量化、索引写入等。
     */
    private Integer stageType;

    /**
     * 事件类型枚举值。
     * 标识此日志记录的事件类型，如阶段开始、阶段完成、异常发生等。
     */
    private Integer eventType;

    /**
     * 日志级别枚举值。
     * 标识日志的严重程度：INFO（信息）、WARN（警告）、ERROR（错误）等。
     */
    private Integer logLevel;

    /**
     * 操作者类型枚举值。
     * 标识执行此操作的主体：SYSTEM（系统自动）、USER（用户手动）等。
     */
    private Integer operatorType;

    /**
     * 操作者 ID。
     * 如果是用户操作，记录用户的 ID；如果是系统操作，此字段可能为 null。
     */
    private Long operatorId;

    /**
     * 日志内容。
     * 日志的主要描述信息，如 "开始解析文档"、"切块完成，共生成 50 个切块" 等。
     */
    private String content;

    /**
     * 详细信息 JSON。
     * 存储日志的详细信息，以 JSON 格式存储。
     * 可以包含参数值、统计结果、堆栈信息等。
     */
    private String detailJson;
}
