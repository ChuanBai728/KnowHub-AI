package ai.knowhub.document.service;

import java.util.List;

/**
 * 【知识路由索引服务接口】
 *
 * 作用：管理知识路由的索引数据，支持根据用户问题快速匹配到最相关的文档。
 * 是知识路由（Knowledge Routing）系统的索引层，将文档的路由信息建立索引以加速检索。
 *
 * 架构角色：
 *   - 属于知识管理模块的"路由索引"层
 *   - 为知识路由服务提供高效的索引查询能力
 *   - 支持基于关键词的词汇级匹配（Lexical Matching）
 *   - 索引数据在文档更新时自动刷新
 *
 * 核心概念：
 *   - 知识路由（Knowledge Routing）：根据用户问题自动选择最相关的文档进行检索
 *   - 路由实体（Route Entity）：路由索引中的实体，描述文档的某个特征（如标题、关键词）
 *   - 实体类型（Entity Type）：路由实体的分类（如 "title"、"keyword"、"summary"）
 *   - 词汇命中（Lexical Hit）：基于关键词匹配的检索结果
 *   - 匹配得分（Score）：表示匹配的相关程度，值越大越相关
 *
 * 设计模式：Gateway 模式，封装路由索引的存储和查询操作
 */
public interface KnowledgeRouteIndexService {

    /**
     * 刷新路由索引（如果需要）
     *
     * 功能说明：
     *   - 检查路由索引是否需要刷新（如文档数据有变更）
     *   - 如果需要，重建或增量更新路由索引
     *   - 通常在系统启动或定时任务中调用
     */
    void refreshIfNeeded();

    /**
     * 搜索匹配的路由
     *
     * 功能说明：
     *   - 根据路由文本（通常是用户问题的改写版本）搜索匹配的文档路由
     *   - 支持按实体类型过滤（如只匹配标题或只匹配关键词）
     *   - 返回按匹配得分降序排列的结果
     *
     * @param routingText 路由文本，通常是用户问题的改写或关键词提取结果
     * @param entityType  实体类型过滤条件，为 null 时不过滤
     * @param size        返回结果的最大数量
     * @return 匹配的路由命中列表
     */
    List<RouteLexicalHit> search(String routingText, String entityType, int size);

    /**
     * 删除指定文档的路由索引
     *
     * @param documentId 文档ID
     */
    void deleteDocumentRoute(Long documentId);

    /**
     * 【路由词汇命中结果 - record 类型】
     *
     * Java 16+ 的 record 语法，用于定义不可变的数据载体。
     *
     * 字段说明：
     *   - routeId:      路由记录的唯一标识
     *   - entityCode:   实体编码（如关键词的编码）
     *   - entityType:   实体类型（如 "title"、"keyword"、"summary"）
     *   - documentId:   关联的文档ID
     *   - scopeCode:    知识域编码
     *   - topicCode:    主题编码
     *   - documentName: 文档名称
     *   - score:        匹配得分
     */
    record RouteLexicalHit(
        String routeId,
        String entityCode,
        String entityType,
        Long documentId,
        String scopeCode,
        String topicCode,
        String documentName,
        double score
    ) {
    }
}
