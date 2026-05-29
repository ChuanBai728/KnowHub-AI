package ai.knowhub.document.service;

import ai.knowhub.document.data.KnowHubDocument;
import ai.knowhub.document.data.KnowHubDocumentStrategyPlan;
import ai.knowhub.document.data.KnowHubDocumentStrategyStep;
import ai.knowhub.document.support.DocumentAnalysisResult;
import ai.knowhub.document.support.DocumentStrategyPlanDraft;
import ai.knowhub.document.support.ParentBlockCandidate;

import java.util.List;

/**
 * 【文档分块策略服务接口】
 *
 * 作用：管理文档的分块策略（Chunking Strategy），决定如何将长文档切分为适合检索的文本块。
 * 这是 RAG 流水线中非常关键的环节，分块质量直接影响检索效果。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"策略规划"层
 *   - 在文档解析完成后、索引构建前执行
 *   - 负责分析文档特征并推荐最优的分块方案
 *   - 支持用户自定义修改分块策略
 *
 * 核心概念：
 *   - 分块策略计划（Strategy Plan）：描述如何分块的配置方案
 *   - 分块策略步骤（Strategy Step）：策略中的具体步骤，如按标题分块、按段落分块等
 *   - 父块（Parent Block）：较大的文本块，提供完整上下文
 *   - 子块（Child Block）：较小的文本块，用于精确检索
 *   - 父子分块策略：同时维护父子两级块，检索时命中子块但返回父块以提供更多上下文
 *
 * 设计模式：策略模式（Strategy Pattern）
 *   - 不同的文档类型和内容特征可能需要不同的分块策略
 *   - 系统根据文档分析结果自动推荐策略，用户可以调整
 */
public interface DocumentStrategyService {

    /**
     * 推荐分块策略
     *
     * 功能说明：
     *   - 根据文档的解析结果（文本长度、结构复杂度等）自动推荐分块策略
     *   - 分析文档的特征，选择最合适的分块方式和参数
     *   - 返回策略计划的草稿，供用户确认或修改
     *
     * @param document      文档实体，包含文档的基本信息
     * @param analysisResult 文档的解析结果，包含文本内容和结构信息
     * @return 推荐的分块策略计划草稿
     */
    DocumentStrategyPlanDraft recommendStrategy(KnowHubDocument document, DocumentAnalysisResult analysisResult);

    /**
     * 规范化分块策略步骤
     *
     * 功能说明：
     *   - 将用户自定义的策略类型与基础策略合并
     *   - 确保策略步骤的完整性和一致性
     *   - 处理父子策略类型之间的关联关系
     *
     * @param basePlan                基础策略计划
     * @param baseSteps               基础策略步骤列表
     * @param requestParentStrategyTypes 用户请求的父块策略类型列表
     * @param requestChildStrategyTypes  用户请求的子块策略类型列表
     * @param documentId              文档ID
     * @return 规范化后的策略步骤列表
     */
    List<KnowHubDocumentStrategyStep> normalizeSteps(KnowHubDocumentStrategyPlan basePlan,
                                                        List<KnowHubDocumentStrategyStep> baseSteps,
                                                        List<Integer> requestParentStrategyTypes,
                                                        List<Integer> requestChildStrategyTypes,
                                                        Long documentId);

    /**
     * 构建父块候选列表
     *
     * 功能说明：
     *   - 根据确认的策略和步骤，将文档解析文本切分为父块
     *   - 每个父块包含较大的文本片段，提供完整的上下文
     *   - 父块会进一步细分为子块用于精确检索
     *
     * @param document   文档实体
     * @param plan       确认的策略计划
     * @param steps      策略步骤列表
     * @param parsedText 文档的解析后纯文本
     * @return 父块候选列表，每个候选包含文本内容和元数据
     */
    List<ParentBlockCandidate> buildParentBlocks(KnowHubDocument document,
                                                 KnowHubDocumentStrategyPlan plan,
                                                 List<KnowHubDocumentStrategyStep> steps,
                                                 String parsedText);
}
