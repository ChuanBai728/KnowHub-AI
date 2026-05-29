package ai.knowhub.document.service;

import ai.knowhub.document.model.route.KnowledgeRouteDecision;

/**
 * 【知识路由服务接口】
 *
 * 作用：实现智能知识路由功能，根据用户问题自动选择最相关的文档进行检索。
 * 是 RAG 流水线中"路由决策"的核心组件，决定将用户问题路由到哪些文档。
 *
 * 架构角色：
 *   - 属于 RAG 流水线的"路由决策"层
 *   - 在检索之前执行，确定检索的目标文档范围
 *   - 支持自动路由和影子路由两种模式
 *
 * 核心概念：
 *   - 知识路由（Knowledge Routing）：根据问题内容智能选择相关文档的过程
 *   - 路由决策（Route Decision）：路由的结果，包含选中的文档和置信度
 *   - 问题改写（Question Rewrite）：对用户原始问题进行改写，提取关键信息用于路由
 *   - 自动路由（Auto Route）：系统自动做出的路由决策，直接影响检索结果
 *   - 影子路由（Shadow Route）：后台运行的路由，不影响实际结果，用于收集数据和优化路由策略
 */
public interface KnowledgeRouteService {

    /**
     * 执行知识路由决策
     *
     * 功能说明：
     *   - 接收用户的原始问题和改写后的问题
     *   - 通过路由索引匹配最相关的文档
     *   - 返回路由决策结果，包含选中的文档和路由依据
     *   - 此方法用于正式的路由决策，直接影响检索结果
     *
     * @param question       用户的原始问题
     * @param rewriteQuestion 经过改写/优化后的问题（提取关键词、消除歧义等）
     * @return 路由决策结果，包含选中的文档列表和路由元数据
     */
    KnowledgeRouteDecision route(String question, String rewriteQuestion);

    /**
     * 记录影子路由
     *
     * 功能说明：
     *   - 影子路由是一种"旁路"路由机制，不影响实际的检索结果
     *   - 系统在执行正式路由的同时，后台运行影子路由
     *   - 影子路由的结果被记录下来，用于后续分析和路由策略优化
     *   - 例如：正式路由用了关键词匹配，影子路由用语义匹配，对比两者效果
     *
     * @param conversationId     会话ID，关联到用户的对话会话
     * @param exchangeId         交互轮次ID，标识对话中的某一轮交互
     * @param selectedDocumentId 当前已选中的文档ID（正式路由的结果）
     * @param question           用户的原始问题
     * @param rewriteQuestion    改写后的问题
     */
    void recordShadowRoute(String conversationId,
                           long exchangeId,
                           Long selectedDocumentId,
                           String question,
                           String rewriteQuestion);

    /**
     * 记录自动路由决策
     *
     * 功能说明：
     *   - 记录系统自动做出的路由决策
     *   - 用于路由效果的追踪和分析
     *   - 记录数据可用于优化路由算法和策略
     *
     * @param conversationId  会话ID
     * @param exchangeId      交互轮次ID
     * @param question        用户的原始问题
     * @param rewriteQuestion 改写后的问题
     * @param decision        路由决策结果
     */
    void recordAutoRoute(String conversationId,
                         long exchangeId,
                         String question,
                         String rewriteQuestion,
                         KnowledgeRouteDecision decision);
}
