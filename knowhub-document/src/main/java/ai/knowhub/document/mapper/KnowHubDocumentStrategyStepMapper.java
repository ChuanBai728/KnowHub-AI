package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocumentStrategyStep;

/**
 * 文档处理策略步骤（Strategy Step）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document_strategy_step表的数据库操作能力。
 * 策略步骤是处理策略方案中的单个执行步骤，多个步骤组成一条处理流水线（Pipeline）。
 *
 * 与 KnowHubDocumentStrategyPlanMapper 的关系：
 * 一个StrategyPlan（方案）包含多个StrategyStep（步骤），步骤定义了具体的处理操作，
 * 例如：步骤1=按段落切分、步骤2=生成摘要、步骤3=向量化等。
 *
 * 文档处理流水线的两级结构：
 * 
 *   父块流水线（parentSteps）：对大段文本的处理步骤
 *   子块流水线（childSteps）：对小段文本的处理步骤
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubDocumentStrategyStep
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubDocumentStrategyStepMapper extends BaseMapper<KnowHubDocumentStrategyStep> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
