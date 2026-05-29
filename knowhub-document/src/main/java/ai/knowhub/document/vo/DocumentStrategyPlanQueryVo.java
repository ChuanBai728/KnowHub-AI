package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档策略计划查询返回值对象（Document Strategy Plan Query Vo）
 *
 * 【类的作用】
 * 用于查询文档策略计划的响应，包含文档的基本状态信息和策略计划详情。
 * 展示文档从解析到索引的完整流程状态。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于策略计划查询页面的数据展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStrategyPlanQueryVo {

    /**
     * 文档ID（Document ID）
     * 关联的文档标识
     */
    private Long documentId;

    /**
     * 文档名称（Document Name）
     */
    private String documentName;

    /**
     * 解析状态编码（Parse Status）
     * 文档解析的当前状态
     */
    private Integer parseStatus;

    /**
     * 解析状态名称（Parse Status Name）
     */
    private String parseStatusName;

    /**
     * 策略状态编码（Strategy Status）
     * 策略的当前状态
     */
    private Integer strategyStatus;

    /**
     * 策略状态名称（Strategy Status Name）
     */
    private String strategyStatusName;

    /**
     * 索引状态编码（Index Status）
     * 索引构建的当前状态
     */
    private Integer indexStatus;

    /**
     * 索引状态名称（Index Status Name）
     */
    private String indexStatusName;

    /**
     * 解析错误信息（Parse Error Message）
     * 解析失败时的错误描述
     */
    private String parseErrorMsg;

    /**
     * 计划是否就绪（Plan Ready）
     * 标识策略计划是否已生成并可供查看
     */
    private Boolean planReady;

    /**
     * 策略计划详情（Plan）
     * 策略计划的完整信息（如果已就绪）
     */
    private DocumentStrategyPlanVo plan;
}
