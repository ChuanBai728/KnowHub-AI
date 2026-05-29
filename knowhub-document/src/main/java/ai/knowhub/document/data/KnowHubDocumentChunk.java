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
 * 文档切块实体类。
 *
 * 对应数据库表 knowhub_document_chunk，记录文档拆分后的每个文本片段。
 *
 * 「切块」（Chunk）是 RAG（检索增强生成）系统的核心概念：
 * 
 *   将长文档拆分为较小的文本片段（通常几百个字符）。
 *   每个切块独立进行向量化（Embedding），存储到向量数据库。
 *   用户提问时，系统通过语义检索找到最相关的切块，作为 LLM 的上下文。
 * 切块的质量直接影响 RAG 检索的效果：
 * 
 *   切块太大 → 语义模糊，检索不精确。
 *   切块太小 → 缺乏上下文，LLM 无法理解。
 *   切块边界不合理 → 语义被切断，信息不完整。
 * 本切块系统支持「父子切块」模式：
 * 
 *   <b>子切块</b>（Child Chunk）：用于向量检索的小片段，保证检索精度。
 *   <b>父切块</b>（Parent Block）：包含子切块的较大片段，检索命中后返回父块，
 *       为 LLM 提供更完整的上下文。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document_chunk")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocumentChunk extends BaseTableData {

    /**
     * 切块主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 所属文档 ID。
     * 关联到 KnowHubDocument 表。
     */
    private Long documentId;

    /**
     * 关联的任务 ID。
     * 标识此切块是由哪个解析任务生成的。
     */
    private Long taskId;

    /**
     * 关联的策略方案 ID。
     * 标识此切块使用的是哪个切块策略。
     */
    private Long planId;

    /**
     * 所属父切块 ID。
     * 关联到 KnowHubDocumentParentBlock 表。
     * 在父子切块模式中，每个子切块都归属于一个父切块。
     */
    private Long parentBlockId;

    /**
     * 切块序号。
     * 在文档中的切块编号，从 1 开始递增，用于保持切块的顺序。
     */
    private Integer chunkNo;

    /**
     * 来源类型枚举值。
     * 标识切块的生成方式，如递归切块、语义切块、LLM 切块等。
     */
    private Integer sourceType;

    /**
     * 章节路径。
     * 切块在文档结构中的路径，如 "第1章 > 第2节 > 第3小节"。
     * 用于 RAG 检索时提供结构化上下文信息。
     */
    private String sectionPath;

    /**
     * 关联的结构节点 ID。
     * 关联到 KnowHubDocumentStructureNode 表，
     * 标识此切块归属于文档结构树中的哪个节点。
     */
    private Long structureNodeId;

    /**
     * 结构节点类型。
     * 标识关联的结构节点类型，如标题、段落、列表等。
     */
    private Integer structureNodeType;

    /**
     * 规范化路径标识。
     * 结构节点的唯一路径标识符，用于精确匹配。
     */
    private String canonicalPath;

    /**
     * 在父级节点中的序号。
     * 切块在其所属结构节点中的位置序号。
     */
    private Integer itemIndex;

    /**
     * 切块正文内容。
     * 切块的实际文本内容，是 RAG 检索和 LLM 上下文的核心数据。
     */
    private String chunkText;

    /**
     * 字符数。
     * 切块文本的字符长度，用于质量评估和展示。
     */
    private Integer charCount;

    /**
     * Token 数。
     * 切块文本的 Token 数量，用于估算 LLM 处理成本。
     */
    private Integer tokenCount;

    /**
     * 向量化状态枚举值。
     * 标识此切块是否已成功转换为向量：未向量化、向量化中、已完成、失败等。
     */
    private Integer vectorStatus;

    /**
     * 向量存储类型枚举值。
     * 标识向量存储在哪里，如 pgvector、Milvus 等。
     */
    private Integer vectorStoreType;

    /**
     * 向量存储中的唯一标识。
     * 向量写入向量数据库后返回的 ID，用于后续更新或删除。
     */
    private String vectorId;
}
