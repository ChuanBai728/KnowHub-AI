package ai.knowhub.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

import java.util.Date;

/**
 * 文档任务实体类。
 *
 * 对应数据库表 knowhub_document_task，记录文档处理的每个异步任务。
 *
 * 在文档管理模块中，以下操作都是异步任务：
 * 
 *   <b>解析任务</b>（taskType=parse）：提取文档文本、识别结构、分析内容质量。
 *   <b>索引构建任务</b>（taskType=index）：将切块写入 Elasticsearch 和向量数据库。
 *   <b>画像生成任务</b>（taskType=profile）：使用 LLM 生成文档画像。
 * 任务通过 Kafka 消息队列异步执行，实现了上传接口的快速响应。
 * 用户上传文档后立即返回，后台消费者异步处理耗时的解析和索引构建工作。
 *
 * 任务状态流转：
 * 
 *   创建 → 待执行 → 执行中 → 已完成
 *                     ↓
 *                  执行失败（可重试）
 * 设计特点：
 * 
 *   支持任务重试：通过 retryCount 和 errorCode 管理失败重试。
 *   支持阶段跟踪：通过 currentStage 跟踪任务执行到哪个阶段。
 *   支持耗时统计：通过 startTime/finishTime/costMillis 统计性能。
 *   支持策略快照：通过 strategySnapshot 保留任务执行时的策略配置。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document_task")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocumentTask extends BaseTableData {

    /**
     * 任务主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 关联的文档 ID。
     * 关联到 KnowHubDocument 表。
     */
    private Long documentId;

    /**
     * 关联的策略方案 ID。
     * 关联到 KnowHubDocumentStrategyPlan 表。
     * 标识此任务使用的切块策略。
     */
    private Long planId;

    /**
     * 任务类型枚举值。
     * 标识任务的类型：解析、索引构建、画像生成等。
     */
    private Integer taskType;

    /**
     * 任务状态枚举值。
     * 标识任务的当前状态：待执行、执行中、已完成、失败等。
     */
    private Integer taskStatus;

    /**
     * 当前执行阶段枚举值。
     * 跟踪任务执行到哪个阶段，如解析阶段、切块阶段、向量化阶段等。
     */
    private Integer currentStage;

    /**
     * 触发来源枚举值。
     * 标识任务的触发方式：用户上传自动触发、手动重新执行、系统定时任务等。
     */
    private Integer triggerSource;

    /**
     * 策略快照 JSON。
     * 任务执行时的策略配置快照，即使后续策略发生变化，
     * 已提交的任务仍然按照原始策略执行。
     */
    private String strategySnapshot;

    /**
     * 重试次数。
     * 记录任务失败后的重试次数，配合最大重试次数控制避免无限重试。
     */
    private Integer retryCount;

    /**
     * 任务开始时间。
     * 任务开始执行的时间戳。
     */
    private Date startTime;

    /**
     * 任务完成时间。
     * 任务执行完成（成功或失败）的时间戳。
     */
    private Date finishTime;

    /**
     * 任务耗时（毫秒）。
     * 从任务开始到完成的耗时，用于性能监控和优化。
     */
    private Long costMillis;

    /**
     * 错误码。
     * 任务失败时的错误码，便于程序化处理错误。
     */
    private String errorCode;

    /**
     * 错误信息。
     * 任务失败时的详细错误描述，便于排查问题。
     */
    private String errorMsg;

    /**
     * 扩展信息 JSON。
     * 存储任务的额外信息，以 JSON 格式存储，提供灵活的扩展能力。
     */
    private String extJson;
}
