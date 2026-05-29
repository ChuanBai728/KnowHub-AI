package ai.knowhub.document.model.route;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 文档路由候选（Document Route Candidate）
 *
 * 【类的作用】
 * 表示在知识路由决策过程中，一个被候选匹配的文档。
 * 当用户提问时，路由引擎会从知识库中筛选出可能相关的文档，
 * 每个候选文档携带相关性评分和匹配原因。
 *
 * 【在架构中的角色】
 * 属于 RAG 流程中"路由决策"阶段的结果模型：
 * 1. 用户提问进入系统后，路由引擎分析问题意图
 * 2. 引擎从 Elasticsearch 的路由索引中检索相关文档
 * 3. 每个匹配的文档封装为一个 DocumentRouteCandidate
 * 4. 最终由 KnowledgeRouteDecision 综合所有候选，选出最佳文档
 *
 * 【评分机制】
 * score 字段记录该候选文档的相关性评分（通常由语义相似度计算得出），
 * 评分越高表示文档与用户问题越相关。
 * reason 字段记录匹配原因，用于调试和日志记录。
 *
 * 【与 KnowledgeDocumentDescriptor 的关系】
 * DocumentRouteCandidate 包含了 KnowledgeDocumentDescriptor 的所有字段，
 * 并额外增加了 score 和 reason 字段，是路由阶段的扩展模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRouteCandidate {

    /**
     * 文档 ID
     * 候选文档的唯一标识，对应数据库中的文档主键。
     */
    private String documentId;

    /**
     * 文档名称
     * 候选文档的名称，用于结果展示和日志记录。
     */
    private String documentName;

    /**
     * 最近一次索引任务 ID
     * 该文档最近一次成功完成索引构建的任务 ID。
     */
    private String lastIndexTaskId;

    /**
     * 知识范围编码
     * 候选文档所属的知识域编码。
     */
    private String knowledgeScopeCode;

    /**
     * 知识范围名称
     * knowledgeScopeCode 的人类可读形式。
     */
    private String knowledgeScopeName;

    /**
     * 业务分类
     * 候选文档的业务分类标签。
     */
    private String businessCategory;

    /**
     * 文档标签
     * 候选文档的关键词标签。
     */
    private String documentTags;

    /**
     * 相关性评分
     * 路由引擎计算的文档与用户问题的相关性评分，
     * 使用 BigDecimal 保证精度，评分越高越相关。
     * 通常基于语义向量相似度（cosine similarity）计算。
     */
    private BigDecimal score;

    /**
     * 匹配原因
     * 路由引擎给出的匹配理由，例如"文档名称语义匹配"、
     * "业务类别匹配"等，用于调试和可解释性。
     */
    private String reason;
}
