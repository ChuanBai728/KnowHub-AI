package ai.knowhub.chat.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import ai.knowhub.chat.data.KnowHubChatChannelExecution;
import ai.knowhub.chat.data.KnowHubChatRetrievalResult;
import ai.knowhub.chat.mapper.KnowHubChatChannelExecutionMapper;
import ai.knowhub.chat.mapper.KnowHubChatRetrievalResultMapper;
import ai.knowhub.chat.model.ChannelExecutionVo;
import ai.knowhub.chat.model.RetrievalResultVo;
import ai.knowhub.enums.BusinessStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 【检索观察存储 - MyBatis 实现】
 *
 * RetrievalObserveStore 接口的 MyBatis-Plus 实现类，
 * 将 RAG 检索过程中的检索结果和通道执行详情持久化到 MySQL 数据库。
 *
 * 涉及两张表：
 * 1. knowhub_chat_retrieval_result: 存储每条检索结果（文档切片）
 *    - 包含：子问题索引、通道类型、排名、分数、是否通过门控、是否被选中等
 * 2. knowhub_chat_channel_execution: 存储每个检索通道的执行摘要
 *    - 包含：执行状态、耗时、召回数、接受数、最终选中数、平均分等
 *
 * 设计模式：仓储模式（Repository Pattern）。
 * 将检索观察的存储操作封装在接口后面，业务层不需要关心数据库细节。
 *
 * 使用场景：
 * - ConversationTraceRecorder.recordRetrievalResults() 调用 batchSaveResults()
 * - ConversationTraceRecorder.recordChannelExecutions() 调用 batchSaveChannelExecutions()
 * - BusinessChatService.getRetrievalResults() 调用 listResults()
 * - BusinessChatService.resetConversation() 调用 deleteByConversation()
 */
@Repository
public class MybatisRetrievalObserveStore implements RetrievalObserveStore {

    /** 检索结果表的 MyBatis-Plus Mapper */
    private final KnowHubChatRetrievalResultMapper retrievalResultMapper;

    /** 通道执行表的 MyBatis-Plus Mapper */
    private final KnowHubChatChannelExecutionMapper channelExecutionMapper;

    /** 百度 UID 生成器，用于生成唯一主键 */
    @Resource
    private UidGenerator uidGenerator;

    /**
     * 构造函数。
     *
     * @param retrievalResultMapper  检索结果 Mapper
     * @param channelExecutionMapper 通道执行 Mapper
     */
    public MybatisRetrievalObserveStore(KnowHubChatRetrievalResultMapper retrievalResultMapper,
                                        KnowHubChatChannelExecutionMapper channelExecutionMapper) {
        this.retrievalResultMapper = retrievalResultMapper;
        this.channelExecutionMapper = channelExecutionMapper;
    }

    /**
     * 批量保存检索结果。
     *
     * 将每条 RetrievalResultVo 转换为数据库实体后逐条插入。
     * 使用 @Transactional 保证批量插入的原子性。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveResults(String conversationId, long exchangeId, List<RetrievalResultVo> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (RetrievalResultVo view : results) {
            KnowHubChatRetrievalResult entity = new KnowHubChatRetrievalResult();
            entity.setId(uidGenerator.getUid());
            entity.setConversationId(conversationId);
            entity.setExchangeId(exchangeId);
            entity.setTraceId(view.getTraceId());
            entity.setSubQuestionIndex(view.getSubQuestionIndex());
            entity.setSubQuestion(view.getSubQuestion());
            entity.setChannelType(view.getChannelType());
            entity.setChannelRank(view.getChannelRank());
            entity.setRrfRank(view.getRrfRank());
            entity.setFinalRank(view.getFinalRank());
            entity.setOriginalScore(view.getOriginalScore());
            entity.setRrfScore(view.getRrfScore());
            entity.setRerankScore(view.getRerankScore());
            entity.setGatePassed(view.isGatePassed() ? 1 : 0);
            entity.setIsElevated(view.isElevated() ? 1 : 0);
            entity.setIsSelected(view.isSelected() ? 1 : 0);
            entity.setSelectionReason(view.getSelectionReason());
            entity.setDocumentId(view.getDocumentId());
            entity.setDocumentName(view.getDocumentName());
            entity.setChunkId(view.getChunkId());
            entity.setChunkNo(view.getChunkNo());
            entity.setParentBlockId(view.getParentBlockId());
            entity.setParentBlockNo(view.getParentBlockNo());
            entity.setSectionPath(view.getSectionPath());
            entity.setChunkTextPreview(view.getChunkTextPreview());
            entity.setChunkCharCount(view.getChunkCharCount());
            entity.setStatus(BusinessStatus.YES.getCode());
            retrievalResultMapper.insert(entity);
        }
    }

    /**
     * 批量保存通道执行详情。
     *
     * 将每条 ChannelExecutionVo 转换为数据库实体后逐条插入。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveChannelExecutions(String conversationId, long exchangeId, List<ChannelExecutionVo> executions) {
        if (executions == null || executions.isEmpty()) {
            return;
        }
        for (ChannelExecutionVo view : executions) {
            KnowHubChatChannelExecution entity = new KnowHubChatChannelExecution();
            entity.setId(uidGenerator.getUid());
            entity.setConversationId(conversationId);
            entity.setExchangeId(exchangeId);
            entity.setTraceId(view.getTraceId());
            entity.setSubQuestionIndex(view.getSubQuestionIndex());
            entity.setSubQuestion(view.getSubQuestion());
            entity.setChannelType(view.getChannelType());
            entity.setExecutionState(view.getExecutionState());
            entity.setStartTime(view.getStartTime() == null ? null : Date.from(view.getStartTime()));
            entity.setEndTime(view.getEndTime() == null ? null : Date.from(view.getEndTime()));
            entity.setDurationMs(view.getDurationMs());
            entity.setRecalledCount(view.getRecalledCount());
            entity.setAcceptedCount(view.getAcceptedCount());
            entity.setFinalSelectedCount(view.getFinalSelectedCount());
            entity.setAvgScore(view.getAvgScore());
            entity.setMaxScore(view.getMaxScore());
            entity.setMinScore(view.getMinScore());
            entity.setErrorMessage(view.getErrorMessage());
            entity.setStatus(BusinessStatus.YES.getCode());
            channelExecutionMapper.insert(entity);
        }
    }

    /**
     * 查询指定问答回合的所有检索结果。
     *
     * 按子问题索引和最终排名排序。
     */
    @Override
    @Transactional(readOnly = true)
    public List<RetrievalResultVo> listResults(String conversationId, long exchangeId) {
        return retrievalResultMapper.selectList(
                new LambdaQueryWrapper<KnowHubChatRetrievalResult>()
                    .eq(KnowHubChatRetrievalResult::getConversationId, conversationId)
                    .eq(KnowHubChatRetrievalResult::getExchangeId, exchangeId)
                    .orderByAsc(KnowHubChatRetrievalResult::getSubQuestionIndex)
                    .orderByAsc(KnowHubChatRetrievalResult::getFinalRank)
            )
            .stream()
            .map(this::toResultVo)
            .toList();
    }

    /**
     * 查询指定问答回合的所有通道执行详情。
     *
     * 按子问题索引和开始时间排序。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ChannelExecutionVo> listChannelExecutions(String conversationId, long exchangeId) {
        return channelExecutionMapper.selectList(
                new LambdaQueryWrapper<KnowHubChatChannelExecution>()
                    .eq(KnowHubChatChannelExecution::getConversationId, conversationId)
                    .eq(KnowHubChatChannelExecution::getExchangeId, exchangeId)
                    .orderByAsc(KnowHubChatChannelExecution::getSubQuestionIndex)
                    .orderByAsc(KnowHubChatChannelExecution::getStartTime)
            )
            .stream()
            .map(this::toExecutionVo)
            .toList();
    }

    /**
     * 删除指定会话的所有检索观察数据（检索结果 + 通道执行详情）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByConversation(String conversationId) {
        retrievalResultMapper.delete(
            new LambdaQueryWrapper<KnowHubChatRetrievalResult>()
                .eq(KnowHubChatRetrievalResult::getConversationId, conversationId)
        );
        channelExecutionMapper.delete(
            new LambdaQueryWrapper<KnowHubChatChannelExecution>()
                .eq(KnowHubChatChannelExecution::getConversationId, conversationId)
        );
    }

    /**
     * 将检索结果数据库实体转换为返回值对象。
     *
     * 注意：数据库中 gatePassed/isElevated/isSelected 使用 int（0/1）存储，
     * 转换时需要转为 boolean。
     */
    private RetrievalResultVo toResultVo(KnowHubChatRetrievalResult entity) {
        return new RetrievalResultVo(
            entity.getId(),
            StrUtil.blankToDefault(entity.getTraceId(), ""),
            entity.getSubQuestionIndex() == null ? 1 : entity.getSubQuestionIndex(),
            StrUtil.blankToDefault(entity.getSubQuestion(), ""),
            StrUtil.blankToDefault(entity.getChannelType(), ""),
            entity.getChannelRank(),
            entity.getRrfRank(),
            entity.getFinalRank(),
            entity.getOriginalScore(),
            entity.getRrfScore(),
            entity.getRerankScore(),
            entity.getGatePassed() != null && entity.getGatePassed() == 1,
            entity.getIsElevated() != null && entity.getIsElevated() == 1,
            entity.getIsSelected() != null && entity.getIsSelected() == 1,
            StrUtil.blankToDefault(entity.getSelectionReason(), ""),
            entity.getDocumentId(),
            StrUtil.blankToDefault(entity.getDocumentName(), ""),
            entity.getChunkId(),
            entity.getChunkNo(),
            entity.getParentBlockId(),
            entity.getParentBlockNo(),
            StrUtil.blankToDefault(entity.getSectionPath(), ""),
            StrUtil.blankToDefault(entity.getChunkTextPreview(), ""),
            entity.getChunkCharCount(),
            toInstant(entity.getCreateTime())
        );
    }

    /**
     * 将通道执行数据库实体转换为返回值对象。
     */
    private ChannelExecutionVo toExecutionVo(KnowHubChatChannelExecution entity) {
        return new ChannelExecutionVo(
            entity.getId(),
            StrUtil.blankToDefault(entity.getTraceId(), ""),
            entity.getSubQuestionIndex() == null ? 1 : entity.getSubQuestionIndex(),
            StrUtil.blankToDefault(entity.getSubQuestion(), ""),
            StrUtil.blankToDefault(entity.getChannelType(), ""),
            entity.getExecutionState() == null ? 1 : entity.getExecutionState(),
            toInstant(entity.getStartTime()),
            toInstant(entity.getEndTime()),
            entity.getDurationMs(),
            entity.getRecalledCount() == null ? 0 : entity.getRecalledCount(),
            entity.getAcceptedCount() == null ? 0 : entity.getAcceptedCount(),
            entity.getFinalSelectedCount() == null ? 0 : entity.getFinalSelectedCount(),
            entity.getAvgScore(),
            entity.getMaxScore(),
            entity.getMinScore(),
            StrUtil.blankToDefault(entity.getErrorMessage(), ""),
            toInstant(entity.getCreateTime())
        );
    }

    /** 将 Date 转为 Instant */
    private Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }
}
