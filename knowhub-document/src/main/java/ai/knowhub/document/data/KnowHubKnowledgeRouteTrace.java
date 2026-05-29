package ai.knowhub.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ai.knowhub.database.data.BaseTableData;

import java.math.BigDecimal;

/**
 * 知识路由追踪实体类。
 *
 * 对应数据库表 knowhub_knowledge_route_trace，记录每次用户提问时系统的知识路由决策过程。
 *
 * 「知识路由」是 RAG 系统中的关键环节，它决定了用户的问题应该在哪个知识范围内检索。
 * 路由追踪记录了完整的决策过程，用于：
 * 
 *   <b>准确性评估</b>：通过分析路由结果，评估路由策略的准确率。
 *   <b>问题诊断</b>：当检索结果不理想时，通过追踪记录定位路由环节的问题。
 *   <b>策略优化</b>：基于追踪数据，持续优化路由策略和知识体系配置。
 *   <b>统计分析</b>：统计各知识范围和主题的访问频率，了解用户的知识需求分布。
 * 路由决策流程：
 * 
 *   接收用户原始问题。
 *   对问题进行改写（Query Rewrite），优化检索效果。
 *   通过知识路由索引，匹配最相关的知识范围和主题。
 *   选择最相关的文档进行检索。
 *   记录整个决策过程到追踪表。
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowhub_knowledge_route_trace")
@EqualsAndHashCode(callSuper = true)
public class KnowHubKnowledgeRouteTrace extends BaseTableData {

    /**
     * 路由追踪主键 ID。
     * 使用百度 UID生成的全局唯一 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 会话 ID。
     * 标识用户的一次对话会话，同一会话中的多次提问共享同一个会话 ID。
     */
    private String conversationId;

    /**
     * 交换 ID。
     * 标识会话中的一次问答交换（用户提问 + 系统回答）。
     */
    private Long exchangeId;

    /**
     * 用户原始问题。
     * 用户输入的原始问题文本。
     */
    private String question;

    /**
     * 改写后的问题。
     * 系统对用户原始问题进行改写后的文本，改写的目的是优化检索效果。
     * 例如，补充上下文、消除歧义、扩展关键词等。
     */
    private String rewriteQuestion;

    /**
     * 路由模式。
     * 标识使用的路由方式，如 "scope"（按知识范围路由）、"topic"（按主题路由）等。
     */
    private String mode;

    /**
     * 命中的知识范围 TOP-N 结果 JSON。
     * 存储路由匹配到的最相关的知识范围列表，JSON 数组格式。
     */
    private String topScopesJson;

    /**
     * 命中的知识主题 TOP-N 结果 JSON。
     * 存储路由匹配到的最相关的知识主题列表，JSON 数组格式。
     */
    private String topTopicsJson;

    /**
     * 命中的文档 TOP-N 结果 JSON。
     * 存储路由匹配到的最相关的文档列表，JSON 数组格式。
     */
    private String topDocumentsJson;

    /**
     * 最终选择的文档 ID。
     * 路由决策最终选定的文档，后续的 RAG 检索在此文档中进行。
     */
    private Long selectedDocumentId;

    /**
     * 是否命中选定文档。
     * 标识最终选择的文档是否在 TOP-N 结果中，用于评估路由准确性。
     * 1=命中，0=未命中。
     */
    private Integer hitSelectedDocument;

    /**
     * 路由置信度。
     * 系统对此次路由决策的置信度评分（0-1 之间），值越高表示越确定。
     */
    private BigDecimal confidence;

    /**
     * 路由状态枚举值。
     * 标识此次路由的状态：成功、失败、超时等。
     */
    private Integer routeStatus;

    /**
     * 错误信息。
     * 当路由失败时，记录失败原因。
     */
    private String errorMsg;
}
