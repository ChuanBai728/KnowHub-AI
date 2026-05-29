package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档策略步骤返回值对象（Document Strategy Step Vo）
 *
 * 【类的作用】
 * 用于展示文档处理策略中单个步骤的详细信息，包括步骤编号、
 * 流水线类型、策略类型、角色、来源类型和执行状态。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，是 DocumentStrategyPipelineVo 的组成部分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStrategyStepVo {

    /**
     * 步骤编号（Step No）
     * 步骤在流水线中的顺序编号
     */
    private Integer stepNo;

    /**
     * 流水线类型编码（Pipeline Type）
     * 该步骤所属的流水线类型
     */
    private String pipelineType;

    /**
     * 流水线类型名称（Pipeline Type Name）
     */
    private String pipelineTypeName;

    /**
     * 策略类型编码（Strategy Type）
     * 该步骤使用的策略算法类型
     */
    private Integer strategyType;

    /**
     * 策略名称（Strategy Name）
     * 策略的可读名称
     */
    private String strategyName;

    /**
     * 策略角色编码（Strategy Role）
     * 该步骤在策略中扮演的角色
     */
    private Integer strategyRole;

    /**
     * 策略角色名称（Strategy Role Name）
     */
    private String strategyRoleName;

    /**
     * 来源类型编码（Source Type）
     * 该步骤处理的数据来源类型
     */
    private Integer sourceType;

    /**
     * 来源类型名称（Source Type Name）
     */
    private String sourceTypeName;

    /**
     * 执行状态编码（Execute Status）
     * 该步骤的执行状态
     */
    private Integer executeStatus;

    /**
     * 执行状态名称（Execute Status Name）
     */
    private String executeStatusName;

    /**
     * 推荐理由（Recommend Reason）
     * 推荐此步骤的理由说明
     */
    private String recommendReason;
}
