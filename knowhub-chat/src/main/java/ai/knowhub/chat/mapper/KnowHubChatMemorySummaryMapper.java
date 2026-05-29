package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.KnowHubChatMemorySummary;

/**
 * 聊天记忆摘要数据访问层接口。
 *
 * 该 Mapper 负责操作 knowhub_chat_memory_summary 表，
 * 用于存储对话历史的压缩摘要（Memory Summary）。
 *
 * 业务背景
 * 随着对话轮次增多，完整的对话历史会变得很长，直接发送给 LLM 会导致：
 * 
 *   Token 数量超过模型上下文窗口限制
 *   推理成本（费用）大幅增加
 *   模型注意力分散，回答质量下降
 * 
 * 因此，本系统实现了"摘要压缩"记忆策略：将早期的对话历史压缩为摘要，
 * 仅保留最近几轮的完整对话，从而在保留上下文信息的同时控制 Token 消耗。
 * 该表存储的就是这些压缩后的摘要数据。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口
 *   BaseMapper<T> - MyBatis-Plus 基础 Mapper，提供通用 CRUD 方法
 * 
 */
@Mapper
public interface KnowHubChatMemorySummaryMapper extends BaseMapper<KnowHubChatMemorySummary> {
}
