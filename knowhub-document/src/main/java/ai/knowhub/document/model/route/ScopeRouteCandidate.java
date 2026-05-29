package ai.knowhub.document.model.route;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 范围路由候选（Scope Route Candidate）
 *
 * 【类的作用】
 * 表示在知识路由决策过程中，一个被候选匹配的知识范围（Scope）。
 * 知识范围是路由层级中最粗粒度的分类，例如"人力资源"、"财务管理"等。
 *
 * 【在架构中的角色】
 * 属于 RAG 流程中"路由决策"阶段的范围层模型：
 * - 三层路由的第一层：Scope -> Topic -> Document
 * - 路由引擎首先确定用户问题属于哪个知识范围
 * - 然后在该范围内进一步匹配主题和文档
 *
 * 【使用场景】
 * 当用户的问题涉及多个知识域时，路由引擎会返回多个范围候选，
 * 每个候选携带评分和匹配原因，供下游选择最相关的范围。
 *
 * 【与 KnowledgeRouteDecision 的关系】
 * ScopeRouteCandidate 是 KnowledgeRouteDecision 中 scopes 列表的元素类型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScopeRouteCandidate {

    /**
     * 范围编码
     * 知识范围的唯一编码标识，例如"HR"（人力资源）、"FIN"（财务）。
     */
    private String scopeCode;

    /**
     * 范围名称
     * scopeCode 的人类可读形式，例如"人力资源"、"财务管理"。
     */
    private String scopeName;

    /**
     * 相关性评分
     * 路由引擎计算的该范围与用户问题的相关性评分，
     * 使用 BigDecimal 保证精度，评分越高越相关。
     */
    private BigDecimal score;

    /**
     * 匹配原因
     * 路由引擎给出的匹配理由，例如"问题关键词匹配范围描述"。
     */
    private String reason;
}
