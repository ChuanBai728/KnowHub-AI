package ai.knowhub.document.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 图查询结果（Graph Query Result）
 *
 * 【类的作用】
 * 封装一次 Neo4j 图查询的完整结果，包含目标章节及其关联的
 * 上下文信息（父节点、兄弟节点、子节点、匹配的条目等）。
 *
 * 【在架构中的角色】
 * 属于 Graph RAG 的查询结果模型层：
 * 1. 检索引擎在 Neo4j 中执行图遍历查询
 * 2. 查询结果包含目标节点及其邻居节点
 * 3. 此类将所有相关信息封装为一个结构化对象
 * 4. 下游服务根据此结果组装 prompt 传递给大模型
 *
 * 【图遍历策略】
 * 此类的设计体现了 Graph RAG 的核心思想——
 * 不仅返回匹配的节点，还返回其周围的上下文节点：
 * - targetSection：目标章节（匹配查询的章节）
 * - parentSection：父章节（向上遍历）
 * - previousSibling / nextSibling：兄弟章节（横向遍历）
 * - children：子章节（向下遍历）
 * - matchedItems / allItems：条目级别的匹配结果
 *
 * 【设计模式】
 * - 结果对象模式（Result Object Pattern）：将复杂的查询结果封装为一个对象，
 *   避免调用方需要理解底层查询逻辑。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphQueryResult {

    /**
     * 目标章节
     * 与查询条件最匹配的 GraphSection 节点，
     * 是本次图查询的核心结果。
     */
    private GraphSection targetSection;

    /**
     * 父章节
     * 目标章节的父节点，提供上一级上下文。
     * 例如目标章节是"2.1 节"，则父章节是"第二章"。
     */
    private GraphSection parentSection;

    /**
     * 前一个兄弟章节
     * 与目标章节同级的前一个章节节点，
     * 用于理解目标章节在文档中的前后文关系。
     */
    private GraphSection previousSibling;

    /**
     * 后一个兄弟章节
     * 与目标章节同级的后一个章节节点。
     */
    private GraphSection nextSibling;

    /**
     * 目标条目
     * 如果查询直接匹配到了某个条目（而非章节），
     * 此字段记录该条目节点。
     */
    private GraphItem targetItem;

    /**
     * 子章节列表
     * 目标章节下的所有直接子章节，
     * 用于理解目标章节的内部结构。
     */
    @Builder.Default
    private List<GraphSection> children = new ArrayList<>();

    /**
     * 匹配的条目列表
     * 在目标章节下，与查询条件匹配的具体条目，
     * 是最终用于生成回答的核心内容。
     */
    @Builder.Default
    private List<GraphItem> matchedItems = new ArrayList<>();

    /**
     * 所有条目列表
     * 目标章节下的所有条目（包括匹配和未匹配的），
     * 用于提供完整的上下文信息，帮助大模型理解章节全貌。
     */
    @Builder.Default
    private List<GraphItem> allItems = new ArrayList<>();
}
