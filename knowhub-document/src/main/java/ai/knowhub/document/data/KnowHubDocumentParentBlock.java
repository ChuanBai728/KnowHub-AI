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
 * 文档父切块实体类。
 *
 * 对应数据库表 knowhub_document_parent_block，记录文档的父级文本块。
 *
 * 「父子切块」（Parent-Child Chunking）是高级切块策略的核心概念：
 * 
 *   <b>子切块</b>（Child Chunk）：较小的文本片段（如 300-800 字符），
 *       用于向量化和语义检索，保证检索的精确性。
 *   <b>父切块</b>（Parent Block）：较大的文本片段（如 1000-3000 字符），
 *       包含多个子切块，检索命中子切块后返回父块内容，为 LLM 提供更完整的上下文。
 * 为什么需要父子切块？
 * 
 *   如果只用小切块检索，虽然精确但上下文不完整，LLM 可能无法理解。
 *   如果只用大切块检索，上下文完整但语义模糊，检索不精确。
 *   父子切块策略兼顾两者：用小切块精确检索，用大切块提供上下文。
 * 典型流程：
 * 
 *   将文档切分为父切块（较大片段）。
 *   将每个父切块进一步切分为子切块（较小片段）。
 *   对子切块进行向量化，存储到向量数据库。
 *   用户提问时，检索最相关的子切块。
 *   找到子切块对应的父切块，将父切块内容作为 LLM 上下文。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document_parent_block")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocumentParentBlock extends BaseTableData {

    /**
     * 父切块主键 ID。
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
     * 标识此父切块是由哪个解析任务生成的。
     */
    private Long taskId;

    /**
     * 关联的策略方案 ID。
     * 标识此父切块使用的是哪个切块策略。
     */
    private Long planId;

    /**
     * 父切块序号。
     * 在文档中的父切块编号，从 1 开始递增。
     */
    private Integer parentNo;

    /**
     * 来源类型枚举值。
     * 标识父切块的生成方式。
     */
    private Integer sourceType;

    /**
     * 章节路径。
     * 父切块在文档结构中的路径。
     */
    private String sectionPath;

    /**
     * 关联的结构节点 ID。
     * 关联到 KnowHubDocumentStructureNode 表。
     */
    private Long structureNodeId;

    /**
     * 结构节点类型。
     */
    private Integer structureNodeType;

    /**
     * 规范化路径标识。
     */
    private String canonicalPath;

    /**
     * 在父级节点中的序号。
     */
    private Integer itemIndex;

    /**
     * 父切块正文内容。
     * 父切块的完整文本，比子切块更大，提供更丰富的上下文信息。
     * 检索命中子切块后，会将对应的父切块内容作为 LLM 的输入上下文。
     */
    private String parentText;

    /**
     * 字符数。
     * 父切块文本的字符长度。
     */
    private Integer charCount;

    /**
     * Token 数。
     * 父切块文本的 Token 数量。
     */
    private Integer tokenCount;

    /**
     * 子切块数量。
     * 此父切块包含的子切块个数。
     */
    private Integer childCount;

    /**
     * 起始子切块序号。
     * 此父切块包含的第一个子切块的 chunkNo。
     */
    private Integer startChunkNo;

    /**
     * 结束子切块序号。
     * 此父切块包含的最后一个子切块的 chunkNo。
     * 配合 startChunkNo 可以确定父切块覆盖的子切块范围。
     */
    private Integer endChunkNo;
}
