package ai.knowhub.document.service;

import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.support.DocumentStructureNodeCandidate;

import java.util.List;
import java.util.Map;

/**
 * 【文档结构节点服务接口】
 *
 * 作用：管理文档的结构节点（Structure Node），即文档的层级结构数据（章、节、段等）。
 * 结构节点是文档解析的核心产出之一，用于支持章节导航、图投影和章节级检索。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"结构管理"层
 *   - 负责结构节点的增删改查操作
 *   - 为导航索引、图投影等下游服务提供结构数据
 *
 * 核心概念：
 *   - 结构节点（Structure Node）：文档中的结构化单元，如章、节、段落
 *   - 节点候选（Node Candidate）：解析阶段产生的候选节点，需要经过处理后才能成为正式节点
 *   - 节点映射（Node Map）：以节点ID为键的映射结构，便于快速查找
 */
public interface DocumentStructureNodeService {

    /**
     * 替换文档的所有结构节点
     *
     * 功能说明：
     *   - 清除文档的旧结构节点
     *   - 将新的候选节点列表转化为正式节点并保存
     *   - 通常在文档解析完成后调用
     *   - 原子操作：要么全部替换成功，要么全部回滚
     *
     * @param documentId  文档ID
     * @param parseTaskId 解析任务ID
     * @param candidates  节点候选列表，解析阶段产生的结构节点数据
     * @return 替换后的正式结构节点列表
     */
    List<KnowHubDocumentStructureNode> replaceDocumentNodes(Long documentId,
                                                               Long parseTaskId,
                                                               List<DocumentStructureNodeCandidate> candidates);

    /**
     * 列出文档的所有结构节点
     *
     * @param documentId  文档ID
     * @param parseTaskId 解析任务ID
     * @return 结构节点列表，按层级顺序排列
     */
    List<KnowHubDocumentStructureNode> listDocumentNodes(Long documentId, Long parseTaskId);

    /**
     * 获取文档结构节点的映射表
     *
     * 功能说明：
     *   - 返回以节点ID为键的 Map 结构
     *   - 便于通过节点ID快速查找节点对象
     *   - 避免重复查询数据库
     *
     * @param documentId  文档ID
     * @param parseTaskId 解析任务ID
     * @return 节点ID到节点对象的映射
     */
    Map<Long, KnowHubDocumentStructureNode> nodeMap(Long documentId, Long parseTaskId);

    /**
     * 删除指定文档的所有结构节点
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);
}
