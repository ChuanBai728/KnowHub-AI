package ai.knowhub.document.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档切片候选对象（Chunk Candidate）
 *
 * 【类的作用】
 * 在文档处理流程中，文档会被拆分成多个"切片"（Chunk）用于向量化和检索。
 * 本类表示一个待处理的文档切片候选，包含了切片的文本内容及其在文档结构中的位置信息。
 *
 * 【在架构中的角色】
 * 属于文档管理模块（manage）的支持层（support），是文档解析和切片流程中的中间数据对象。
 * 文档经过结构分析后，会生成多个 ChunkCandidate，然后进一步处理为最终的向量化切片。
 *
 * 【设计模式】
 * 使用 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor 自动生成 getter/setter、
 * 无参构造器和全参构造器，减少样板代码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkCandidate {

    /**
     * 章节路径（Section Path）
     * 表示该切片在文档结构树中的路径，例如 "第一章 > 第一节 > 背景"
     * 用于标识切片所属的文档层级位置
     */
    private String sectionPath;

    /**
     * 结构节点ID（Structure Node ID）
     * 关联到文档结构树中的具体节点，用于回溯切片的结构来源
     */
    private Long structureNodeId;

    /**
     * 结构节点类型（Structure Node Type）
     * 标识节点的类型，如文档节点、章节节点、列表项节点等
     */
    private Integer structureNodeType;

    /**
     * 规范路径（Canonical Path）
     * 节点的标准化路径，格式如 "/document/chapter-1/section-2"
     * 用于唯一标识文档结构中的一个位置
     */
    private String canonicalPath;

    /**
     * 项目索引（Item Index）
     * 当切片来源于列表项或步骤项时，记录其在列表中的序号
     */
    private Integer itemIndex;

    /**
     * 切片文本内容（Text）
     * 该候选切片的实际文本内容，后续会被向量化处理
     */
    private String text;

    /**
     * 来源类型（Source Type）
     * 标识切片的来源方式，例如：正文、表格、列表等
     */
    private Integer sourceType;

    /**
     * 简化构造器
     * 仅传入章节路径、文本和来源类型，其他字段使用默认值
     * 内部调用全参构造器，将未提供的字段设为 null 或空字符串
     *
     * @param sectionPath 章节路径
     * @param text        切片文本内容
     * @param sourceType  来源类型
     */
    public ChunkCandidate(String sectionPath, String text, Integer sourceType) {
        this(sectionPath, null, null, "", null, text, sourceType);
    }
}
