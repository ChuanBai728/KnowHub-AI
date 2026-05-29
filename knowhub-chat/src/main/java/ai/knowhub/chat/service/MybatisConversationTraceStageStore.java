package ai.knowhub.chat.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import ai.knowhub.chat.data.KnowHubChatExchangeTraceStage;
import ai.knowhub.chat.mapper.KnowHubChatExchangeTraceStageMapper;
import ai.knowhub.chat.model.trace.ConversationTraceStageCode;
import ai.knowhub.chat.model.trace.ConversationTraceStageState;
import ai.knowhub.chat.model.trace.ConversationTraceStageVo;
import ai.knowhub.enums.BusinessStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【会话追踪阶段存储 - MyBatis 实现】
 *
 * ConversationTraceStageStore 接口的 MyBatis-Plus 实现类，
 * 将追踪阶段数据持久化到 MySQL 数据库的 knowhub_chat_exchange_trace_stage 表。
 *
 * 数据库表结构（knowhub_chat_exchange_trace_stage）：
 * - id: 主键（百度 UID 生成器生成的唯一长整数）
 * - conversation_id: 会话 ID
 * - exchange_id: 问答回合 ID
 * - trace_id: 追踪 ID
 * - stage_code: 阶段代码（如 QUERY_REWRITE）
 * - stage_name: 阶段名称（如"查询改写"）
 * - stage_order: 阶段排序号
 * - stage_level: 阶段层级（1=顶层，2=子阶段）
 * - parent_stage_id: 父阶段 ID
 * - execution_mode: 执行模式（如 RAG、REACT_AGENT）
 * - stage_state: 阶段状态（RUNNING=运行中、COMPLETED=完成、FAILED=失败）
 * - start_time: 开始时间
 * - end_time: 结束时间
 * - duration_ms: 耗时（毫秒）
 * - summary_text: 阶段摘要
 * - error_message: 错误信息
 * - snapshot_json: 快照数据（JSON 格式）
 * - status: 业务状态（1=有效）
 *
 * 设计模式：仓储模式（Repository Pattern）+ 适配器模式（Adapter）。
 * 将接口定义的领域操作适配为 MyBatis-Plus 的数据库操作。
 *
 * 注意：snapshot 字段使用 ObjectMapper 序列化为 JSON 字符串存储，
 * 读取时反序列化为 Map<String, Object>。
 */
@Repository
public class MybatisConversationTraceStageStore implements ConversationTraceStageStore {

    /** Jackson TypeReference，用于反序列化 Map<String, Object> 类型 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /** 追踪阶段表的 MyBatis-Plus Mapper */
    private final KnowHubChatExchangeTraceStageMapper traceStageMapper;

    /** Jackson ObjectMapper，用于 JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    /** 百度 UID 生成器，用于生成唯一主键 */
    @Resource
    private UidGenerator uidGenerator;

    /**
     * 构造函数。
     *
     * @param traceStageMapper 追踪阶段 Mapper
     * @param objectMapper     Jackson ObjectMapper
     */
    public MybatisConversationTraceStageStore(KnowHubChatExchangeTraceStageMapper traceStageMapper,
                                              ObjectMapper objectMapper) {
        this.traceStageMapper = traceStageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 开始一个新的追踪阶段。
     *
     * 向数据库插入一条状态为 RUNNING 的阶段记录。
     *
     * @return 新创建的阶段 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long startStage(String conversationId,
                           long exchangeId,
                           String traceId,
                           ConversationTraceStageCode stageCode,
                           int stageLevel,
                           Long parentStageId,
                           String executionMode,
                           String summaryText,
                           Object snapshot) {
        long stageId = uidGenerator.getUid();
        KnowHubChatExchangeTraceStage stage = new KnowHubChatExchangeTraceStage();
        stage.setId(stageId);
        stage.setConversationId(conversationId);
        stage.setExchangeId(exchangeId);
        stage.setTraceId(traceId);
        stage.setStageCode(stageCode.getCode());
        stage.setStageName(stageCode.getLabel());
        stage.setStageOrder(stageCode.getOrder());
        stage.setStageLevel(stageLevel);
        stage.setParentStageId(parentStageId);
        stage.setExecutionMode(StrUtil.blankToDefault(executionMode, ""));
        stage.setStageState(ConversationTraceStageState.RUNNING.getCode());
        stage.setStartTime(new Date());
        stage.setSummaryText(StrUtil.blankToDefault(summaryText, ""));
        stage.setSnapshotJson(writeNullableJson(snapshot));
        stage.setStatus(BusinessStatus.YES.getCode());
        traceStageMapper.insert(stage);
        return stageId;
    }

    /**
     * 结束一个追踪阶段。
     *
     * 更新指定阶段记录的状态、结束时间、耗时、摘要、错误信息和快照。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishStage(long stageId,
                            ConversationTraceStageState stageState,
                            String summaryText,
                            String errorMessage,
                            Object snapshot,
                            long durationMs) {
        traceStageMapper.update(
            null,
            new LambdaUpdateWrapper<KnowHubChatExchangeTraceStage>()
                .eq(KnowHubChatExchangeTraceStage::getId, stageId)
                .set(KnowHubChatExchangeTraceStage::getStageState, stageState.getCode())
                .set(KnowHubChatExchangeTraceStage::getEndTime, new Date())
                .set(KnowHubChatExchangeTraceStage::getDurationMs, Math.max(durationMs, 0L))
                .set(KnowHubChatExchangeTraceStage::getSummaryText, StrUtil.blankToDefault(summaryText, ""))
                .set(KnowHubChatExchangeTraceStage::getErrorMessage, StrUtil.blankToDefault(errorMessage, ""))
                .set(KnowHubChatExchangeTraceStage::getSnapshotJson, writeNullableJson(snapshot))
        );
    }

    /**
     * 查询指定问答回合的所有追踪阶段。
     *
     * 按阶段排序号、开始时间、ID 三重排序，保证输出顺序稳定。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ConversationTraceStageVo> listStageViews(String conversationId, long exchangeId) {
        return traceStageMapper.selectList(
                new LambdaQueryWrapper<KnowHubChatExchangeTraceStage>()
                    .eq(KnowHubChatExchangeTraceStage::getConversationId, conversationId)
                    .eq(KnowHubChatExchangeTraceStage::getExchangeId, exchangeId)
                    .orderByAsc(KnowHubChatExchangeTraceStage::getStageOrder)
                    .orderByAsc(KnowHubChatExchangeTraceStage::getStartTime)
                    .orderByAsc(KnowHubChatExchangeTraceStage::getId)
            )
            .stream()
            .map(this::toVo)
            .toList();
    }

    /**
     * 删除指定会话的所有追踪阶段数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStages(String conversationId) {
        traceStageMapper.delete(
            new LambdaQueryWrapper<KnowHubChatExchangeTraceStage>()
                .eq(KnowHubChatExchangeTraceStage::getConversationId, conversationId)
        );
    }

    /**
     * 将数据库实体转换为返回值对象。
     */
    private ConversationTraceStageVo toVo(KnowHubChatExchangeTraceStage stage) {
        return new ConversationTraceStageVo(
            stage.getId(),
            StrUtil.blankToDefault(stage.getTraceId(), ""),
            StrUtil.blankToDefault(stage.getStageCode(), ""),
            StrUtil.blankToDefault(stage.getStageName(), ""),
            stage.getStageOrder(),
            stage.getStageLevel(),
            stage.getParentStageId(),
            StrUtil.blankToDefault(stage.getExecutionMode(), ""),
            ConversationTraceStageState.fromCode(stage.getStageState()).name(),
            toInstant(stage.getStartTime()),
            toInstant(stage.getEndTime()),
            stage.getDurationMs(),
            StrUtil.blankToDefault(stage.getSummaryText(), ""),
            StrUtil.blankToDefault(stage.getErrorMessage(), ""),
            readSnapshot(stage.getSnapshotJson())
        );
    }

    /** 将 Date 转为 Instant */
    private Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }

    /** 将对象序列化为 JSON 字符串（可为 null） */
    private String writeNullableJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception exception) {
            throw new IllegalStateException("序列化阶段轨迹快照失败", exception);
        }
    }

    /** 将 JSON 字符串反序列化为 Map（用于快照数据） */
    private Map<String, Object> readSnapshot(String value) {
        if (StrUtil.isBlank(value)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(value, MAP_TYPE);
            return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
        }
        catch (Exception exception) {
            throw new IllegalStateException("解析阶段轨迹快照失败", exception);
        }
    }
}
