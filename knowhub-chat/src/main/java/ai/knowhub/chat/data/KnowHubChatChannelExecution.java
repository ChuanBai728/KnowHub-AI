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

import java.math.BigDecimal;
import java.util.Date;

/**
 * RAG 检索通道执行记录的数据实体。
 *
 * 对应数据库表：knowhub_chat_channel_execution
 *
 * 什么是检索通道（Channel）？
 * RAG（Retrieval-Augmented Generation，检索增强生成）系统支持多种检索方式，
 * 每种检索方式称为一个"通道"：
 * 
 *   <b>关键词检索（Keyword）</b>：基于 Elasticsearch 的全文检索，擅长精确匹配
 *   <b>向量检索（Vector）</b>：基于向量数据库的语义检索，擅长语义理解
 * 
 * 系统会同时使用多个通道进行检索，然后通过 RRF（Reciprocal Rank Fusion）
 * 算法将各通道的结果融合排序。
 *
 * 该实体记录的信息
 * 每次检索通道执行时，会记录：执行状态、耗时、召回数量、接受数量、
 * 分数统计等信息，用于性能监控和质量分析。
 *
 * 继承关系
 * 继承自 BaseTableData，自动拥有创建时间、更新时间等公共字段。
 *
 * 涉及的注解
 * 
 *   @TableName —— MyBatis-Plus 注解，指定对应的数据库表名
 *   @TableId —— 标记主键字段
 *   @TableField —— 显式映射数据库字段名（当字段名与 Java 属性名不一致时使用）
 *   @EqualsAndHashCode(callSuper = true) —— Lombok 注解，生成 equals/hashCode 时包含父类字段
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_chat_channel_execution")
@EqualsAndHashCode(callSuper = true)
public class KnowHubChatChannelExecution extends BaseTableData {

    /**
     * 主键 ID。
     * 由应用程序手动设置（IdType.INPUT）。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 对话编码（会话 ID）。
     * 标识这次检索属于哪个会话。数据库字段名为 "dialogue_code"。
     */
    @TableField("dialogue_code")
    private String conversationId;

    /**
     * 对话交换 ID。
     * 标识这次检索属于哪一次"用户提问-AI回答"的交换。
     * 一个会话中可能有多次交换。
     */
    @TableField("exchange_id")
    private Long exchangeId;

    /**
     * 追踪 ID。
     * 用于分布式链路追踪，可以将一次请求涉及的所有操作串联起来，
     * 方便排查跨服务的问题。
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 子问题索引。
     * 当用户的问题被拆分为多个子问题时，此字段记录当前是第几个子问题（从 0 开始）。
     */
    @TableField("sub_question_index")
    private Integer subQuestionIndex;

    /**
     * 子问题内容。
     * 经过查询改写后生成的子问题文本。
     */
    @TableField("sub_question")
    private String subQuestion;

    /**
     * 通道类型。
     * 标识使用的检索通道，如 "keyword"（关键词检索）或 "vector"（向量检索）。
     */
    @TableField("channel_type")
    private String channelType;

    /**
     * 执行状态。
     * 使用整数编码表示状态，如：0=进行中，1=成功，2=失败等。
     */
    @TableField("execution_state")
    private Integer executionState;

    /**
     * 开始时间。
     * 检索开始执行的时间戳。
     */
    @TableField("start_time")
    private Date startTime;

    /**
     * 结束时间。
     * 检索执行完成的时间戳。
     */
    @TableField("end_time")
    private Date endTime;

    /**
     * 执行耗时（毫秒）。
     * 等于 endTime - startTime，用于性能监控。
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 召回数量。
     * 该通道从知识库中检索出的文档片段总数。
     */
    @TableField("recalled_count")
    private Integer recalledCount;

    /**
     * 接受数量。
     * 经过初步筛选（如分数阈值过滤）后保留的文档片段数量。
     */
    @TableField("accepted_count")
    private Integer acceptedCount;

    /**
     * 最终选中数量。
     * 经过 RRF 融合和重排序后，最终被选中用于生成回答的文档片段数量。
     */
    @TableField("final_selected_count")
    private Integer finalSelectedCount;

    /**
     * 平均检索分数。
     * 所有召回结果的平均相似度分数，用于评估检索质量。
     */
    @TableField("avg_score")
    private BigDecimal avgScore;

    /**
     * 最高检索分数。
     * 召回结果中的最高相似度分数。
     */
    @TableField("max_score")
    private BigDecimal maxScore;

    /**
     * 最低检索分数。
     * 召回结果中的最低相似度分数。
     */
    @TableField("min_score")
    private BigDecimal minScore;

    /**
     * 配置快照（JSON 格式）。
     * 记录执行时使用的检索配置参数（如 topK、阈值等），
     * 用于问题复现和配置审计。
     */
    @TableField("config_snapshot")
    private String configSnapshot;

    /**
     * 错误信息。
     * 如果检索执行失败，记录具体的错误信息。
     */
    @TableField("error_message")
    private String errorMessage;
}
