package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.KnowHubChatChannelExecution;

/**
 * 聊天通道执行记录数据访问层接口。
 *
 * 该 Mapper 负责操作 knowhub_chat_channel_execution 表，
 * 用于记录每次对话交换中各检索通道（Channel）的执行情况。
 *
 * 业务背景
 * 本系统的 RAG 流程支持多通道检索（Multi-Channel Retrieval），例如：
 * 
 *   关键词检索通道 - 使用 Elasticsearch 进行全文关键词匹配
 *   向量检索通道 - 使用向量数据库进行语义相似度匹配
 *   图谱检索通道 - 使用 Neo4j 知识图谱进行结构化查询
 * 
 * 每个通道的执行耗时、检索数量、命中结果等信息都会记录在此表中，
 * 用于检索流程的性能监控和质量分析。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口
 *   BaseMapper<T> - MyBatis-Plus 基础 Mapper，提供通用 CRUD 方法
 * 
 */
@Mapper
public interface KnowHubChatChannelExecutionMapper extends BaseMapper<KnowHubChatChannelExecution> {
}
