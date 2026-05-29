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
 * 文档结构节点实体类。
 *
 * 对应数据库表 knowhub_document_structure_node，记录文档解析后的结构树信息。
 *
 * 「结构节点」是文档结构解析的核心概念。文档解析器会将文档的标题、段落、
 * 列表等元素识别为结构节点，并建立它们之间的层级关系，形成一棵结构树。
 *
 * 结构树示例：
 * 
 *   文档根节点 (depth=0)
 *   ├── 第一章 标题 (depth=1, nodeType=heading-1)
 *   │   ├── 1.1 节标题 (depth=2, nodeType=heading-2)
 *   │   │   ├── 段落内容 (depth=3, nodeType=paragraph)
 *   │   │   └── 段落内容 (depth=3, nodeType=paragraph)
 *   │   └── 1.2 节标题 (depth=2, nodeType=heading-2)
 *   │       └── 段落内容 (depth=3, nodeType=paragraph)
 *   └── 第二章 标题 (depth=1, nodeType=heading-1)
 *       └── 2.1 节标题 (depth=2, nodeType=heading-2)
 * 结构树的用途：
 * 
 *   前端展示文档的目录结构（大纲展示）。
 *   基于结构的精准检索（如只在某个章节下检索）。
 *   切块时保留结构上下文（每个切块知道它属于哪个章节）。
 *   导航索引：在 Elasticsearch 中建立结构导航索引，支持结构化查询。
 * 节点间的关联关系：
 * 
 *   parentNodeId：父子关系（树结构的核心）。
 *   prevSiblingNodeId/nextSiblingNodeId：兄弟节点关系（双向链表）。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_document_structure_node")
@EqualsAndHashCode(callSuper = true)
public class KnowHubDocumentStructureNode extends BaseTableData {

    /**
     * 结构节点主键 ID。
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
     * 关联的解析任务 ID。
     * 标识此节点是由哪个解析任务生成的。
     */
    private Long parseTaskId;

    /**
     * 节点序号。
     * 节点在文档中的出现顺序编号，从 1 开始递增。
     */
    private Integer nodeNo;

    /**
     * 节点类型枚举值。
     * 标识节点的类型，如 heading-1（一级标题）、heading-2（二级标题）、
     * paragraph（段落）、table（表格）、code（代码块）等。
     */
    private Integer nodeType;

    /**
     * 父节点 ID。
     * 关联到本表的另一个记录，表示此节点的父级节点。
     * 根节点的 parentNodeId 为 null。
     */
    private Long parentNodeId;

    /**
     * 前一个兄弟节点 ID。
     * 关联到本表的另一个记录，用于构建兄弟节点的双向链表。
     * 如果是第一个子节点，prevSiblingNodeId 为 null。
     */
    private Long prevSiblingNodeId;

    /**
     * 后一个兄弟节点 ID。
     * 关联到本表的另一个记录，用于构建兄弟节点的双向链表。
     * 如果是最后一个子节点，nextSiblingNodeId 为 null。
     */
    private Long nextSiblingNodeId;

    /**
     * 节点深度。
     * 节点在结构树中的层级深度，根节点为 0，一级标题为 1，以此类推。
     */
    private Integer depth;

    /**
     * 节点编码。
     * 节点的层级编码，如 "1"（第一章）、"1.2"（第一章第二节）、"1.2.3"（第一章第二节第三小节）。
     */
    private String nodeCode;

    /**
     * 节点标题。
     * 标题节点的标题文本。对于段落节点，此字段可能为 null。
     */
    private String title;

    /**
     * 锚点文本。
     * 用于前端定位的锚点标识文本。
     */
    private String anchorText;

    /**
     * 规范化路径标识。
     * 节点在文档中的唯一路径标识符，用于精确匹配。
     */
    private String canonicalPath;

    /**
     * 章节路径。
     * 节点在文档结构中的可读路径，如 "第一章 > 第二节 > 第三小节"。
     */
    private String sectionPath;

    /**
     * 节点正文内容。
     * 对于段落节点，存储段落的文本内容。
     * 对于标题节点，可能存储标题下的简介文本。
     */
    private String contentText;

    /**
     * 在父节点中的序号。
     * 节点在其父节点的子节点列表中的位置序号，从 0 开始。
     */
    private Integer itemIndex;
}
