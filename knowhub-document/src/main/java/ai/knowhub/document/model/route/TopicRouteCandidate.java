package ai.knowhub.document.model.route;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 主题路由候选（Topic Route Candidate）
 *
 * 【类的作用】
 * 表示在知识路由决策过程中，一个被候选匹配的知识主题（Topic）。
 * 主题是路由层级中中等粒度的分类，是知识范围下的子分类。
 *
 * 【在架构中的角色】
 * 属于 RAG 流程中"路由决策"阶段的主题层模型：
 * - 三层路由的第二层：Scope -> Topic -> Document
 * - 在确定知识范围后，路由引擎进一步匹配该范围下的主题
 * - 主题比范围更细粒度，例如"考勤管理"是"人力资源"下的主题
 *
 * 【层级关系示例】
 * - 范围（Scope）：人力资源
 *   - 主题（Topic）：考勤管理
 *     - 文档（Document）：2024年考勤制度.docx
 *   - 主题（Topic）：薪酬福利
 *     - 文档（Document）：薪酬方案.pdf
 *
 * 【与 KnowledgeRouteDecision 的关系】
 * TopicRouteCandidate 是 KnowledgeRouteDecision 中 topics 列表的元素类型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicRouteCandidate {

    /**
     * 主题编码
     * 知识主题的唯一编码标识，例如"ATTENDANCE"（考勤）。
     */
    private String topicCode;

    /**
     * 主题名称
     * topicCode 的人类可读形式，例如"考勤管理"。
     */
    private String topicName;

    /**
     * 所属范围编码
     * 该主题所属的知识范围编码，
     * 用于将主题与范围关联起来。
     */
    private String scopeCode;

    /**
     * 相关性评分
     * 路由引擎计算的该主题与用户问题的相关性评分，
     * 使用 BigDecimal 保证精度，评分越高越相关。
     */
    private BigDecimal score;

    /**
     * 匹配原因
     * 路由引擎给出的匹配理由，用于调试和可解释性。
     */
    private String reason;
}
