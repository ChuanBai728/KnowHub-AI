package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.KnowHubChatExchange;

/**
 * 聊天交换记录数据访问层接口。
 *
 * 该 Mapper 负责操作 knowhub_chat_exchange 表，
 * 用于存储每次"对话交换"（Exchange）的元数据信息。
 *
 * 业务背景
 * 一次"交换"（Exchange）代表用户提问与 AI 回答的完整配对过程。
 * 与 Dialogue（单条消息）不同，Exchange 是更高层次的抽象，
 * 它记录了：
 * 
 *   用户的问题摘要
 *   AI 的回答摘要
 *   执行耗时
 *   使用的工具列表
 *   检索来源数量
 *   整体状态（进行中/已完成/失败）
 * 
 * Exchange 是会话管理的核心数据单元，前端的对话历史列表展示的就是 Exchange 记录。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口
 *   BaseMapper<T> - MyBatis-Plus 基础 Mapper，提供通用 CRUD 方法
 * 
 */
@Mapper
public interface KnowHubChatExchangeMapper extends BaseMapper<KnowHubChatExchange> {
}
