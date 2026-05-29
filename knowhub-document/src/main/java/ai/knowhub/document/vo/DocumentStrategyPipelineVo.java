package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档策略流水线返回值对象（Document Strategy Pipeline Vo）
 *
 * 【类的作用】
 * 用于展示文档处理策略中的单条流水线信息，包括流水线类型、
 * 策略快照和具体的处理步骤列表。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，是 DocumentStrategyConfirmVo 和
 * DocumentStrategyPlanVo 的组成部分。
 *
 * 【关键概念】
 * 文档处理采用两级流水线架构：
 * - 父块流水线（parent）：将文档拆分为父级文本块
 * - 子块流水线（child）：将父块拆分为子级切片
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStrategyPipelineVo {

    /**
     * 流水线类型编码（Pipeline Type）
     * 标识流水线的类型（如 parent、child）
     */
    private String pipelineType;

    /**
     * 流水线类型名称（Pipeline Type Name）
     * 流水线类型的可读名称
     */
    private String pipelineTypeName;

    /**
     * 策略快照（Strategy Snapshot）
     * 策略配置的 JSON 序列化字符串
     */
    private String strategySnapshot;

    /**
     * 处理步骤列表（Steps）
     * 流水线中包含的所有处理步骤
     */
    private List<DocumentStrategyStepVo> steps;
}
