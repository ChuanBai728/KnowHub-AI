package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识路由追踪项返回值对象（Knowledge Route Trace Item Vo）
 *
 * 【类的作用】
 * 用于展示知识路由的单条追踪记录。知识路由是 RAG 系统中的核心环节，
 * 负责将用户问题路由到最相关的知识范围和文档。本对象记录了路由过程的
 * 完整信息，包括原始问题、改写问题、候选范围/主题/文档、最终选择等。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，用于知识路由追踪页面的数据展示。
 * 帮助运维人员分析路由效果，优化路由策略。
 *
 * 【关键概念】
 * - 知识路由：将用户问题映射到最相关知识的过程
 * - topScopes：候选知识范围列表
 * - topTopics：候选主题列表
 * - topDocuments：候选文档列表
 * - selectedDocumentId：最终选中的文档ID
 * - hitSelectedDocument：是否命中了选中的文档
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRouteTraceItemVo {

    /** 追踪记录ID */
    private String id;

    /** 会话ID（Conversation ID） */
    private String conversationId;

    /** 交换ID（Exchange ID） */
    private String exchangeId;

    /** 原始用户问题（Question） */
    private String question;

    /** 改写后的问题（Rewrite Question） */
    private String rewriteQuestion;

    /** 路由模式（Mode） */
    private String mode;

    /** 候选知识范围 JSON（Top Scopes） */
    private String topScopesJson;

    /** 候选主题 JSON（Top Topics） */
    private String topTopicsJson;

    /** 候选文档 JSON（Top Documents） */
    private String topDocumentsJson;

    /** 选中的文档ID（Selected Document ID） */
    private String selectedDocumentId;

    /** 是否命中选中文档（Hit Selected Document） */
    private String hitSelectedDocument;

    /** 路由置信度（Confidence） */
    private String confidence;

    /** 路由状态（Route Status） */
    private String routeStatus;

    /** 错误信息（Error Message） */
    private String errorMsg;

    /** 创建时间（Create Time） */
    private String createTime;
}
