package ai.knowhub.chat.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

import java.util.Date;

/**
 * 对话交换的追踪阶段（Trace Stage）数据实体。
 *
 * 对应数据库表：knowhub_chat_exchange_trace_stage
 *
 * 什么是追踪阶段？
 * 一次对话交换（Exchange）的处理过程被拆分为多个阶段（Stage），
 * 每个阶段负责 RAG 管道中的一个步骤。通过记录每个阶段的执行情况，
 * 可以精确分析性能瓶颈和定位问题。
 *
 * RAG 管道的典型阶段
 * 
 *   <b>query_rewrite</b> —— 查询改写：将用户问题优化为更适合检索的形式
 *   <b>retrieval</b> —— 检索：从知识库中检索相关文档片段
 *   <b>rerank</b> —— 重排序：对检索结果进行精排，提高相关性
 *   <b>prompt_assembly</b> —— Prompt 组装：将检索结果和对话历史组装成最终 Prompt
 *   <b>llm_generation</b> —— LLM 生成：调用大模型生成回答
 *   <b>recommendation</b> —— 追问推荐：生成后续问题建议
 * 阶段层级关系
 * 阶段之间存在父子关系（通过 parentStageId 关联），
 * 例如 "retrieval" 阶段下可能有 "keyword_retrieval" 和 "vector_retrieval" 子阶段。
 *
 * 继承关系
 * 继承自 BaseTableData，自动拥有创建时间、更新时间等公共字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_chat_exchange_trace_stage")
@EqualsAndHashCode(callSuper = true)
public class KnowHubChatExchangeTraceStage extends BaseTableData {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 对话编码（会话 ID）。
     */
    @TableField("dialogue_code")
    private String conversationId;

    /**
     * 对话交换 ID。
     * 标识这个阶段属于哪一次交换。
     */
    @TableField("exchange_id")
    private Long exchangeId;

    /**
     * 追踪 ID。
     * 用于分布式链路追踪，将一次请求的所有阶段串联起来。
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 阶段编码。
     * 阶段的唯一标识符，如 "query_rewrite"、"retrieval"、"rerank" 等。
     */
    @TableField("stage_code")
    private String stageCode;

    /**
     * 阶段名称。
     * 阶段的人类可读名称，如 "查询改写"、"检索"、"重排序" 等。
     */
    @TableField("stage_name")
    private String stageName;

    /**
     * 阶段顺序。
     * 该阶段在 RAG 管道中的执行顺序编号（从 0 开始），
     * 用于在调试面板中按正确顺序展示各阶段。
     */
    @TableField("stage_order")
    private Integer stageOrder;

    /**
     * 阶段层级。
     * 标识阶段的嵌套层级，0 表示顶层阶段，1 表示子阶段，以此类推。
     */
    @TableField("stage_level")
    private Integer stageLevel;

    /**
     * 父阶段 ID。
     * 如果当前阶段是子阶段，此字段指向其父阶段的 ID。
     * 顶层阶段的 parentStageId 为 null。
     */
    @TableField("parent_stage_id")
    private Long parentStageId;

    /**
     * 执行模式。
     * 标识使用了哪种执行策略，如 "react_agent"、"graph_only" 等。
     * 不同的执行模式对应不同的推理策略。
     */
    @TableField("execution_mode")
    private String executionMode;

    /**
     * 阶段状态。
     * 使用整数编码表示阶段的执行状态，例如：
     * 
     *   0 —— 等待中
     *   1 —— 执行中
     *   2 —— 已完成
     *   3 —— 失败
     * 
     */
    @TableField("stage_state")
    private Integer stageState;

    /**
     * 开始时间。
     */
    @TableField("start_time")
    private Date startTime;

    /**
     * 结束时间。
     */
    @TableField("end_time")
    private Date endTime;

    /**
     * 执行耗时（毫秒）。
     * 等于 endTime - startTime，用于性能分析。
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 阶段摘要文本。
     * 该阶段执行结果的简要描述，如"检索到 5 个相关片段"。
     */
    @TableField("summary_text")
    private String summaryText;

    /**
     * 错误信息。
     * 如果阶段执行失败，记录具体的错误信息。
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 阶段快照数据（JSON 格式）。
     * 记录该阶段的详细输入输出数据，用于深度调试。
     * 例如：检索阶段会记录查询词、召回结果、分数等。
     */
    @TableField("snapshot_json")
    private String snapshotJson;
}
