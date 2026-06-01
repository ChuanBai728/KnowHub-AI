package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubKnowledgeRouteTrace;

/**
 * 知识路由追踪（Route Trace）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_knowledge_route_trace表的数据库操作能力。
 * 知识路由追踪记录了RAG系统中每次用户查询被路由到哪个知识域/主题的过程详情。
 *
 * 知识路由（Knowledge Route）是RAG系统的核心组件之一：
 * 
 *   用户提出问题
 *   路由引擎分析问题，决定应从哪个知识域/主题中检索答案
 *   记录路由决策过程（本表存储的追踪数据）
 *   从目标知识域中检索相关文档
 *   生成最终回答
 * 
 * 路由追踪数据可用于分析路由策略的准确性，优化路由算法。
 *
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubKnowledgeRouteTrace
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubKnowledgeRouteTraceMapper extends BaseMapper<KnowHubKnowledgeRouteTrace> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
