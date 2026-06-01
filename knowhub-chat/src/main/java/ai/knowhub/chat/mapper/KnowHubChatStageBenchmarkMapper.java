package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.KnowHubChatStageBenchmark;

/**
 * 聊天阶段基准测试数据访问层接口。
 *
 * 该 Mapper 负责操作 knowhub_chat_stage_benchmark 表，
 * 用于记录各执行阶段的性能基准数据（Benchmark）。
 *
 * 业务背景
 * 每个执行阶段（Stage）在完成时会记录其开始时间、结束时间和耗时，
 * 这些数据构成了一次对话交换的完整性能画像。通过分析这些基准数据，可以：
 * 
 *   识别性能瓶颈（如检索耗时过长、LLM 推理过慢）
 *   对比不同聊天模式的执行效率
 *   监控系统整体性能趋势
 *   为优化 RAG 流程提供数据支撑
 * 
 * 与 ExchangeTraceStage 侧重于追踪执行过程不同，
 * 该表侧重于性能指标的量化记录。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口
 *   BaseMapper<T> - MyBatis-Plus 基础 Mapper，提供通用 CRUD 方法
 * 
 */
@Mapper
public interface KnowHubChatStageBenchmarkMapper extends BaseMapper<KnowHubChatStageBenchmark> {
}
