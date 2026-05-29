package ai.knowhub.document.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档策略步骤草案（Document Strategy Step Draft）
 *
 * 【类的作用】
 * 表示文档处理策略中的单个步骤。每个步骤定义了一种具体的处理操作，
 * 包括使用哪种流水线、策略类型、角色和来源类型。
 *
 * 【在架构中的角色】
 * 是 DocumentStrategyPlanDraft 的组成部分。一个策略计划包含多个步骤，
 * 每个步骤对应流水线中的一个处理阶段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStrategyStepDraft {

    /**
     * 流水线类型（Pipeline Type）
     * 标识该步骤属于哪种处理流水线
     * 例如：父块提取流水线、切片流水线等
     */
    private String pipelineType;

    /**
     * 策略类型（Strategy Type）
     * 标识该步骤使用的具体策略算法
     * 例如：基于结构的切片、基于语义的切片等
     */
    private Integer strategyType;

    /**
     * 策略角色（Strategy Role）
     * 标识该步骤在策略中扮演的角色
     * 例如：主策略、备选策略、补充策略等
     */
    private Integer strategyRole;

    /**
     * 来源类型（Source Type）
     * 标识该步骤处理的数据来源类型
     * 例如：正文、表格、列表等
     */
    private Integer sourceType;

    /**
     * 推荐理由（Recommend Reason）
     * 说明为什么推荐此步骤，通常由 LLM 分析后生成
     */
    private String recommendReason;
}
