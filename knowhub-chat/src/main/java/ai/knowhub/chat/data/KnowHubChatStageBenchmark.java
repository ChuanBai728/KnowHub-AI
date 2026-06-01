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
 * 各阶段性能基准（Benchmark）的数据实体。
 *
 * 对应数据库表：knowhub_chat_stage_benchmark
 *
 * 什么是阶段基准？
 * 系统会持续收集 RAG 管道中每个阶段的执行耗时数据，
 * 并计算各种统计指标（如 P50、P90、P99、平均值等），
 * 形成性能基准报告。这些数据用于：
 * 
 *   <b>性能监控</b>：实时了解系统各阶段的运行状况
 *   <b>瓶颈定位</b>：找出耗时最长的阶段，针对性优化
 *   <b>趋势分析</b>：通过历史数据观察性能变化趋势
 *   <b>SLA 保障</b>：确保各阶段的响应时间满足服务水平协议
 * 百分位数（Percentile）说明
 * 
 *   <b>P50</b>（中位数）：50% 的请求耗时低于此值，代表"典型"体验
 *   <b>P90</b>：90% 的请求耗时低于此值，代表"大多数"用户的体验
 *   <b>P99</b>：99% 的请求耗时低于此值，代表"几乎所有"用户的体验
 * 
 * 例如，P50=100ms、P90=300ms、P99=1000ms 意味着：
 * 一半请求在 100ms 内完成，90% 在 300ms 内完成，只有 1% 超过 1 秒。
 *
 * 继承关系
 * 继承自 BaseTableData，自动拥有创建时间、更新时间等公共字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_chat_stage_benchmark")
@EqualsAndHashCode(callSuper = true)
public class KnowHubChatStageBenchmark extends BaseTableData {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 阶段编码。
     * 标识统计的是哪个阶段，如 "query_rewrite"、"retrieval"、"rerank" 等。
     */
    @TableField("stage_code")
    private String stageCode;

    /**
     * 执行模式。
     * 标识统计的是哪种执行模式下的数据，如 "react_agent"、"graph_only" 等。
     * 不同执行模式的性能特征可能不同。
     */
    @TableField("execution_mode")
    private String executionMode;

    /**
     * P50 耗时（毫秒）。
     * 50% 的请求耗时低于此值（中位数）。
     */
    @TableField("p50_duration_ms")
    private Long p50DurationMs;

    /**
     * P90 耗时（毫秒）。
     * 90% 的请求耗时低于此值。
     */
    @TableField("p90_duration_ms")
    private Long p90DurationMs;

    /**
     * P99 耗时（毫秒）。
     * 99% 的请求耗时低于此值。
     */
    @TableField("p99_duration_ms")
    private Long p99DurationMs;

    /**
     * 平均耗时（毫秒）。
     * 所有采样数据的算术平均值。
     */
    @TableField("avg_duration_ms")
    private Long avgDurationMs;

    /**
     * 最大耗时（毫秒）。
     * 采样数据中的最大值，用于了解最坏情况。
     */
    @TableField("max_duration_ms")
    private Long maxDurationMs;

    /**
     * 最小耗时（毫秒）。
     * 采样数据中的最小值，代表最佳情况。
     */
    @TableField("min_duration_ms")
    private Long minDurationMs;

    /**
     * 采样数量。
     * 统计时使用的数据点总数。采样数越多，统计结果越可靠。
     */
    @TableField("sample_count")
    private Integer sampleCount;

    /**
     * 最近的耗时数据（JSON 格式）。
     * 存储最近 N 次执行的耗时数据，用于绘制趋势图表。
     * 格式示例：[120, 135, 98, 142, 110]
     */
    @TableField("recent_durations")
    private String recentDurations;

    /**
     * 最后更新时间。
     * 记录基准数据最后一次被更新的时间。
     */
    @TableField("last_update_time")
    private Date lastUpdateTime;
}
