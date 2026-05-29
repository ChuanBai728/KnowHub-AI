package ai.knowhub.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

/**
 * 文档切块策略步骤实体类。
 *
 * 对应数据库表 knowhub_document_strategy_step，记录策略方案中的每个切块步骤。
 *
 * 一个策略方案（KnowHubDocumentStrategyPlan）可以包含多个步骤，
 * 每个步骤定义了一种特定的切块方式。步骤按照 stepNo 顺序执行。
 *
 * 多步骤策略的典型场景：
 * 
 *   <b>步骤 1</b>：按标题层级拆分文档为章节（结构化拆分）。
 *   <b>步骤 2</b>：对每个章节进行递归切块（递归拆分）。
 *   <b>步骤 3</b>：对特殊内容（如表格、代码块）进行语义切块（语义拆分）。
 * 每个步骤的关键属性：
 * 
 *   <b>pipelineType</b>：流水线类型，标识此步骤属于哪个处理阶段。
 *   <b>strategyType</b>：策略类型，标识使用的切块算法。
 *   <b>strategyRole</b>：策略角色，标识此步骤在方案中的定位（主策略/辅助策略）。
 *   <b>sourceType</b>：来源类型，标识此步骤的输入数据类型。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document_strategy_step")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocumentStrategyStep extends BaseTableData {

    /**
     * 策略步骤主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 关联的策略方案 ID。
     * 关联到 KnowHubDocumentStrategyPlan 表。
     */
    private Long planId;

    /**
     * 关联的文档 ID。
     * 关联到 KnowHubDocument 表。
     */
    private Long documentId;

    /**
     * 步骤序号。
     * 标识此步骤在策略方案中的执行顺序，从 1 开始递增。
     */
    private Integer stepNo;

    /**
     * 流水线类型。
     * 标识此步骤属于哪个处理阶段的流水线，如 "parse"（解析）、"chunk"（切块）、"index"（索引）等。
     */
    private String pipelineType;

    /**
     * 策略类型枚举值。
     * 标识使用的切块算法，如递归切块、语义切块、LLM 切块等。
     */
    private Integer strategyType;

    /**
     * 策略角色枚举值。
     * 标识此步骤在方案中的定位：主策略（主要切块方式）、辅助策略（补充切块方式）等。
     */
    private Integer strategyRole;

    /**
     * 来源类型枚举值。
     * 标识此步骤处理的输入数据类型，如原始文本、结构节点等。
     */
    private Integer sourceType;

    /**
     * 执行状态枚举值。
     * 标识此步骤的执行状态：未执行、执行中、已完成、失败等。
     */
    private Integer executeStatus;

    /**
     * 推荐理由。
     * 系统推荐此步骤的原因说明。
     */
    private String recommendReason;
}
