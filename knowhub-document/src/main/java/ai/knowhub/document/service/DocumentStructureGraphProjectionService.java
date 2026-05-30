package ai.knowhub.document.service;

/**
 * 文档结构图谱投影服务接口。
 *
 * 负责将文档的结构节点（章节、步骤、列表项）投影到图数据库（Neo4j），
 * 以便后续通过图查询实现结构化知识导航。
 */
public interface DocumentStructureGraphProjectionService {

    boolean enabled();

    void projectToGraph(Long documentId, Long parseTaskId);

    void deleteByDocumentId(Long documentId);
}
