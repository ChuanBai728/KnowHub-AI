package ai.knowhub.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.chat.data.GraphCheckpoint;

/**
 * 图检查点（Graph Checkpoint）数据访问层接口。
 *
 * 该 Mapper 负责操作 graph_checkpoint 表，用于持久化 Spring AI Alibaba
 * 图执行框架的检查点数据。在 ReAct Agent 的执行过程中，图的每个节点执行状态
 * 都会被保存为检查点，以便在需要时进行回滚、重放或状态恢复。
 *
 * 在架构中的角色
 * 属于数据访问层（DAO/Mapper），是 MyBatis-Plus 框架与数据库之间的桥梁。
 * 通过继承 BaseMapper<GraphCheckpoint>，自动获得了 CRUD（增删改查）
 * 等基础数据库操作能力，无需手动编写 SQL 语句。
 *
 * MyBatis-Plus 注解说明
 * 
 *   @Mapper - 标记该接口为 MyBatis 的 Mapper 接口，
 *       Spring 会自动扫描并创建代理实现类，注册到 IoC 容器中
 *   BaseMapper<T> - MyBatis-Plus 提供的基础 Mapper 接口，
 *       泛型 T 对应数据库表的实体类，提供了 insert/delete/update/select 等通用方法
 * 设计模式
 * 采用"模板方法"模式，BaseMapper 定义了通用的数据库操作模板，
 * 子接口只需继承即可获得完整的基础 CRUD 能力，如有特殊查询需求可在此扩展自定义方法。
 */
@Mapper
public interface GraphCheckpointMapper extends BaseMapper<GraphCheckpoint> {
}
