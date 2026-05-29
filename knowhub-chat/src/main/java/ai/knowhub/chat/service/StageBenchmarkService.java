package ai.knowhub.chat.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import ai.knowhub.chat.data.KnowHubChatStageBenchmark;
import ai.knowhub.chat.mapper.KnowHubChatStageBenchmarkMapper;
import ai.knowhub.chat.model.StageBenchmarkVo;
import ai.knowhub.enums.BusinessStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 【阶段性能基准服务】
 *
 * 收集和统计各执行阶段的耗时数据，用于性能监控和优化。
 *
 * 背景：一次 AI 会话会经历多个阶段（查询改写、检索、重排、生成等），
 * 每个阶段的耗时会直接影响用户体验。此服务通过收集历史耗时数据，
 * 计算 P50/P90/P99 分位数和平均值，帮助开发者：
 *   1. 识别性能瓶颈（哪个阶段最慢）
 *   2. 监控性能趋势（是否在变慢）
 *   3. 设置合理的超时阈值
 *
 * 数据结构：
 * - 每个 (stageCode, executionMode) 维度对应一条基准记录
 * - 记录中保存最近 200 个样本的耗时列表（滑动窗口）
 * - 每次新增样本后重新计算统计数据
 *
 * 统计指标：
 * - avgDurationMs: 平均耗时
 * - p50DurationMs: 中位数耗时（50% 的请求在此时间内完成）
 * - p90DurationMs: 90 分位耗时（90% 的请求在此时间内完成）
 * - p99DurationMs: 99 分位耗时（99% 的请求在此时间内完成）
 * - maxDurationMs: 最大耗时
 * - minDurationMs: 最小耗时
 * - sampleCount: 样本数量
 *
 * 设计模式：滑动窗口统计模式。只保留最近 MAX_RECENT_SAMPLES（200）个样本，
 * 避免历史数据无限增长，同时保证统计数据的时效性。
 *
 * 使用场景：ConversationTraceRecorder 在阶段完成时调用 recordDuration() 记录耗时。
 */
@Slf4j
@Service
public class StageBenchmarkService {

    /** 滑动窗口最大样本数，超过此数量时丢弃最早的样本 */
    private static final int MAX_RECENT_SAMPLES = 200;

    /** Jackson TypeReference，用于反序列化 List<Long> 类型 */
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};

    /** 阶段基准表的 MyBatis-Plus Mapper */
    private final KnowHubChatStageBenchmarkMapper benchmarkMapper;

    /** Jackson ObjectMapper，用于 JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    /** 百度 UID 生成器，用于生成唯一主键 */
    @Resource
    private UidGenerator uidGenerator;

    /**
     * 构造函数。
     *
     * @param benchmarkMapper 阶段基准 Mapper
     * @param objectMapper    Jackson ObjectMapper
     */
    public StageBenchmarkService(KnowHubChatStageBenchmarkMapper benchmarkMapper,
                                 ObjectMapper objectMapper) {
        this.benchmarkMapper = benchmarkMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录一个阶段的耗时数据。
     *
     * 执行逻辑：
     * 1. 根据 stageCode + executionMode 查询是否已有基准记录
     * 2. 如果没有，创建新记录（首次记录）
     * 3. 如果有，将新耗时追加到最近耗时列表，裁剪到 MAX_RECENT_SAMPLES，
     *    重新计算统计数据并更新
     *
     * @param stageCode     阶段代码（如 "QUERY_REWRITE"、"RETRIEVAL"）
     * @param executionMode 执行模式（如 "RAG"、"REACT_AGENT"）
     * @param durationMs    耗时（毫秒）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordDuration(String stageCode, String executionMode, long durationMs) {
        if (StrUtil.isBlank(stageCode) || durationMs < 0) {
            return;
        }
        String normalizedMode = StrUtil.blankToDefault(executionMode, "UNKNOWN");
        // 查询是否已有基准记录
        KnowHubChatStageBenchmark existing = benchmarkMapper.selectOne(
            new LambdaQueryWrapper<KnowHubChatStageBenchmark>()
                .eq(KnowHubChatStageBenchmark::getStageCode, stageCode)
                .eq(KnowHubChatStageBenchmark::getExecutionMode, normalizedMode)
                .last("LIMIT 1")
        );

        // 首次记录：创建新基准记录
        if (existing == null) {
            KnowHubChatStageBenchmark benchmark = new KnowHubChatStageBenchmark();
            benchmark.setId(uidGenerator.getUid());
            benchmark.setStageCode(stageCode);
            benchmark.setExecutionMode(normalizedMode);
            List<Long> durations = new ArrayList<>();
            durations.add(durationMs);
            benchmark.setRecentDurations(writeJson(durations));
            benchmark.setSampleCount(1);
            benchmark.setAvgDurationMs(durationMs);
            benchmark.setP50DurationMs(durationMs);
            benchmark.setP90DurationMs(durationMs);
            benchmark.setP99DurationMs(durationMs);
            benchmark.setMaxDurationMs(durationMs);
            benchmark.setMinDurationMs(durationMs);
            benchmark.setLastUpdateTime(new Date());
            benchmark.setStatus(BusinessStatus.YES.getCode());
            benchmarkMapper.insert(benchmark);
            return;
        }

        // 已有记录：追加新样本并重新计算统计数据
        List<Long> durations = readDurations(existing.getRecentDurations());
        durations.add(durationMs);
        // 裁剪到滑动窗口大小
        if (durations.size() > MAX_RECENT_SAMPLES) {
            durations = new ArrayList<>(durations.subList(durations.size() - MAX_RECENT_SAMPLES, durations.size()));
        }

        // 排序后计算分位数
        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        int size = sorted.size();

        benchmarkMapper.update(null,
            new LambdaUpdateWrapper<KnowHubChatStageBenchmark>()
                .eq(KnowHubChatStageBenchmark::getId, existing.getId())
                .set(KnowHubChatStageBenchmark::getRecentDurations, writeJson(durations))
                .set(KnowHubChatStageBenchmark::getSampleCount, size)
                .set(KnowHubChatStageBenchmark::getAvgDurationMs, sorted.stream().mapToLong(Long::longValue).sum() / size)
                .set(KnowHubChatStageBenchmark::getP50DurationMs, sorted.get((int) (size * 0.5)))
                .set(KnowHubChatStageBenchmark::getP90DurationMs, sorted.get(Math.min((int) (size * 0.9), size - 1)))
                .set(KnowHubChatStageBenchmark::getP99DurationMs, sorted.get(Math.min((int) (size * 0.99), size - 1)))
                .set(KnowHubChatStageBenchmark::getMaxDurationMs, sorted.get(size - 1))
                .set(KnowHubChatStageBenchmark::getMinDurationMs, sorted.get(0))
                .set(KnowHubChatStageBenchmark::getLastUpdateTime, new Date())
        );
    }

    /**
     * 查询所有阶段的性能基准数据。
     *
     * @return 阶段基准展示列表，按阶段代码升序排列
     */
    @Transactional(readOnly = true)
    public List<StageBenchmarkVo> listAll() {
        return benchmarkMapper.selectList(
                new LambdaQueryWrapper<KnowHubChatStageBenchmark>()
                    .eq(KnowHubChatStageBenchmark::getStatus, BusinessStatus.YES.getCode())
                    .orderByAsc(KnowHubChatStageBenchmark::getStageCode)
            )
            .stream()
            .map(this::toVo)
            .toList();
    }

    /**
     * 将数据库实体转换为返回值对象。
     */
    private StageBenchmarkVo toVo(KnowHubChatStageBenchmark entity) {
        return new StageBenchmarkVo(
            entity.getStageCode(),
            entity.getExecutionMode(),
            entity.getP50DurationMs(),
            entity.getP90DurationMs(),
            entity.getP99DurationMs(),
            entity.getAvgDurationMs(),
            entity.getMaxDurationMs(),
            entity.getMinDurationMs(),
            entity.getSampleCount() == null ? 0 : entity.getSampleCount()
        );
    }

    /**
     * 从 JSON 字符串反序列化耗时列表。
     */
    private List<Long> readDurations(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<Long> result = objectMapper.readValue(json, LONG_LIST_TYPE);
            return result == null ? new ArrayList<>() : new ArrayList<>(result);
        } catch (Exception exception) {
            log.warn("解析性能基准耗时记录失败", exception);
            return new ArrayList<>();
        }
    }

    /**
     * 将对象序列化为 JSON 字符串。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("序列化性能基准数据失败", exception);
        }
    }
}
