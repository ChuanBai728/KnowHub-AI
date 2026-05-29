package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档策略确认返回值对象（Document Strategy Confirm Vo）
 *
 * 【类的作用】
 * 用于展示文档处理策略的确认信息，包括策略状态和两个流水线
 * （父块流水线和子块流水线）的配置详情，供用户确认后执行。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于策略确认页面的数据展示。
 * 用户在查看策略详情后，可以确认执行或修改策略。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStrategyConfirmVo {

    /**
     * 文档ID（Document ID）
     * 关联的文档标识
     */
    private Long documentId;

    /**
     * 计划ID（Plan ID）
     * 策略计划的唯一标识
     */
    private Long planId;

    /**
     * 计划版本（Plan Version）
     * 策略计划的版本号
     */
    private Integer planVersion;

    /**
     * 策略状态编码（Strategy Status）
     * 策略的当前状态编码
     */
    private Integer strategyStatus;

    /**
     * 策略状态名称（Strategy Status Name）
     * 策略状态的可读名称
     */
    private String strategyStatusName;

    /**
     * 是否已标准化（Normalized）
     * 标识策略是否已经过标准化处理
     */
    private Boolean normalized;

    /**
     * 父块流水线（Parent Pipeline）
     * 父级文本块的处理流水线配置
     */
    private DocumentStrategyPipelineVo parentPipeline;

    /**
     * 子块流水线（Child Pipeline）
     * 子级切片的处理流水线配置
     */
    private DocumentStrategyPipelineVo childPipeline;
}
