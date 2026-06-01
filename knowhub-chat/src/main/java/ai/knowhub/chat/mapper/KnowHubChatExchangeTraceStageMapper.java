package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.KnowHubChatExchangeTraceStage;

/**
 * 聊天交换追踪阶段记录数据访问层接口。
 *
 * 该 Mapper 负责操作 knowhub_chat_exchange_trace_stage 表，
 * 用于记录每次对话交换过程中各个执行阶段（Stage）的追踪数据。
 *
 * 业务背景
 * 本系统的 AI Agent 执行流程被拆分为多个阶段，每个阶段都会产生追踪记录，
 * 典型的阶段包括：
 * 
 *   查询改写（Query Rewrite）- 将用户问题改写为更适合检索的形式
 *   文档检索（Document Retrieval）- 从向量数据库/ES 中检索相关文档
 *   文档重排序（Document Reranking）- 对检索结果进行相关性重排序
 *   答案生成（Answer Generation）- 基于检索结果生成最终回答
 *   工具调用（Tool Calling）- 调用外部工具（如 Tavily 搜索）
 * 
 * 这些追踪数据用于调试、性能分析和执行流程可视化。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口
 *   BaseMapper<T> - MyBatis-Plus 基础 Mapper，提供通用 CRUD 方法
 * 
 */
@Mapper
public interface KnowHubChatExchangeTraceStageMapper extends BaseMapper<KnowHubChatExchangeTraceStage> {
}
