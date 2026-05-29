package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 文档任务日志查询返回值对象（Document Task Log Query Vo）
 *
 * 【类的作用】
 * 用于查询文档处理任务的日志信息，包含任务的基本信息、执行状态、
 * 时间统计和详细的日志记录列表。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于任务日志查询页面的数据展示。
 * 帮助用户了解文档处理任务的执行过程和结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTaskLogQueryVo {

    /**
     * 任务ID（Task ID）
     * 处理任务的唯一标识
     */
    private Long taskId;

    /**
     * 文档ID（Document ID）
     * 关联的文档标识
     */
    private Long documentId;

    /**
     * 任务类型编码（Task Type）
     * 任务类型的编码值
     */
    private Integer taskType;

    /**
     * 任务类型名称（Task Type Name）
     */
    private String taskTypeName;

    /**
     * 任务状态编码（Task Status）
     * 任务的当前状态
     */
    private Integer taskStatus;

    /**
     * 任务状态名称（Task Status Name）
     */
    private String taskStatusName;

    /**
     * 当前阶段编码（Current Stage）
     * 任务当前执行到的阶段
     */
    private Integer currentStage;

    /**
     * 当前阶段名称（Current Stage Name）
     */
    private String currentStageName;

    /**
     * 开始时间（Start Time）
     * 任务开始执行的时间
     */
    private Date startTime;

    /**
     * 完成时间（Finish Time）
     * 任务完成的时间
     */
    private Date finishTime;

    /**
     * 耗时毫秒数（Cost Millis）
     * 任务执行的总耗时（毫秒）
     */
    private Long costMillis;

    /**
     * 错误编码（Error Code）
     * 任务失败时的错误编码
     */
    private String errorCode;

    /**
     * 错误信息（Error Message）
     * 任务失败时的错误描述
     */
    private String errorMsg;

    /**
     * 日志总数（Total）
     * 日志记录的总数
     */
    private Long total;

    /**
     * 日志记录列表（Logs）
     * 任务的详细日志记录
     */
    private List<DocumentTaskLogVo> logs;
}
