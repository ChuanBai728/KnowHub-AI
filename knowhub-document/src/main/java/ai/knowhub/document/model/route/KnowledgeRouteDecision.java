package ai.knowhub.document.model.route;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识路由决策（Knowledge Route Decision）
 *
 * 【类的作用】
 * 封装一次完整的知识路由决策结果，包含三个层级的候选列表：
 * 范围（Scope）、主题（Topic）、文档（Document），
 * 以及整体置信度和路由状态。
 *
 * 【在架构中的角色】
 * 属于 RAG 流程中"路由决策"阶段的核心结果模型：
 * 1. 用户提问进入系统后，路由引擎分析问题意图
 * 2. 引擎从三个层级分别筛选候选：范围 -> 主题 -> 文档
 * 3. 所有候选结果封装到此对象中
 * 4. 下游检索引擎根据此决策结果，限定检索范围
 *
 * 【三层路由策略】
 * - Scope（范围）：最粗粒度，决定在哪个知识域中检索
 * - Topic（主题）：中等粒度，进一步缩小到特定主题
 * - Document（文档）：最细粒度，精确到具体文档
 *
 * 这种层级路由设计的好处：
 * 1. 减少检索范围，提高检索速度
 * 2. 提高检索精准度，减少无关内容干扰
 * 3. 支持渐进式缩小范围，即使粗粒度匹配失败也能降级处理
 *
 * 【routeStatus 字段说明】
 * - "SUCCESS"：路由成功，找到了匹配的知识
 * - "FALLBACK"：路由降级，未能精确匹配，使用兜底策略
 * - "EMPTY"：路由为空，未找到任何匹配的知识
 *
 * 【设计模式】
 * - 决策对象模式（Decision Object Pattern）：将路由决策的完整结果封装为一个对象，
 *   包含决策内容、置信度、状态和原因，便于下游消费和调试。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRouteDecision {

    /**
     * 范围候选列表
     * 路由引擎匹配到的候选知识范围，
     * 例如"人力资源"、"财务管理"等。
     * 列表按相关性评分降序排列。
     */
    private List<ScopeRouteCandidate> scopes = new ArrayList<>();

    /**
     * 主题候选列表
     * 路由引擎匹配到的候选主题，
     * 例如"考勤管理"、"报销流程"等。
     * 列表按相关性评分降序排列。
     */
    private List<TopicRouteCandidate> topics = new ArrayList<>();

    /**
     * 文档候选列表
     * 路由引擎匹配到的候选文档，
     * 例如"2024年考勤制度.docx"、"报销指南.pdf"等。
     * 列表按相关性评分降序排列。
     */
    private List<DocumentRouteCandidate> documents = new ArrayList<>();

    /**
     * 整体置信度
     * 路由决策的整体置信度评分，范围 0~1：
     * - 1.0：完全确信路由结果正确
     * - 0.5：中等置信度
     * - 0.0：完全不确定
     * 默认为 BigDecimal.ZERO，表示尚未计算。
     */
    private BigDecimal confidence = BigDecimal.ZERO;

    /**
     * 路由状态
     * 标识本次路由决策的结果状态：
     * - "SUCCESS"：路由成功
     * - "FALLBACK"：路由降级
     * - "EMPTY"：路由为空
     * 默认为 "SUCCESS"。
     */
    private String routeStatus = "SUCCESS";

    /**
     * 路由原因
     * 路由引擎给出的决策理由，用于日志记录和调试。
     * 默认为空字符串。
     */
    private String reason = "";

    /**
     * 获取评分最高的文档候选
     *
     * 【使用场景】
     * 当只需要一个最佳匹配文档时调用此方法，
     * 避免调用方手动判断列表是否为空。
     *
     * @return 评分最高的 DocumentRouteCandidate，如果没有候选则返回 null
     */
    public DocumentRouteCandidate topDocument() {
        return documents == null || documents.isEmpty() ? null : documents.get(0);
    }
}
