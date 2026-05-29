package ai.knowhub.chat.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

import java.math.BigDecimal;

/**
 * RAG 检索结果的数据实体。
 *
 * 对应数据库表：knowhub_chat_retrieval_result
 *
 * 什么是检索结果？
 * 当用户提出问题时，RAG 系统会从知识库中检索相关的文档片段。
 * 每个被检索到的文档片段都会生成一条检索结果记录。
 *
 * 检索结果的处理流程
 * 
 *   <b>召回</b>：多通道（关键词 + 向量）分别检索，各自返回候选片段
 *   <b>融合</b>：使用 RRF（Reciprocal Rank Fusion）算法融合多通道结果
 *   <b>过滤</b>：通过门控（Gate）机制过滤低质量结果
 *   <b>重排序</b>：使用 Rerank 模型对结果进行精排
 *   <b>选择</b>：最终选出用于生成回答的文档片段
 * 关键分数说明
 * 
 *   <b>originalScore</b>：原始检索分数（由向量数据库或搜索引擎返回）
 *   <b>rrfScore</b>：RRF 融合分数（综合多通道的排名）
 *   <b>rerankScore</b>：重排序分数（由 Rerank 模型给出的精确相关性分数）
 * 继承关系
 * 继承自 BaseTableData，自动拥有创建时间、更新时间等公共字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_chat_retrieval_result")
@EqualsAndHashCode(callSuper = true)
public class KnowHubChatRetrievalResult extends BaseTableData {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 对话编码（会话 ID）。
     */
    @TableField("dialogue_code")
    private String conversationId;

    /**
     * 对话交换 ID。
     * 标识这条检索结果属于哪一次交换。
     */
    @TableField("exchange_id")
    private Long exchangeId;

    /**
     * 追踪 ID。
     * 用于分布式链路追踪。
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 子问题索引。
     * 当用户问题被拆分为多个子问题时，标识当前是第几个子问题。
     */
    @TableField("sub_question_index")
    private Integer subQuestionIndex;

    /**
     * 子问题内容。
     * 经过查询改写后生成的子问题文本。
     */
    @TableField("sub_question")
    private String subQuestion;

    /**
     * 检索通道类型。
     * 标识这条结果来自哪个检索通道，如 "keyword" 或 "vector"。
     */
    @TableField("channel_type")
    private String channelType;

    /**
     * 通道内排名。
     * 这条结果在其所属检索通道中的排名（从 1 开始）。
     */
    @TableField("channel_rank")
    private Integer channelRank;

    /**
     * RRF 融合排名。
     * 经过 RRF 算法融合多通道结果后的排名。
     */
    @TableField("rrf_rank")
    private Integer rrfRank;

    /**
     * 最终排名。
     * 经过重排序（Rerank）后的最终排名，决定哪些结果会被用于生成回答。
     */
    @TableField("final_rank")
    private Integer finalRank;

    /**
     * 原始检索分数。
     * 由检索引擎（Elasticsearch 或向量数据库）直接返回的相似度分数。
     * 不同通道的原始分数不可直接比较。
     */
    @TableField("original_score")
    private BigDecimal originalScore;

    /**
     * RRF 融合分数。
     * 使用 RRF（Reciprocal Rank Fusion）算法计算的融合分数。
     * RRF 的公式：score = 1 / (k + rank)，其中 k 通常为 60。
     */
    @TableField("rrf_score")
    private BigDecimal rrfScore;

    /**
     * 重排序分数。
     * 由 Rerank 模型（如 BGE-Reranker）给出的精确相关性分数。
     * 分数越高，表示该文档片段与用户问题越相关。
     */
    @TableField("rerank_score")
    private BigDecimal rerankScore;

    /**
     * 是否通过门控过滤。
     * 门控（Gate）机制会过滤掉分数过低的结果。
     * 0 = 未通过（被过滤），1 = 通过。
     */
    @TableField("gate_passed")
    private Integer gatePassed;

    /**
     * 是否被提升。
     * 某些特殊规则可能会将特定结果的排名提升。
     * 0 = 正常排名，1 = 被提升。
     */
    @TableField("is_elevated")
    private Integer isElevated;

    /**
     * 是否被最终选中。
     * 只有被选中的结果才会被用于组装 Prompt 和生成回答。
     * 0 = 未选中，1 = 选中。
     */
    @TableField("is_selected")
    private Integer isSelected;

    /**
     * 选中/排除原因。
     * 记录该结果被选中或排除的具体原因，便于调试和优化。
     * 例如："rerank_score > threshold" 或 "below_gate_threshold"。
     */
    @TableField("selection_reason")
    private String selectionReason;

    /**
     * 文档 ID。
     * 该检索结果所属的知识文档的 ID。
     */
    @TableField("document_id")
    private Long documentId;

    /**
     * 文档名称。
     * 冗余存储文档名称，方便展示。
     */
    @TableField("document_name")
    private String documentName;

    /**
     * 文档片段（Chunk）ID。
     * 文档被分割为多个片段（Chunk），每个片段有唯一的 ID。
     */
    @TableField("chunk_id")
    private Long chunkId;

    /**
     * 文档片段编号。
     * 该片段在其所属文档中的序号（从 1 开始）。
     */
    @TableField("chunk_no")
    private Integer chunkNo;

    /**
     * 父级块 ID。
     * 如果使用了层级分割策略，此字段指向该片段所属的父级块。
     */
    @TableField("parent_block_id")
    private Long parentBlockId;

    /**
     * 父级块编号。
     */
    @TableField("parent_block_no")
    private Integer parentBlockNo;

    /**
     * 章节路径。
     * 该片段在文档中的位置路径，如 "第3章 > 3.2节 > 第2段"。
     * 帮助用户理解片段的上下文。
     */
    @TableField("section_path")
    private String sectionPath;

    /**
     * 文档片段的文本预览。
     * 存储片段内容的前 N 个字符，用于在调试面板中快速预览。
     * 完整内容需要通过其他接口查询。
     */
    @TableField("chunk_text_preview")
    private String chunkTextPreview;

    /**
     * 文档片段的字符数。
     * 该片段的总字符长度，用于评估片段大小是否合理。
     */
    @TableField("chunk_char_count")
    private Integer chunkCharCount;
}
