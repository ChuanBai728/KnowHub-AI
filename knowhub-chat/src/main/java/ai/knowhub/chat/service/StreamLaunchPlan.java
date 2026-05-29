package ai.knowhub.chat.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import ai.knowhub.enums.ChatQueryMode;

import java.time.LocalDate;

/**
 * 【流式会话启动计划】
 *
 * 在用户发起一次聊天请求时，系统需要把用户的问题、会话 ID、查询模式、选中文档等信息
 * 统一整理成一个"启动计划"对象。后续的会话引导（bootstrap）、租约申请、执行计划编排
 * 都基于这个对象来获取上下文信息。
 *
 * 设计理念：把散落在 ChatRequestDto 中的原始参数，经过校验和转换后，
 * 封装成一个不可变（final 字段）的中间对象，避免后续逻辑重复做校验。
 *
 * 使用场景：BusinessChatService.buildLaunchPlan() 方法创建此对象，
 * 然后传递给 claimConversationLease()、bootstrapConversation() 等方法。
 *
 * 字段说明：
 * - question: 经过 trim 后的用户问题
 * - conversationId: 会话 ID，如果用户没传则自动生成 UUID
 * - chatMode: 查询模式（开放式提问、自动知识问答、当前文档问答）
 * - selectedDocumentId / selectedDocumentName: 当 chatMode 为"当前文档问答"时，选中的文档信息
 * - selectedTaskId: 选中文档的索引任务 ID
 * - leaseKey: Redis 租约的键，格式为 "chat:running:{conversationId}"
 * - leaseOwnerToken: 租约持有者令牌，用于后续续租和释放
 * - currentDate: 当前日期（上海时区），用于时间相关问题的上下文
 * - currentDateText: 当前日期的中文文本表示，例如 "2026-05-27（星期三）"
 */
@Data
@AllArgsConstructor
public class StreamLaunchPlan {

    /** 用户提问内容（已去除首尾空白） */
    private final String question;

    /** 会话唯一标识，由前端传入或后端自动生成 */
    private final String conversationId;

    /**
     * 查询模式枚举：
     * - OPEN_CHAT: 开放式提问，不限定文档范围
     * - AUTO_DOCUMENT: 自动知识问答，系统自动匹配文档
     * - DOCUMENT_CHAT: 当前文档问答，用户指定特定文档
     */
    private final ChatQueryMode chatMode;

    /** 选中的知识文档 ID（仅 DOCUMENT_CHAT 模式下有值） */
    private final Long selectedDocumentId;

    /** 选中的知识文档名称（仅 DOCUMENT_CHAT 模式下有值） */
    private final String selectedDocumentName;

    /** 选中文档的最近一次索引任务 ID，用于 RAG 检索时定位文档切片 */
    private final Long selectedTaskId;

    /**
     * Redis 租约的键名。
     * 格式：chat:running:{conversationId}
     * 作用：防止同一会话并发执行多个回答生成任务。
     */
    private final String leaseKey;

    /**
     * 租约持有者唯一令牌（UUID）。
     * 只有持有此令牌的服务实例才能续租和释放锁，
     * 防止其他实例误释放不属于自己的锁。
     */
    private final String leaseOwnerToken;

    /** 当前日期（Asia/Shanghai 时区），用于时间敏感问题的上下文 */
    private final LocalDate currentDate;

    /**
     * 当前日期的中文文本表示。
     * 例如："2026-05-27（星期三）"
     * 会注入到 Agent 的上下文中，帮助模型理解"今天"是哪天。
     */
    private final String currentDateText;
}
