package ai.knowhub.chat.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import ai.knowhub.chat.data.KnowHubChatDialogue;
import ai.knowhub.chat.data.KnowHubChatExchange;
import ai.knowhub.chat.mapper.KnowHubChatDialogueMapper;
import ai.knowhub.chat.mapper.KnowHubChatExchangeMapper;
import ai.knowhub.chat.model.ConversationExchangeVo;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.model.debug.ChatDebugTrace;
import ai.knowhub.enums.BusinessStatus;
import ai.knowhub.enums.ChatQueryMode;
import ai.knowhub.enums.ChatSessionStatus;
import ai.knowhub.enums.ChatTurnStatus;
import ai.knowhub.util.DateUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/**
 * 【会话归档存储 - MyBatis 实现】
 *
 * ConversationArchiveStore 接口的 MyBatis-Plus 实现类，
 * 将会话和问答回合数据持久化到 MySQL 数据库。
 *
 * 涉及两张核心表：
 * 1. knowhub_chat_dialogue: 会话表
 *    - 存储会话元数据：conversationId、sessionStatus、chatMode、selectedDocumentId 等
 *    - 一个 conversationId 可能有多条记录（历史版本），查询时取最新一条
 * 2. knowhub_chat_exchange: 问答回合表
 *    - 存储每轮问答的完整数据：问题、回答、思考步骤、引用、推荐等
 *    - 通过 conversationId 关联到会话
 *    - thinkingSteps、referenceList、recommendationList 等字段以 JSON 字符串存储
 *
 * JSON 序列化策略：
 * - thinkingSteps: List<String> -> JSON 字符串
 * - referenceList: List<SearchReference> -> JSON 字符串
 * - recommendationList: List<String> -> JSON 字符串
 * - usedToolList: List<String> -> JSON 字符串
 * - debugTraceJson: ChatDebugTrace -> JSON 字符串（可为 null）
 *
 * 设计模式：仓储模式（Repository Pattern）。
 * 使用 MyBatis-Plus 的 LambdaQueryWrapper 构建类型安全的查询条件。
 *
 * 事务管理：
 * - 写操作使用 @Transactional(rollbackFor = Exception.class)
 * - 读操作使用 @Transactional(readOnly = true) 优化性能
 */
@Repository
public class MybatisConversationArchiveStore implements ConversationArchiveStore {

    // ==================== Jackson TypeReference 常量 ====================
    // 用于反序列化 JSON 字符串为具体的 Java 类型

    /** List<String> 类型引用，用于反序列化 thinkingSteps、recommendations 等 */
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    /** List<SearchReference> 类型引用，用于反序列化引用来源列表 */
    private static final TypeReference<List<SearchReference>> REFERENCE_LIST_TYPE = new TypeReference<>() {
    };

    /** ChatDebugTrace 类型引用，用于反序列化调试追踪信息 */
    private static final TypeReference<ChatDebugTrace> DEBUG_TRACE_TYPE = new TypeReference<>() {
    };

    // ==================== 依赖注入 ====================

    /** 会话表 Mapper */
    private final KnowHubChatDialogueMapper dialogueMapper;

    /** 问答回合表 Mapper */
    private final KnowHubChatExchangeMapper exchangeMapper;

    /** Jackson ObjectMapper，用于 JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    /** 百度 UID 生成器，用于生成唯一主键 */
    @Resource
    private UidGenerator uidGenerator;

    /**
     * 构造函数。
     */
    public MybatisConversationArchiveStore(KnowHubChatDialogueMapper dialogueMapper,
                                           KnowHubChatExchangeMapper exchangeMapper,
                                           ObjectMapper objectMapper) {
        this.dialogueMapper = dialogueMapper;
        this.exchangeMapper = exchangeMapper;
        this.objectMapper = objectMapper;
    }

    // ==================== 核心业务方法 ====================

    /**
     * 开始一个新的问答回合。
     *
     * 执行逻辑：
     * 1. 调用 upsertDialogue() 确保会话记录存在（不存在则创建，已存在则更新状态）
     * 2. 生成唯一 exchangeId（百度 UID）
     * 3. 创建 exchange 记录，状态为 RUNNING，所有 JSON 字段初始化为空列表
     * 4. 插入数据库并返回返回值对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationExchangeVo startExchange(String conversationId,
                                                  String question,
                                                  ChatQueryMode chatMode,
                                                  Long selectedDocumentId,
                                                  String selectedDocumentName) {
        upsertDialogue(conversationId, ChatSessionStatus.RUNNING, chatMode, selectedDocumentId, selectedDocumentName);

        long exchangeId = uidGenerator.getUid();

        KnowHubChatExchange exchange = new KnowHubChatExchange();

        exchange.setId(exchangeId);
        exchange.setConversationId(conversationId);
        exchange.setQuestion(question);
        exchange.setAnswer("");
        exchange.setThinkingSteps(writeJson(List.of()));
        exchange.setReferenceList(writeJson(List.of()));
        exchange.setRecommendationList(writeJson(List.of()));
        exchange.setUsedToolList(writeJson(List.of()));
        exchange.setDebugTraceJson(null);
        exchange.setTurnStatus(ChatTurnStatus.RUNNING.getCode());
        exchange.setErrorMessage("");
        exchange.setFirstResponseTimeMs(null);
        exchange.setTotalResponseTimeMs(null);
        exchange.setStatus(BusinessStatus.YES.getCode());
        exchangeMapper.insert(exchange);

        return new ConversationExchangeVo(
            exchangeId,
            question,
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ChatTurnStatus.RUNNING,
            "",
            null,
            null,
             DateUtils.now(),
             DateUtils.now()
        );
    }

    /**
     * 刷新会话的作用域信息。
     *
     * 当执行计划编排器发现需要切换查询模式或文档时调用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshSessionScope(String conversationId,
                                    ChatQueryMode chatMode,
                                    Long selectedDocumentId,
                                    String selectedDocumentName) {
        upsertDialogue(conversationId, ChatSessionStatus.RUNNING, chatMode, selectedDocumentId, selectedDocumentName);
    }

    /**
     * 完成一个问答回合。
     *
     * 执行逻辑：
     * 1. 根据 exchangeId 和 conversationId 查询现有记录
     * 2. 更新回答、思考步骤、引用、推荐、工具列表、调试追踪等字段
     * 3. 更新回合状态（COMPLETED/FAILED/STOPPED）
     * 4. 将会话状态从 RUNNING 改为 IDLE
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeExchange(String conversationId,
                                 long exchangeId,
                                 String answer,
                                 List<String> thinkingSteps,
                                 List<SearchReference> references,
                                 List<String> recommendations,
                                 List<String> usedTools,
                                 ChatDebugTrace debugTrace,
                                 ChatTurnStatus status,
                                 String errorMessage,
                                 Long firstResponseTimeMs,
                                 Long totalResponseTimeMs) {

        // 查询现有回合记录
        KnowHubChatExchange existingExchange = exchangeMapper.selectOne(
            new LambdaQueryWrapper<KnowHubChatExchange>()
                .eq(KnowHubChatExchange::getId, exchangeId)
                .eq(KnowHubChatExchange::getConversationId, conversationId)
                .last("LIMIT 1")
        );
        if (existingExchange == null) {

            return;
        }

        // 更新回合数据
        KnowHubChatExchange updateExchange = new KnowHubChatExchange();
        updateExchange.setId(exchangeId);
        updateExchange.setAnswer(safeText(answer));
        updateExchange.setThinkingSteps(writeJson(thinkingSteps));
        updateExchange.setReferenceList(writeJson(references));
        updateExchange.setRecommendationList(writeJson(recommendations));
        updateExchange.setUsedToolList(writeJson(usedTools));
        updateExchange.setDebugTraceJson(writeNullableJson(debugTrace));
        updateExchange.setTurnStatus(status.getCode());
        updateExchange.setErrorMessage(safeText(errorMessage));
        updateExchange.setFirstResponseTimeMs(firstResponseTimeMs);
        updateExchange.setTotalResponseTimeMs(totalResponseTimeMs);
        exchangeMapper.updateById(updateExchange);

        // 将会话状态从 RUNNING 改为 IDLE
        dialogueMapper.update(
            null,
            new LambdaUpdateWrapper<KnowHubChatDialogue>()
                .eq(KnowHubChatDialogue::getConversationId, conversationId)

                .set(KnowHubChatDialogue::getSessionStatus, ChatSessionStatus.IDLE.getCode())
        );
    }

    /**
     * 获取指定会话的归档记录。
     *
     * 查询最新的 dialogue 记录和所有 exchange 记录，组装为 ConversationArchiveRecord。
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ConversationArchiveRecord> getSessionRecord(String conversationId) {

        KnowHubChatDialogue dialogue = dialogueMapper.selectOne(
            activeDialogueByConversation(conversationId)
                .orderByDesc(KnowHubChatDialogue::getId)
                .last("LIMIT 1")
        );
        if (dialogue == null) {
            return Optional.empty();
        }

        List<ConversationExchangeVo> exchanges = loadExchangeViews(List.of(conversationId))
            .getOrDefault(conversationId, List.of());

        return Optional.of(new ConversationArchiveRecord(
            dialogue.getConversationId(),
            ChatSessionStatus.isRunning(dialogue.getSessionStatus()),
            resolveChatMode(dialogue),
            dialogue.getSelectedDocumentId(),
            safeText(dialogue.getSelectedDocumentName()),
            toInstant(dialogue.getCreateTime()),
            toInstant(dialogue.getEditTime()),
            exchanges
        ));
    }

    /**
     * 列出指定会话的所有问答回合（时间升序）。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ConversationExchangeVo> listExchanges(String conversationId) {

        return selectConversationExchanges(
            new LambdaQueryWrapper<KnowHubChatExchange>()
                .eq(KnowHubChatExchange::getConversationId, conversationId)
                .orderByAsc(KnowHubChatExchange::getCreateTime)
                .orderByAsc(KnowHubChatExchange::getId)
        );
    }

    /**
     * 列出指定回合 ID 之后的所有回合（增量查询）。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ConversationExchangeVo> listExchangesAfter(String conversationId, long afterExchangeId) {

        return selectConversationExchanges(
            new LambdaQueryWrapper<KnowHubChatExchange>()
                .eq(KnowHubChatExchange::getConversationId, conversationId)
                .gt(afterExchangeId > 0, KnowHubChatExchange::getId, afterExchangeId)
                .orderByAsc(KnowHubChatExchange::getCreateTime)
                .orderByAsc(KnowHubChatExchange::getId)
        );
    }

    /**
     * 列出最近的 N 个问答回合。
     *
     * 先按时间降序取最近 N 条，再反转为升序返回。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ConversationExchangeVo> listRecentExchanges(String conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<KnowHubChatExchange> exchanges = exchangeMapper.selectList(
            new LambdaQueryWrapper<KnowHubChatExchange>()
                .eq(KnowHubChatExchange::getConversationId, conversationId)
                .orderByDesc(KnowHubChatExchange::getCreateTime)
                .orderByDesc(KnowHubChatExchange::getId)
                .last("LIMIT " + limit)
        );
        if (exchanges == null || exchanges.isEmpty()) {
            return List.of();
        }
        // 反转为时间升序
        List<ConversationExchangeVo> views = new ArrayList<>(exchanges.size());
        for (int index = exchanges.size() - 1; index >= 0; index--) {
            views.add(toExchangeVo(exchanges.get(index)));
        }
        return views;
    }

    /**
     * 列出所有会话的归档记录。
     *
     * 逻辑：
     * 1. 查询所有 dialogue 记录（按更新时间降序）
     * 2. 按 conversationId 去重（保留最新的一条）
     * 3. 批量加载所有 exchange 记录
     * 4. 组装为 ConversationArchiveRecord 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<ConversationArchiveRecord> listSessionRecords() {
        List<KnowHubChatDialogue> rawDialogues = dialogueMapper.selectList(
            new LambdaQueryWrapper<KnowHubChatDialogue>()
                .orderByDesc(KnowHubChatDialogue::getEditTime)
                .orderByDesc(KnowHubChatDialogue::getId)
        );
        if (rawDialogues == null || rawDialogues.isEmpty()) {
            return List.of();
        }

        // 按 conversationId 去重，保留最新的一条
        Map<String, KnowHubChatDialogue> latestDialogues = new LinkedHashMap<>();
        for (KnowHubChatDialogue dialogue : rawDialogues) {

            latestDialogues.putIfAbsent(dialogue.getConversationId(), dialogue);
        }
        List<KnowHubChatDialogue> dialogues = new ArrayList<>(latestDialogues.values());

        List<String> conversationIds = dialogues.stream()
            .map(KnowHubChatDialogue::getConversationId)
            .toList();

        // 批量加载所有 exchange 记录
        Map<String, List<ConversationExchangeVo>> exchangeVoMap = loadExchangeViews(conversationIds);

        List<ConversationArchiveRecord> result = new ArrayList<>(dialogues.size());
        for (KnowHubChatDialogue dialogue : dialogues) {
            result.add(new ConversationArchiveRecord(
                dialogue.getConversationId(),
                ChatSessionStatus.isRunning(dialogue.getSessionStatus()),
                resolveChatMode(dialogue),
                dialogue.getSelectedDocumentId(),
                safeText(dialogue.getSelectedDocumentName()),
                toInstant(dialogue.getCreateTime()),
                toInstant(dialogue.getEditTime()),
                exchangeVoMap.getOrDefault(dialogue.getConversationId(), List.of())
            ));
        }
        return result;
    }

    /**
     * 分页查询会话归档记录。
     *
     * 支持按关键词、查询模式、最近回合状态过滤。
     * 使用 MyBatis-Plus 的 IPage 分页机制。
     */
    @Override
    @Transactional(readOnly = true)
    public ConversationArchivePage listSessionRecordPage(int pageNo,
                                                         int pageSize,
                                                         String keyword,
                                                         ChatQueryMode chatMode,
                                                         ChatTurnStatus latestTurnStatus) {
        int resolvedPageNo = Math.max(pageNo, 1);
        int resolvedPageSize = Math.max(pageSize, 1);

        LambdaQueryWrapper<KnowHubChatDialogue> wrapper = new LambdaQueryWrapper<KnowHubChatDialogue>()
            .orderByDesc(KnowHubChatDialogue::getEditTime)
            .orderByDesc(KnowHubChatDialogue::getId);
        applySessionPageFilters(wrapper, keyword, chatMode, latestTurnStatus);

        Page<KnowHubChatDialogue> page = new Page<>(resolvedPageNo, resolvedPageSize);
        IPage<KnowHubChatDialogue> resultPage = dialogueMapper.selectPage(
            page,
            wrapper
        );

        // 加载每个会话的最新回合（用于列表展示）
        List<String> conversationIds = resultPage.getRecords().stream()
            .map(KnowHubChatDialogue::getConversationId)
            .toList();
        Map<String, ConversationExchangeVo> latestExchangeMap = loadLatestExchangeMap(conversationIds);

        List<ConversationArchiveRecord> records = resultPage.getRecords().stream()
            .map(dialogue -> new ConversationArchiveRecord(
                dialogue.getConversationId(),
                ChatSessionStatus.isRunning(dialogue.getSessionStatus()),
                resolveChatMode(dialogue),
                dialogue.getSelectedDocumentId(),
                safeText(dialogue.getSelectedDocumentName()),
                toInstant(dialogue.getCreateTime()),
                toInstant(dialogue.getEditTime()),
                latestExchangeMap.containsKey(dialogue.getConversationId())
                    ? List.of(latestExchangeMap.get(dialogue.getConversationId()))
                    : List.of()
            ))
            .toList();

        return new ConversationArchivePage(
            resultPage.getCurrent(),
            resultPage.getSize(),
            resultPage.getTotal(),
            records
        );
    }

    /**
     * 删除指定会话的所有数据。
     *
     * 先统计数量，再执行删除。
     */
    @Override
    @Transactional
    public ConversationArchiveStore.ConversationRemovalResult deleteSession(String conversationId) {
        LambdaQueryWrapper<KnowHubChatExchange> exchangeQuery = exchangesByConversation(conversationId);
        LambdaQueryWrapper<KnowHubChatDialogue> dialogueQuery = activeDialogueByConversation(conversationId);

        int removedExchangeCount = toInt(exchangeMapper.selectCount(exchangeQuery));
        int removedDialogueCount = toInt(dialogueMapper.selectCount(dialogueQuery));

        if (removedExchangeCount > 0) {

            exchangeMapper.delete(exchangesByConversation(conversationId));
        }
        if (removedDialogueCount > 0) {
            dialogueMapper.delete(activeDialogueByConversation(conversationId));
        }

        return new ConversationArchiveStore.ConversationRemovalResult(removedDialogueCount, removedExchangeCount);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 插入或更新会话记录（Upsert 逻辑）。
     *
     * 如果会话不存在则创建新记录；如果已存在但元数据有变化则更新。
     */
    private void upsertDialogue(String conversationId,
                                ChatSessionStatus dialogueStage,
                                ChatQueryMode chatMode,
                                Long selectedDocumentId,
                                String selectedDocumentName) {
        Objects.requireNonNull(chatMode, "chatMode 不能为空");
        KnowHubChatDialogue dialogue = dialogueMapper.selectOne(
            activeDialogueByConversation(conversationId)
                .orderByDesc(KnowHubChatDialogue::getId)
                .last("LIMIT 1")
        );

        // 会话不存在，创建新记录
        if (dialogue == null) {
            KnowHubChatDialogue newDialogue = new KnowHubChatDialogue();
            newDialogue.setId(uidGenerator.getUid());
            newDialogue.setConversationId(conversationId);
            newDialogue.setSessionStatus(dialogueStage.getCode());
            newDialogue.setChatMode(chatMode.getCode());
            newDialogue.setSelectedDocumentId(selectedDocumentId);
            newDialogue.setSelectedDocumentName(selectedDocumentName);
            newDialogue.setStatus(BusinessStatus.YES.getCode());

            dialogueMapper.insert(newDialogue);
            return;
        }

        // 会话已存在，检查是否有变化
        boolean stageChanged = !dialogueStage.equals(ChatSessionStatus.fromCode(dialogue.getSessionStatus()));
        boolean chatModeChanged = !Objects.equals(chatMode.getCode(), dialogue.getChatMode());
        boolean documentScopeChanged = !Objects.equals(selectedDocumentId, dialogue.getSelectedDocumentId())
            || !Objects.equals(safeText(selectedDocumentName), safeText(dialogue.getSelectedDocumentName()));

        // 有变化才更新
        if (stageChanged || chatModeChanged || documentScopeChanged) {
            KnowHubChatDialogue updateDialogue = new KnowHubChatDialogue();
            updateDialogue.setId(dialogue.getId());
            updateDialogue.setSessionStatus(dialogueStage.getCode());
            updateDialogue.setChatMode(chatMode.getCode());
            updateDialogue.setSelectedDocumentId(selectedDocumentId);
            updateDialogue.setSelectedDocumentName(selectedDocumentName);
            dialogueMapper.updateById(updateDialogue);
        }
    }

    /**
     * 解析会话的查询模式。
     */
    private ChatQueryMode resolveChatMode(KnowHubChatDialogue dialogue) {
        if (dialogue == null || dialogue.getChatMode() == null) {
            throw new IllegalStateException("会话记录缺少 chatMode，当前教学版项目要求数据库使用最新结构");
        }
        return ChatQueryMode.fromCode(dialogue.getChatMode());
    }

    /**
     * 批量加载多个会话的所有回合记录。
     *
     * @param conversationIds 会话 ID 列表
     * @return Key=conversationId, Value=该会话的所有回合列表
     */
    private Map<String, List<ConversationExchangeVo>> loadExchangeViews(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }

        List<KnowHubChatExchange> exchanges = exchangeMapper.selectList(
            new LambdaQueryWrapper<KnowHubChatExchange>()
                .in(KnowHubChatExchange::getConversationId, conversationIds)

                .orderByAsc(KnowHubChatExchange::getCreateTime)
                .orderByAsc(KnowHubChatExchange::getConversationId)
                .orderByAsc(KnowHubChatExchange::getId)
        );

        Map<String, List<ConversationExchangeVo>> exchangeVosByConversation = new LinkedHashMap<>();
        for (KnowHubChatExchange exchange : exchanges) {

            exchangeVosByConversation.computeIfAbsent(exchange.getConversationId(), key -> new ArrayList<>())
                .add(toExchangeVo(exchange));
        }
        return exchangeVosByConversation;
    }

    /**
     * 应用分页查询的过滤条件。
     *
     * @param wrapper         查询条件包装器
     * @param keyword         搜索关键词（模糊匹配会话 ID、文档名、问答内容）
     * @param chatMode        查询模式过滤
     * @param latestTurnStatus 最近回合状态过滤
     */
    private void applySessionPageFilters(LambdaQueryWrapper<KnowHubChatDialogue> wrapper,
                                         String keyword,
                                         ChatQueryMode chatMode,
                                         ChatTurnStatus latestTurnStatus) {
        if (wrapper == null) {
            return;
        }
        if (chatMode != null) {
            wrapper.eq(KnowHubChatDialogue::getChatMode, chatMode.getCode());
        }
        if (StrUtil.isNotBlank(keyword)) {
            String likeKeyword = "%" + keyword.trim() + "%";
            wrapper.and(query -> query
                .like(KnowHubChatDialogue::getConversationId, keyword.trim())
                .or()
                .like(KnowHubChatDialogue::getSelectedDocumentName, keyword.trim())
                .or()
                .apply(
                    "EXISTS (SELECT 1 FROM knowhub_chat_exchange e WHERE e.dialogue_code = knowhub_chat_dialogue.dialogue_code"
                        + " AND (e.user_prompt LIKE {0} OR e.reply_content LIKE {0} OR e.finish_note LIKE {0}))",
                    likeKeyword
                )
            );
        }
        if (latestTurnStatus == null) {
            return;
        }
        if (latestTurnStatus == ChatTurnStatus.RUNNING) {
            wrapper.eq(KnowHubChatDialogue::getSessionStatus, ChatSessionStatus.RUNNING.getCode());
            return;
        }
        wrapper.eq(KnowHubChatDialogue::getSessionStatus, ChatSessionStatus.IDLE.getCode());
        wrapper.apply(
            "EXISTS (SELECT 1 FROM knowhub_chat_exchange e"
                + " WHERE e.dialogue_code = knowhub_chat_dialogue.dialogue_code"
                + " AND e.id = (SELECT latest.id FROM knowhub_chat_exchange latest"
                + " WHERE latest.dialogue_code = knowhub_chat_dialogue.dialogue_code"
                + " ORDER BY latest.create_time DESC, latest.id DESC LIMIT 1)"
                + " AND e.exchange_state = {0})",
            latestTurnStatus.getCode()
        );
    }

    /**
     * 加载每个会话的最新回合（用于列表页展示）。
     */
    private Map<String, ConversationExchangeVo> loadLatestExchangeMap(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }
        List<KnowHubChatExchange> exchanges = exchangeMapper.selectList(
            new LambdaQueryWrapper<KnowHubChatExchange>()
                .in(KnowHubChatExchange::getConversationId, conversationIds)
                .orderByDesc(KnowHubChatExchange::getCreateTime)
                .orderByDesc(KnowHubChatExchange::getId)
        );
        if (exchanges == null || exchanges.isEmpty()) {
            return Map.of();
        }

        Map<String, ConversationExchangeVo> latestExchangeMap = new LinkedHashMap<>();
        for (KnowHubChatExchange exchange : exchanges) {
            if (exchange == null || latestExchangeMap.containsKey(exchange.getConversationId())) {
                continue;
            }
            latestExchangeMap.put(exchange.getConversationId(), toExchangeVo(exchange));
        }
        return latestExchangeMap;
    }

    /**
     * 执行查询并转换为展示列表。
     */
    private List<ConversationExchangeVo> selectConversationExchanges(LambdaQueryWrapper<KnowHubChatExchange> queryWrapper) {
        List<KnowHubChatExchange> exchanges = exchangeMapper.selectList(queryWrapper);
        if (exchanges == null || exchanges.isEmpty()) {
            return List.of();
        }
        List<ConversationExchangeVo> result = new ArrayList<>(exchanges.size());
        for (KnowHubChatExchange exchange : exchanges) {
            result.add(toExchangeVo(exchange));
        }
        return result;
    }

    /**
     * 将数据库实体转换为返回值对象。
     *
     * JSON 字段的反序列化在此方法中完成。
     */
    private ConversationExchangeVo toExchangeVo(KnowHubChatExchange exchange) {

        return new ConversationExchangeVo(
            exchange.getId(),
            safeText(exchange.getQuestion()),
            safeText(exchange.getAnswer()),
            readStringList(exchange.getThinkingSteps()),
            readReferenceList(exchange.getReferenceList()),
            readStringList(exchange.getRecommendationList()),
            readStringList(exchange.getUsedToolList()),
            readDebugTrace(exchange.getDebugTraceJson()),
            ChatTurnStatus.fromCode(exchange.getTurnStatus()),
            safeText(exchange.getErrorMessage()),
            exchange.getFirstResponseTimeMs(),
            exchange.getTotalResponseTimeMs(),
            exchange.getCreateTime(),
            exchange.getEditTime()
        );
    }

    // ==================== JSON 序列化/反序列化辅助方法 ====================

    /** 将 JSON 字符串反序列化为 List<String> */
    private List<String> readStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {

            return objectMapper.readValue(json, STRING_LIST_TYPE);
        }
        catch (Exception exception) {
            throw new IllegalStateException("解析字符串列表失败", exception);
        }
    }

    /** 将 JSON 字符串反序列化为 List<SearchReference> */
    private List<SearchReference> readReferenceList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {

            return objectMapper.readValue(json, REFERENCE_LIST_TYPE);
        }
        catch (Exception exception) {
            throw new IllegalStateException("解析引用来源列表失败", exception);
        }
    }

    /** 将 JSON 字符串反序列化为 ChatDebugTrace */
    private ChatDebugTrace readDebugTrace(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {

            return objectMapper.readValue(json, DEBUG_TRACE_TYPE);
        }
        catch (Exception exception) {
            throw new IllegalStateException("解析调试轨迹失败", exception);
        }
    }

    /** 将对象序列化为 JSON 字符串（null 时序列化为空列表） */
    private String writeJson(Object value) {
        try {

            return objectMapper.writeValueAsString(value != null ? value : List.of());
        }
        catch (Exception exception) {
            throw new IllegalStateException("序列化会话字段失败", exception);
        }
    }

    /** 将对象序列化为 JSON 字符串（null 时返回 null） */
    private String writeNullableJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception exception) {
            throw new IllegalStateException("序列化可空会话字段失败", exception);
        }
    }

    // ==================== 通用辅助方法 ====================

    /** 将 Date 转为 Instant */
    private Instant toInstant(Date date) {

        return date != null ? date.toInstant() : null;
    }

    /** 安全获取文本，null 时返回空字符串 */
    private String safeText(String text) {

        return text != null ? text : "";
    }

    /** 构建按 conversationId 查询会话的条件 */
    private LambdaQueryWrapper<KnowHubChatDialogue> activeDialogueByConversation(String conversationId) {

        return new LambdaQueryWrapper<KnowHubChatDialogue>()
            .eq(KnowHubChatDialogue::getConversationId, conversationId);
    }

    /** 构建按 conversationId 查询回合的条件 */
    private LambdaQueryWrapper<KnowHubChatExchange> exchangesByConversation(String conversationId) {

        return new LambdaQueryWrapper<KnowHubChatExchange>()
            .eq(KnowHubChatExchange::getConversationId, conversationId);
    }

    /** 安全地将 Long 转为 int，null 时返回 0 */
    private int toInt(Long count) {

        return count == null ? 0 : count.intValue();
    }
}
