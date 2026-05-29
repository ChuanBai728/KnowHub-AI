package ai.knowhub.document.service;

import ai.knowhub.document.model.graph.GraphItem;
import ai.knowhub.document.model.graph.GraphSection;

import java.util.List;

/**
 * 【文档结构图查询服务接口】
 *
 * 作用：提供基于图数据库的文档结构查询能力，支持对文档章节和内容项的灵活导航和检索。
 * 是 Graph RAG 的查询层，允许系统以图的方式遍历和搜索文档结构。
 *
 * 架构角色：
 *   - 属于 RAG 流水线的"图查询"模块
 *   - 基于 Neo4j 图数据库存储的文档结构进行查询
 *   - 支持按 ID、编码、标题、路径等多种方式定位章节
 *   - 支持章节间的导航（父子、兄弟、前后）
 *
 * 核心概念：
 *   - GraphSection（图章节）：图中的章节节点，包含标题、路径、内容等
 *   - GraphItem（图内容项）：章节下的具体内容单元（段落、列表项等）
 *   - 规范路径（Canonical Path）：章节在图中的唯一路径标识
 *   - 主题/维度（Topic/Facet）：用于语义匹配的维度参数
 *
 * 设计模式：Gateway 模式，封装图数据库的查询操作
 */
public interface DocumentStructureGraphService {

    /**
     * 检查指定文档的图数据是否可用
     *
     * @param documentId 文档ID
     * @return true 表示图数据可用，false 表示不可用
     */
    default boolean isGraphAvailable(Long documentId) {
        return documentId != null;
    }

    /**
     * 根据节点ID查找章节
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @return 匹配的章节，不存在时返回 null
     */
    GraphSection findSectionById(Long documentId, Long sectionNodeId);

    /**
     * 根据节点编码查找章节
     *
     * @param documentId 文档ID
     * @param nodeCode   节点编码（如 "1.2.3"）
     * @return 匹配的章节
     */
    GraphSection findSectionByCode(Long documentId, String nodeCode);

    /**
     * 根据标题查找章节
     *
     * @param documentId 文档ID
     * @param title      章节标题
     * @return 匹配的章节
     */
    GraphSection findSectionByTitle(Long documentId, String title);

    /**
     * 根据规范路径查找章节
     *
     * @param documentId    文档ID
     * @param canonicalPath 规范路径（如 "/chapter1/section1.2"）
     * @return 匹配的章节
     */
    GraphSection findSectionByCanonicalPath(Long documentId, String canonicalPath);

    /**
     * 根据主题和维度查找最佳匹配章节
     *
     * 功能说明：
     *   - 使用语义匹配在文档中定位最相关的章节
     *   - 用于 RAG 流水线中的章节级检索
     *
     * @param documentId 文档ID
     * @param topic      主题关键词
     * @param facet      维度/方面
     * @return 最佳匹配的章节
     */
    GraphSection findBestSection(Long documentId, String topic, String facet);

    /**
     * 列出文档的所有顶级章节
     *
     * @param documentId 文档ID
     * @return 顶级章节列表
     */
    List<GraphSection> listSections(Long documentId);

    /**
     * 列出指定章节的所有子章节
     *
     * @param documentId    文档ID
     * @param sectionNodeId 父章节节点ID
     * @return 子章节列表
     */
    List<GraphSection> listChildren(Long documentId, Long sectionNodeId);

    /**
     * 获取指定章节的父章节
     *
     * @param documentId    文档ID
     * @param sectionNodeId 当前章节节点ID
     * @return 父章节，如果是顶级章节则返回 null
     */
    GraphSection parentSection(Long documentId, Long sectionNodeId);

    /**
     * 获取指定章节的前一个兄弟章节
     *
     * @param documentId    文档ID
     * @param sectionNodeId 当前章节节点ID
     * @return 前一个兄弟章节，如果没有则返回 null
     */
    GraphSection previousSibling(Long documentId, Long sectionNodeId);

    /**
     * 获取指定章节的后一个兄弟章节
     *
     * @param documentId    文档ID
     * @param sectionNodeId 当前章节节点ID
     * @return 后一个兄弟章节，如果没有则返回 null
     */
    GraphSection nextSibling(Long documentId, Long sectionNodeId);

    /**
     * 根据索引查找章节下的内容项
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @param itemIndex     内容项索引
     * @return 匹配的内容项
     */
    GraphItem findItemByIndex(Long documentId, Long sectionNodeId, Integer itemIndex);

    /**
     * 列出章节下的所有内容项
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @return 内容项列表
     */
    List<GraphItem> listItems(Long documentId, Long sectionNodeId);

    /**
     * 在指定章节内按关键词搜索内容项
     *
     * @param documentId    文档ID
     * @param sectionNodeId 章节节点ID
     * @param keyword       搜索关键词
     * @return 匹配的内容项列表
     */
    List<GraphItem> searchItemsInSection(Long documentId, Long sectionNodeId, String keyword);
}
