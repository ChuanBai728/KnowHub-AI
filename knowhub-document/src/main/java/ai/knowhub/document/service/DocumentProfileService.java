package ai.knowhub.document.service;

import ai.knowhub.document.data.KnowHubDocumentProfile;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.support.DocumentAnalysisResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 【文档画像服务接口】
 *
 * 作用：生成和管理文档的"画像"（Profile），即文档的摘要、主题、关键词等元信息。
 * 文档画像是对文档内容的高度概括，用于知识管理和检索时的文档级理解。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"画像生成"环节
 *   - 在文档解析完成后，基于解析结果和结构节点自动生成文档画像
 *   - 画像信息用于知识域管理、文档路由、检索增强等场景
 *
 * 核心概念：
 *   - 文档画像（Document Profile）：文档的结构化摘要，包含主题、关键词、适用场景等
 *   - 结构节点（Structure Node）：文档的章节结构，用于辅助画像生成
 *   - 分析结果（Document AnalysisResult）：文档解析后的原始分析数据
 */
public interface DocumentProfileService {

    /**
     * 生成文档画像
     *
     * 功能说明：
     *   - 基于文档的解析结果和结构节点，自动生成文档画像
     *   - 通常使用 LLM（大语言模型）对文档内容进行摘要和主题提取
     *   - 生成的画像存储到数据库中，供后续检索和管理使用
     *
     * @param documentId      文档ID
     * @param analysisResult  文档的解析结果，包含提取的文本和元数据
     * @param structureNodes  文档的结构节点列表，提供章节层级信息
     * @return 生成的文档画像对象
     */
    KnowHubDocumentProfile generateProfile(Long documentId,
                                              DocumentAnalysisResult analysisResult,
                                              List<KnowHubDocumentStructureNode> structureNodes);

    /**
     * 重新生成文档画像
     *
     * 功能说明：
     *   - 当文档内容更新或画像需要刷新时调用
     *   - 会重新读取文档的解析结果和结构信息，生成新的画像
     *
     * @param documentId 文档ID
     * @return 重新生成的文档画像对象
     */
    KnowHubDocumentProfile regenerateProfile(Long documentId);

    /**
     * 批量重新生成文档画像
     *
     * 功能说明：
     *   - 对多个文档批量重新生成画像
     *   - 适用于批量数据迁移、画像策略变更等场景
     *
     * @param documentIds 文档ID集合
     * @return 重新生成的文档画像列表
     */
    List<KnowHubDocumentProfile> batchRegenerateProfiles(Collection<Long> documentIds);

    /**
     * 根据文档ID查询画像
     *
     * @param documentId 文档ID
     * @return 文档画像的 Optional 包装，如果画像不存在则返回 Optional.empty()
     *         使用 Optional 可以避免空指针异常，是 Java 8+ 推荐的空值处理方式
     */
    Optional<KnowHubDocumentProfile> getByDocumentId(Long documentId);
}
