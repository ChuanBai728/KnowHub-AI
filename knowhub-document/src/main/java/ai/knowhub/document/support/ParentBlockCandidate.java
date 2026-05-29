package ai.knowhub.document.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 父级文本块候选（Parent Block Candidate）
 *
 * 【类的作用】
 * 表示文档切片流程中的一个父级文本块候选。父块是切片的上一级组织单位，
 * 一个父块可以包含多个子切片（ChunkCandidate）。父块保留了更大的上下文，
 * 有助于提高检索时的语义完整性。
 *
 * 【在架构中的角色】
 * 属于文档切片策略的中间产物。文档经过结构分析后，首先被划分为父块，
 * 然后再将父块拆分为更小的切片。这种两级切片策略（Parent → Chunk）
 * 有助于在检索时提供更完整的上下文。
 *
 * 【与 ChunkCandidate 的关系】
 * ParentBlockCandidate 是 ChunkCandidate 的父级容器，
 * 通过 childChunks 字段关联其包含的所有子切片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParentBlockCandidate {

    /**
     * 章节路径（Section Path）
     * 父块在文档结构树中的路径，如 "第一章 > 第一节"
     */
    private String sectionPath;

    /**
     * 结构节点ID（Structure Node ID）
     * 关联到文档结构树中的具体节点
     */
    private Long structureNodeId;

    /**
     * 结构节点类型（Structure Node Type）
     * 标识节点的类型
     */
    private Integer structureNodeType;

    /**
     * 规范路径（Canonical Path）
     * 节点的标准化路径
     */
    private String canonicalPath;

    /**
     * 项目索引（Item Index）
     * 当父块来源于列表项时，记录其在列表中的序号
     */
    private Integer itemIndex;

    /**
     * 父块文本内容（Text）
     * 父块的完整文本，包含所有子切片的文本
     */
    private String text;

    /**
     * 来源类型（Source Type）
     * 标识父块的来源方式
     */
    private Integer sourceType;

    /**
     * 子切片列表（Child Chunks）
     * 该父块包含的所有子切片
     */
    private List<ChunkCandidate> childChunks = new ArrayList<>();

    /**
     * 简化构造器
     * 仅传入章节路径、文本、来源类型和子切片列表
     *
     * @param sectionPath 章节路径
     * @param text        父块文本
     * @param sourceType  来源类型
     * @param childChunks 子切片列表
     */
    public ParentBlockCandidate(String sectionPath,
                                String text,
                                Integer sourceType,
                                List<ChunkCandidate> childChunks) {
        this(sectionPath, null, null, "", null, text, sourceType, childChunks);
    }
}
