package ai.knowhub.document.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 带子章节的图章节（Graph Section With Children）
 *
 * 【类的作用】
 * 将一个 GraphSection（章节节点）与其直接子章节列表打包在一起，
 * 用于表示文档结构树中的一个"章节及其下一级结构"。
 *
 * 【在架构中的角色】
 * 属于 Graph RAG 的结果模型层，常见使用场景：
 * - 文档目录树展示：获取某个章节及其所有子章节，渲染前端目录
 * - 图遍历结果：从某个章节向下遍历一层，获取其子结构
 * - 上下文组装：在 RAG 结果中，展示目标章节的内部结构概览
 *
 * 【与 GraphQueryResult 的关系】
 * GraphQueryResult 包含了更完整的上下文（父节点、兄弟节点等），
 * 而 GraphSectionWithChildren 更轻量，只关注"章节 -> 子章节"这一层级关系。
 *
 * 【设计模式】
 * - 聚合模式（Aggregate Pattern）：将章节与其子章节聚合为一个对象，
 *   便于整体传递和处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphSectionWithChildren {

    /**
     * 父章节节点
     * 作为容器的章节节点，是子章节的父节点。
     */
    private GraphSection section;

    /**
     * 子章节列表
     * 该章节下的所有直接子章节节点。
     * 使用 @Builder.Default 设置默认空列表，避免空指针。
     */
    @Builder.Default
    private List<GraphSection> children = new ArrayList<>();
}
