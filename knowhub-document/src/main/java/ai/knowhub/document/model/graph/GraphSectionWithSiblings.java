package ai.knowhub.document.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 带兄弟节点的图章节（Graph Section With Siblings）
 *
 * 【类的作用】
 * 将一个 GraphSection（章节节点）与其父节点、前后兄弟节点打包在一起，
 * 用于表示文档结构树中某个章节的"水平上下文"信息。
 *
 * 【在架构中的角色】
 * 属于 Graph RAG 的结果模型层，常见使用场景：
 * - 章节上下文定位：了解某个章节在文档中的位置（前后是什么章节）
 * - 扩展检索范围：当命中某个章节时，自动获取其兄弟章节作为补充上下文
 * - 文档导航：支持"上一节"、"下一节"的导航功能
 *
 * 【与 GraphQueryResult 的关系】
 * GraphQueryResult 包含了更完整的上下文（子节点、条目等），
 * 而 GraphSectionWithSiblings 专注于章节级别的水平上下文。
 *
 * 【设计模式】
 * - 上下文对象模式（Context Object Pattern）：封装某个节点的周围上下文信息，
 *   使调用方无需了解图遍历的细节。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphSectionWithSiblings {

    /**
     * 目标章节
     * 当前关注的核心章节节点。
     */
    private GraphSection section;

    /**
     * 父章节
     * 目标章节的父节点，提供上一级上下文。
     */
    private GraphSection parent;

    /**
     * 前一个兄弟章节
     * 与目标章节同级的前一个章节，
     * 用于理解目标章节在文档中的前文内容。
     */
    private GraphSection previousSibling;

    /**
     * 后一个兄弟章节
     * 与目标章节同级的后一个章节，
     * 用于理解目标章节在文档中的后文内容。
     */
    private GraphSection nextSibling;
}
