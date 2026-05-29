package ai.knowhub.document.model.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档导航索引记录（Document Navigation Index Record）
 *
 * 【类的作用】
 * 映射 Elasticsearch 中的文档结构导航节点索引记录。
 * 每个实例代表文档目录结构中的一个节点（如章节、标题、段落），
 * 用于支持文档的结构化导航和层级检索。
 *
 * 【在架构中的角色】
 * 属于 RAG 流程中"文档解析"阶段的产物：
 * 1. 文档上传后，解析器会识别文档的层级结构（目录、章节、段落等）
 * 2. 每个结构节点创建一条此记录，写入 Elasticsearch
 * 3. 检索时可以通过节点类型、深度、层级关系等条件进行结构化查询
 *
 * 【与关键词索引的关系】
 * - DocumentKeywordIndexRecord：存储文档的文本内容块，用于全文检索
 * - DocumentNavigationIndexRecord：存储文档的结构节点，用于结构化导航
 * 两者配合使用，既能做全文检索，又能做结构化导航。
 *
 * 【Elasticsearch 中的作用】
 * 此记录对应的 ES 索引通常用于：
 * - 按章节路径快速定位文档片段
 * - 获取某个节点的父节点、兄弟节点等结构信息
 * - 支持文档目录树的前端渲染
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentNavigationIndexRecord {

    /**
     * 节点唯一标识 ID
     * 文档结构树中每个节点的唯一 ID，也是 ES 文档的 _id。
     */
    private Long nodeId;

    /**
     * 所属文档 ID
     * 关联到知识库中的源文档。
     */
    private Long documentId;

    /**
     * 解析任务 ID
     * 标识该节点是由哪次解析任务生成的。
     */
    private Long parseTaskId;

    /**
     * 节点类型
     * 描述该结构节点的类型，例如：
     * - "chapter"：章节
     * - "section"：小节
     * - "paragraph"：段落
     * - "table"：表格
     * - "list"：列表
     */
    private String nodeType;

    /**
     * 节点编码
     * 节点的编号标识，例如"1.2.3"表示第一章第二节第三小节，
     * 用于文档目录的排序和展示。
     */
    private String nodeCode;

    /**
     * 节点序号
     * 节点在同级兄弟节点中的顺序编号，从 0 开始。
     */
    private Integer nodeNo;

    /**
     * 节点深度
     * 节点在文档结构树中的层级深度，
     * 例如根节点深度为 0，一级标题深度为 1，以此类推。
     */
    private Integer depth;

    /**
     * 父节点 ID
     * 指向该节点的父节点，用于构建树形结构。
     * 如果为 null，表示该节点是根节点。
     */
    private Long parentNodeId;

    /**
     * 节点标题
     * 例如章节标题"1.1 项目背景"、段落标题等。
     */
    private String title;

    /**
     * 锚点文本
     * 文档中用于内部链接跳转的锚点标识文本，
     * 例如 HTML 中的 <a name="section1"> 对应的 "section1"。
     */
    private String anchorText;

    /**
     * 章节路径
     * 节点在文档目录中的完整路径，
     * 例如"第一章/第2节/2.1 小节标题"。
     */
    private String sectionPath;

    /**
     * 规范路径
     * 节点的标准化唯一路径标识，不依赖文档的目录编号格式。
     */
    private String canonicalPath;

    /**
     * 内容文本
     * 该节点对应的文本内容。
     * 对于标题节点，可能与 title 相同；
     * 对于段落节点，是段落的完整文本。
     */
    private String contentText;

    /**
     * 条目索引
     * 该节点在其所属层级中的全局顺序索引，
     * 用于检索结果的排序。
     */
    private Integer itemIndex;
}
