package ai.knowhub.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 【检索结果展示】
 *
 * 作用：展示 RAG 检索流程中单个文档片段（Chunk）的检索结果详情。
 * 包含该片段在不同检索阶段的排名、分数、以及最终是否被选入上下文。
 *
 * 所属架构位置：属于 RAG 检索管道的结果模型，是检索可观测性的核心数据。
 * 通过此展示可以追踪每个文档片段从「被检索到」到「被选入上下文」的完整生命周期。
 *
 * 设计模式说明：「值对象（Value Object）」模式，用于记录检索结果的完整快照。
 * 其中涉及 RRF（Reciprocal Rank Fusion，倒数排名融合）和 Rerank（重排序）等
 * 检索领域的核心概念。
 *
 * @author knowhub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResultVo {

    /** 主键 ID */
    private Long id;

    /** 追踪 ID，关联到本次检索请求 */
    private String traceId;

    /** 子问题索引（当问题被拆分时） */
    private int subQuestionIndex;

    /** 子问题文本 */
    private String subQuestion;

    /**
     * 检索通道类型（channelType）
     * 标识该结果来自哪个检索通道，如 "keyword"（关键词）、"vector"（向量）等。
     */
    private String channelType;

    /**
     * 通道内排名（channelRank）
     * 该片段在所属检索通道中的排名（基于原始相关性分数）。
     */
    private Integer channelRank;

    /**
     * RRF 融合排名（rrfRank）
     * 经过 RRF（倒数排名融合）算法合并多通道结果后的排名。
     * RRF 是一种常用的多路检索结果融合方法。
     */
    private Integer rrfRank;

    /**
     * 最终排名（finalRank）
     * 经过重排序（Rerank）模型打分后的最终排名。
     */
    private Integer finalRank;

    /** 原始检索相关性分数 */
    private BigDecimal originalScore;

    /** RRF 融合分数 */
    private BigDecimal rrfScore;

    /**
     * 重排序分数（rerankScore）
     * 由重排序模型（如 Cross-Encoder）计算的相关性分数，
     * 通常比原始向量相似度更准确。
     */
    private BigDecimal rerankScore;

    /**
     * 是否通过质量门控（gatePassed）
     * 标识该片段是否通过了最低质量阈值的筛选。
     */
    private boolean gatePassed;

    /**
     * 是否被提升（isElevated）
     * 标识该片段是否因为特殊原因（如与问题高度匹配）被提升排名。
     */
    private boolean isElevated;

    /**
     * 是否最终被选中（isSelected）
     * 标识该片段是否最终被选入 AI 的上下文窗口。
     */
    private boolean isSelected;

    /**
     * 选中/未选中的原因（selectionReason）
     * 记录为什么该片段被选中或被排除，便于调试和优化。
     */
    private String selectionReason;

    /** 文档 ID */
    private Long documentId;

    /** 文档名称 */
    private String documentName;

    /**
     * 文档片段 ID（chunkId）
     * 唯一标识知识库中的一个文档片段（Chunk）。
     */
    private Long chunkId;

    /**
     * 片段编号（chunkNo）
     * 该片段在原始文档中的顺序编号。
     */
    private Integer chunkNo;

    /**
     * 父块 ID（parentBlockId）
     * 如果片段属于某个更大的结构块（如章节），此字段标识其父块。
     */
    private Long parentBlockId;

    /** 父块编号 */
    private Integer parentBlockNo;

    /**
     * 章节路径（sectionPath）
     * 该片段在文档结构中的位置路径，如 "第一章 > 第二节 > 第三小节"。
     */
    private String sectionPath;

    /** 片段文本预览（截取部分内容） */
    private String chunkTextPreview;

    /** 片段字符数 */
    private Integer chunkCharCount;

    /** 记录创建时间 */
    private Instant createTime;
}
