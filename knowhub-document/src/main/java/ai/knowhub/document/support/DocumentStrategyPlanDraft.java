package ai.knowhub.document.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档策略计划草案（Document Strategy Plan Draft）
 *
 * 【类的作用】
 * 表示文档处理策略的草案。在文档被解析后，系统会分析文档特征，
 * 自动生成一份处理策略计划，包含推荐理由和具体的处理步骤。
 *
 * 【在架构中的角色】
 * 属于文档处理流水线（Pipeline）的策略规划阶段。系统会根据文档的
 * 结构特征、内容类型等因素，生成不同的处理策略（如父块提取策略、
 * 切片策略等），最终由用户确认后执行。
 *
 * 【关键概念】
 * - 策略快照（strategySnapshot）：策略的 JSON 序列化表示
 * - 父块步骤（parentSteps）：父级文本块的提取步骤
 * - 子块步骤（childSteps）：子级切片的处理步骤
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStrategyPlanDraft {

    /**
     * 策略快照（Strategy Snapshot）
     * 策略配置的 JSON 序列化字符串，保存了完整的策略参数
     * 用于策略的持久化和版本管理
     */
    private String strategySnapshot;

    /**
     * 推荐理由（Recommend Reason）
     * 说明为什么推荐此策略，通常由 LLM 分析文档特征后生成
     */
    private String recommendReason;

    /**
     * 父块处理步骤列表（Parent Steps）
     * 定义如何将文档拆分为父级文本块（Parent Block）的步骤
     * 父块是切片的上一级组织单位
     */
    private List<DocumentStrategyStepDraft> parentSteps;

    /**
     * 子块处理步骤列表（Child Steps）
     * 定义如何将父块进一步拆分为子级切片（Chunk）的步骤
     * 子块是最终用于向量化和检索的文本单元
     */
    private List<DocumentStrategyStepDraft> childSteps;
}
