package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.KnowHubChatRetrievalResult;

/**
 * 聊天检索结果数据访问层接口。
 *
 * 该 Mapper 负责操作 knowhub_chat_retrieval_result 表，
 * 用于存储每次对话交换中 RAG 检索流程返回的文档片段结果。
 *
 * 业务背景
 * 在 RAG（检索增强生成）流程中，系统会从知识库中检索与用户问题相关的
 * 文档片段（Chunk），这些片段将作为上下文信息提供给 LLM 生成回答。
 * 该表记录了每次检索的详细结果，包括：
 * 
 *   文档片段内容
 *   来源文档信息
 *   相关性分数
 *   检索通道来源（关键词/向量/图谱）
 * 
 * 这些数据用于检索质量监控、回答溯源和"检索观察"功能的展示。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口
 *   BaseMapper<T> - MyBatis-Plus 基础 Mapper，提供通用 CRUD 方法
 * 
 */
@Mapper
public interface KnowHubChatRetrievalResultMapper extends BaseMapper<KnowHubChatRetrievalResult> {
}
