package ai.knowhub.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【阶段性能基准展示】
 *
 * 作用：展示某个处理阶段的性能统计指标（基准测试结果）。
 * 用于性能监控和优化，帮助开发者了解每个阶段的耗时分布情况。
 *
 * 所属架构位置：属于系统的可观测性（Observability）模块，
 * 聚合了各处理阶段的 P50/P90/P99 等分位数统计信息。
 *
 * 设计模式说明：「返回值对象（VO）」模式，用于展示统计聚合结果。
 * 其中 P50/P90/P99 是性能监控中常用的百分位数指标：
 * - P50（中位数）：50% 的请求在此时间内完成
 * - P90：90% 的请求在此时间内完成
 * - P99：99% 的请求在此时间内完成
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StageBenchmarkVo {

    /**
     * 阶段编码（stageCode）
     * 标识是哪个处理阶段，如 "MEMORY"、"REWRITE"、"RAG_RETRIEVE" 等。
     */
    private String stageCode;

    /**
     * 执行模式（executionMode）
     * 标识使用的执行模式，如 "ReactAgent"、"GraphOnly" 等。
     */
    private String executionMode;

    /** P50 耗时（毫秒）—— 50% 的请求在此时间内完成 */
    private Long p50DurationMs;

    /** P90 耗时（毫秒）—— 90% 的请求在此时间内完成 */
    private Long p90DurationMs;

    /** P99 耗时（毫秒）—— 99% 的请求在此时间内完成 */
    private Long p99DurationMs;

    /** 平均耗时（毫秒） */
    private Long avgDurationMs;

    /** 最大耗时（毫秒） */
    private Long maxDurationMs;

    /** 最小耗时（毫秒） */
    private Long minDurationMs;

    /**
     * 样本数量（sampleCount）
     * 统计这些指标时使用的样本数量，样本越多统计越可靠。
     */
    private int sampleCount;
}
