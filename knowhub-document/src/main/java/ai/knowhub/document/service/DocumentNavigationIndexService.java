package ai.knowhub.document.service;

import ai.knowhub.document.data.KnowHubDocumentStructureNode;

import java.util.List;

/**
 * 【文档导航索引服务接口】
 *
 * 作用：管理文档的导航索引，支持基于文档结构的章节导航和定位检索。
 * 将文档的结构节点（章节、段落等）建立索引，使系统能够快速定位到文档中的特定章节。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"索引构建"层
 *   - 负责将文档的层级结构（树形节点）转化为可检索的索引
 *   - 支持 RAG 流水线中的"章节级检索"，帮助精确定位相关内容所在的章节
 *
 * 核心概念：
 *   - 导航节点（Navigation Node）：文档中的结构化节点，如章、节、段
 *   - 章节路径（Section Path）：节点在文档树中的路径，如 "第1章 > 1.1节 > 段落1"
 *   - 规范路径（Canonical Path）：标准化的路径表示，用于精确匹配
 *
 * 设计模式：Gateway 模式，作为访问导航索引存储的统一入口
 */
public interface DocumentNavigationIndexService {

    /**
     * 重新索引文档的所有导航节点
     *
     * 功能说明：
     *   - 清除文档的旧索引数据
     *   - 将新的结构节点列表写入导航索引
     *   - 通常在文档解析完成后调用，建立章节导航索引
     *   - 索引数据用于支持基于章节的检索和导航
     *
     * @param documentId   文档ID
     * @param parseTaskId  解析任务ID，关联到具体的解析执行记录
     * @param nodes        文档的结构节点列表，包含章节标题、层级、路径等信息
     */
    void reindexDocumentNodes(Long documentId, Long parseTaskId, List<KnowHubDocumentStructureNode> nodes);

    /**
     * 删除指定文档的所有导航索引
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 在文档的章节索引中搜索最匹配的章节
     *
     * 功能说明：
     *   - 根据主题、维度、信息需求、问题等多个维度进行章节匹配
     *   - 返回最相关的章节列表，按匹配得分降序排列
     *   - 用于 RAG 流水线中的章节级检索，缩小检索范围
     *
     * @param documentId      文档ID，限定在某个文档内搜索
     * @param topic           主题关键词
     * @param facet           维度/方面（如"实现方式"、"配置说明"等）
     * @param informationNeed 信息需求描述
     * @param question        用户的原始问题
     * @param size            返回结果的最大数量
     * @return 匹配的章节命中列表，包含节点ID、标题、路径和匹配得分
     */
    List<NavigationSectionHit> searchSections(Long documentId,
                                              String topic,
                                              String facet,
                                              String informationNeed,
                                              String question,
                                              int size);

    /**
     * 【章节命中结果 - record 类型】
     *
     * Java 16+ 的 record 语法，用于定义不可变的数据载体。
     * record 自动生成构造器、getter、equals、hashCode 和 toString 方法。
     *
     * 字段说明：
     *   - nodeId:       结构节点ID
     *   - nodeCode:     节点编码（如 "1.2.3"）
     *   - title:        章节标题
     *   - sectionPath:  章节路径（人类可读的层级路径）
     *   - canonicalPath: 规范路径（用于精确匹配的标准化路径）
     *   - score:        匹配得分，值越大表示越相关
     */
    record NavigationSectionHit(
        Long nodeId,
        String nodeCode,
        String title,
        String sectionPath,
        String canonicalPath,
        double score
    ) {
    }
}
