package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocumentStrategyPlan;

/**
 * 文档处理策略方案（Strategy Plan）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document_strategy_plan表的数据库操作能力。
 * 处理策略方案是系统为每个文档生成的处理规划，包含文档应如何切分、
 * 使用什么处理流水线（Pipeline）等信息。
 *
 * RAG文档处理流程：
 * 
 *   文档上传 -> 系统自动分析文档特征
 *   生成处理策略方案（StrategyPlan）-> 推荐最佳的切分和处理方式
 *   用户确认或调整方案
 *   按照确认的方案执行索引构建
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubDocumentStrategyPlan
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubDocumentStrategyPlanMapper extends BaseMapper<KnowHubDocumentStrategyPlan> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
