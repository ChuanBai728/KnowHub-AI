package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档策略计划返回值对象（Document Strategy Plan Vo）
 *
 * 【类的作用】
 * 用于展示文档处理策略计划的完整信息，包括计划的版本、来源、状态、
 * 策略快照、推荐理由以及两条流水线（父块和子块）的配置。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，是策略计划查询的核心数据对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStrategyPlanVo {

    /**
     * 计划ID（Plan ID）
     * 策略计划的唯一标识
     */
    private Long planId;

    /**
     * 计划版本（Plan Version）
     * 策略计划的版本号，支持多版本管理
     */
    private Integer planVersion;

    /**
     * 计划来源编码（Plan Source）
     * 标识计划的生成来源（如 LLM 自动生成、手动创建等）
     */
    private Integer planSource;

    /**
     * 计划来源名称（Plan Source Name）
     * 计划来源的可读名称
     */
    private String planSourceName;

    /**
     * 计划状态编码（Plan Status）
     * 计划的当前状态（如草稿、已确认、已执行等）
     */
    private Integer planStatus;

    /**
     * 计划状态名称（Plan Status Name）
     * 计划状态的可读名称
     */
    private String planStatusName;

    /**
     * 策略快照（Strategy Snapshot）
     * 策略配置的 JSON 序列化字符串
     */
    private String strategySnapshot;

    /**
     * 推荐理由（Recommend Reason）
     * LLM 推荐此策略的理由说明
     */
    private String recommendReason;

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
