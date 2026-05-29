package ai.knowhub.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

import java.util.Date;

/**
 * 文档切块策略方案实体类。
 *
 * 对应数据库表 knowhub_document_strategy_plan，记录文档的切块策略推荐方案。
 *
 * 「策略方案」是系统为文档推荐的切块策略的完整描述。一个文档可以有多个策略方案
 * （如系统推荐一版、用户手动调整一版），但只有一个被确认生效。
 *
 * 策略推荐流程：
 * 
 *   文档解析完成后，系统分析文档特征（长度、结构复杂度、内容类型等）。
 *   基于文档特征，系统自动推荐最优的切块策略（递归/语义/LLM）。
 *   推荐结果以策略方案的形式存储，供用户查看和确认。
 *   用户可以接受推荐方案，也可以手动调整策略参数。
 *   确认后的方案标记为生效状态，后续的切块操作按照此方案执行。
 * 版本管理：通过 planVersion 字段支持策略方案的版本管理，
 * 每次生成新方案时版本号递增。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document_strategy_plan")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocumentStrategyPlan extends BaseTableData {

    /**
     * 策略方案主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 关联的文档 ID。
     * 关联到 KnowHubDocument 表。
     */
    private Long documentId;

    /**
     * 方案版本号。
     * 每次生成新方案时递增，支持版本管理和历史追踪。
     */
    private Integer planVersion;

    /**
     * 方案来源枚举值。
     * 标识方案的生成方式：系统自动推荐、用户手动创建等。
     */
    private Integer planSource;

    /**
     * 方案状态枚举值。
     * 标识方案的当前状态：草稿、已确认、已废弃等。
     */
    private Integer planStatus;

    /**
     * 策略步骤数量。
     * 此方案包含的切块策略步骤总数。
     * 一个方案可能包含多个步骤（如先按标题拆分，再按段落拆分）。
     */
    private Integer strategyCount;

    /**
     * 策略快照 JSON。
     * 将策略配置序列化为 JSON 字符串存储，保留策略的完整配置信息。
     * 即使后续策略配置发生变化，已确认的方案仍然保留原始配置。
     */
    private String strategySnapshot;

    /**
     * 推荐理由。
     * 系统推荐此策略的原因说明，帮助用户理解为什么选择这个策略。
     */
    private String recommendReason;

    /**
     * 调整说明。
     * 用户手动调整策略时填写的说明，记录用户的调整意图。
     */
    private String adjustNote;

    /**
     * 确认人用户 ID。
     * 记录是哪个用户确认了此方案，便于审计追踪。
     */
    private Long confirmUserId;

    /**
     * 确认时间。
     * 方案被确认的时间戳。
     */
    private Date confirmTime;
}
