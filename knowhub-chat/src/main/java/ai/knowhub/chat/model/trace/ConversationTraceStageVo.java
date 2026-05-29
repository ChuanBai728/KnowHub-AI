package ai.knowhub.chat.model.trace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 【会话追踪阶段展示】
 *
 * 作用：展示对话处理流程中单个阶段的执行详情。
 * 记录了每个阶段的开始/结束时间、执行状态、耗时、摘要信息等，
 * 用于前端展示对话处理的全链路追踪（类似分布式系统中的链路追踪）。
 *
 * 所属架构位置：属于会话追踪系统的核心展示模型，
 * 与 ConversationTraceStageCode（阶段编码）和 ConversationTraceStageState（阶段状态）
 * 配合使用，构成完整的阶段追踪体系。
 *
 * 设计模式说明：「返回值对象（VO）」模式，用于展示阶段执行的完整快照。
 * 其中 snapshot 字段使用 Map 结构存储各阶段特有的快照数据，
 * 提供了灵活的扩展能力。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTraceStageVo {

    /** 阶段记录 ID，数据库自增主键 */
    private long stageId;

    /** 追踪 ID（traceId），关联到本次对话请求 */
    private String traceId;

    /**
     * 阶段编码（stageCode）
     * 标识是哪个处理阶段，取值来自 ConversationTraceStageCode 枚举。
     */
    private String stageCode;

    /** 阶段名称（中文），如 "会话记忆"、"问题改写" 等 */
    private String stageName;

    /**
     * 阶段执行顺序（stageOrder）
     * 数值越小越先执行，用于前端按顺序展示阶段列表。
     */
    private Integer stageOrder;

    /**
     * 阶段层级（stageLevel）
     * 标识阶段的层级深度，用于支持嵌套阶段的树形展示。
     * 例如：主阶段 level=0，子阶段 level=1。
     */
    private Integer stageLevel;

    /**
     * 父阶段 ID（parentStageId）
     * 如果当前阶段是某个主阶段的子阶段，此字段指向父阶段的 ID。
     */
    private Long parentStageId;

    /**
     * 执行模式（executionMode）
     * 标识使用的执行模式，如 "ReactAgent"、"GraphOnly" 等。
     */
    private String executionMode;

    /**
     * 阶段状态（stageState）
     * 标识当前阶段的执行状态，取值来自 ConversationTraceStageState 枚举。
     */
    private String stageState;

    /** 阶段开始时间 */
    private Instant startTime;

    /** 阶段结束时间 */
    private Instant endTime;

    /** 阶段执行耗时（毫秒） */
    private Long durationMs;

    /**
     * 摘要文本（summaryText）
     * 阶段执行结果的简要描述，如 "改写为3个子问题"、"检索到15个文档片段" 等。
     */
    private String summaryText;

    /** 如果阶段执行失败，记录错误信息 */
    private String errorMessage;

    /**
     * 快照数据（snapshot）
     * 存储各阶段特有的详细数据，使用灵活的 Map 结构。
     * 例如：
     * - 问题改写阶段：改写前后的问题对比
     * - 检索阶段：检索结果列表
     * - 回答生成阶段：Token 消耗统计
     */
    private Map<String, Object> snapshot;
}
