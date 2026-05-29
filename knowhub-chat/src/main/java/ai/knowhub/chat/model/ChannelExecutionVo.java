package ai.knowhub.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 【检索通道执行展示】
 *
 * 作用：用于展示 RAG（检索增强生成）流程中，某个检索通道的执行情况。
 * 例如：关键词检索通道、向量检索通道、网络搜索通道等，每个通道都会产生一条执行记录。
 *
 * 所属架构位置：属于 RAG 检索管道（Retrieval Pipeline）的可观测性模型，
 * 用于前端展示检索过程的调试信息和性能指标。
 *
 * 设计模式说明：这是一个典型的「返回值对象 / 值对象（Value Object / VO）」模式，
 * 仅用于数据展示，不包含业务逻辑。使用 Lombok 的 @Data 自动生成 getter/setter、
 * toString、equals、hashCode 等方法。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelExecutionVo {

    /** 主键 ID，数据库自增主键 */
    private Long id;

    /**
     * 追踪 ID（traceId）
     * 用于将同一次对话请求中的所有检索操作串联起来，便于日志追踪和问题排查。
     */
    private String traceId;

    /**
     * 子问题索引
     * 当用户的问题被拆分为多个子问题时，此字段标识当前是第几个子问题（从 0 开始）。
     */
    private int subQuestionIndex;

    /**
     * 子问题文本
     * 原始用户问题被改写/拆分后的子问题内容。
     */
    private String subQuestion;

    /**
     * 通道类型（channelType）
     * 标识使用了哪种检索通道，常见的值：
     * - "keyword"：关键词检索（通常基于 Elasticsearch）
     * - "vector"：向量检索（基于嵌入向量的相似度搜索）
     * - "web-search"：网络搜索（如 Tavily 搜索工具）
     */
    private String channelType;

    /**
     * 执行状态
     * 标识该通道的执行结果状态，例如：
     * 0=待执行，1=执行中，2=成功，3=失败 等。
     */
    private int executionState;

    /** 通道检索开始时间 */
    private Instant startTime;

    /** 通道检索结束时间 */
    private Instant endTime;

    /** 通道检索耗时（毫秒） */
    private Long durationMs;

    /**
     * 召回数量（recalledCount）
     * 该通道从知识库中初步检索到的文档片段数量。
     */
    private int recalledCount;

    /**
     * 通过质量门控的数量（acceptedCount）
     * 经过初步质量筛选后，被认为符合条件的文档片段数量。
     */
    private int acceptedCount;

    /**
     * 最终选中数量（finalSelectedCount）
     * 经过重排序（rerank）和预算控制后，最终被选入上下文的文档片段数量。
     */
    private int finalSelectedCount;

    /** 检索结果的平均相关性分数 */
    private BigDecimal avgScore;

    /** 检索结果中的最高相关性分数 */
    private BigDecimal maxScore;

    /** 检索结果中的最低相关性分数 */
    private BigDecimal minScore;

    /** 如果通道执行失败，此字段记录错误信息 */
    private String errorMessage;

    /** 记录创建时间 */
    private Instant createTime;
}
