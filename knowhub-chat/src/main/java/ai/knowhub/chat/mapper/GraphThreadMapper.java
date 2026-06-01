package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.GraphThread;

/**
 * 图线程（Graph Thread）数据访问层接口。
 *
 * 该 Mapper 负责操作 graph_thread 表，用于管理 Spring AI Alibaba
 * 图执行框架中的线程信息。在本系统中，"线程"（Thread）代表一次 Agent 图执行的
 * 上下文环境，每个会话（Conversation）可能对应一个或多个线程。
 *
 * 在架构中的角色
 * 属于数据访问层（DAO/Mapper），与 GraphCheckpointMapper 配合使用，
 * 共同支撑图执行框架的状态持久化。Thread 管理执行上下文，
 * Checkpoint 管理执行过程中的快照数据。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口，
 *       Spring 容器会自动扫描并创建代理实现类
 *   BaseMapper<T> - MyBatis-Plus 的基础 Mapper 泛型接口，
 *       提供标准的 CRUD 操作方法
 * 
 */
@Mapper
public interface GraphThreadMapper extends BaseMapper<GraphThread> {
}
