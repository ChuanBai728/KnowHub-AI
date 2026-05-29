package ai.knowhub.document.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 带上下文的图条目（Graph Item With Context）
 *
 * 【类的作用】
 * 将一个 GraphItem（条目节点）与其所属的 GraphSection（章节节点）
 * 以及同级兄弟条目打包在一起，形成一个完整的上下文信息包。
 *
 * 【在架构中的角色】
 * 属于 Graph RAG 的结果模型层，用于向大模型提供丰富的上下文：
 * - 当检索命中某个条目时，不仅返回该条目本身，
 *   还返回它所属的章节信息和同级兄弟条目，
 *   让大模型能够理解该条目在文档结构中的位置和上下文。
 *
 * 【使用场景】
 * 在 RAG 结果组装阶段，系统会将检索到的 GraphItem 包装为 GraphItemWithContext，
 * 然后将 section 标题 + siblingItems 上下文 + item 本身一起拼接为 prompt，
 * 传递给大模型进行回答生成。
 *
 * 【设计模式】
 * - 装饰器模式（Decorator Pattern）：在不修改 GraphItem 的情况下，
 *   为其附加额外的上下文信息（section 和 siblingItems）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphItemWithContext {

    /**
     * 所属章节节点
     * 该条目所属的 GraphSection，提供章节级别的上下文信息，
     * 例如章节标题、章节路径等。
     */
    private GraphSection section;

    /**
     * 目标条目节点
     * 检索命中的核心 GraphItem，是用户问题最相关的内容片段。
     */
    private GraphItem item;

    /**
     * 同级兄弟条目列表
     * 与目标条目同一章节下的其他条目，
     * 用于提供上下文信息，帮助大模型理解该条目的前后文关系。
     * 使用 @Builder.Default 设置默认空列表，避免空指针。
     */
    @Builder.Default
    private List<GraphItem> siblingItems = new ArrayList<>();
}
