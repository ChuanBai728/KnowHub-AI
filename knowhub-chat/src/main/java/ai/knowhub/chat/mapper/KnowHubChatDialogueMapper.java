package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.KnowHubChatDialogue;

/**
 * 聊天对话记录数据访问层接口。
 *
 * 该 Mapper 负责操作 knowhub_chat_dialogue 表，
 * 用于存储聊天对话中的每一条消息记录（Dialogue）。
 *
 * 业务背景
 * 在本系统中，"对话"（Dialogue）指的是聊天过程中产生的每一条独立消息，
 * 包括：
 * 
 *   用户消息（Human Message）- 用户输入的问题或指令
 *   AI 回复（Assistant Message）- AI Agent 生成的回答
 *   系统消息（System Message）- 系统级的提示或上下文信息
 *   工具调用消息（Tool Message）- AI 调用外部工具时的中间消息
 * 
 * 这些消息记录是会话历史的最小粒度单元，也是实现对话记忆（Memory）
 * 和滑动窗口策略的基础数据。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口
 *   BaseMapper<T> - MyBatis-Plus 基础 Mapper，提供通用 CRUD 方法
 * 
 */
@Mapper
public interface KnowHubChatDialogueMapper extends BaseMapper<KnowHubChatDialogue> {
}
