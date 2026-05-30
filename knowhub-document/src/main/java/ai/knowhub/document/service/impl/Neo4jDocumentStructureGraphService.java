package ai.knowhub.document.service.impl;

import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.model.graph.GraphItem;
import ai.knowhub.document.model.graph.GraphSection;
import ai.knowhub.document.service.DocumentStructureGraphService;
import cn.hutool.core.util.StrUtil;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Neo4j 结构图谱查询实现。
 *
 * 基于 Neo4j 中的文档结构节点图，提供章节查询、父子导航、兄弟遍历、
 * 列表项检索和模糊搜索等能力。支持按标题、编码、路径等多种方式定位章节。
 * 文本匹配时会去除空白和 Markdown 标记后做大小写不敏感比较。
 */
@Service
@ConditionalOnBean(name = "documentManageNeo4jDriver")
public class Neo4jDocumentStructureGraphService implements DocumentStructureGraphService {

    private final Driver driver;
    private final DocumentManageProperties properties;

    public Neo4jDocumentStructureGraphService(Driver driver, DocumentManageProperties properties) {
        this.driver = driver;
        this.properties = properties;
    }

    @Override
    public boolean isGraphAvailable(Long documentId) {
        if (documentId == null) {
            return false;
        }
        try (Session session = session()) {
            return session.run(
                    "MATCH (n:KnowHubDocumentNode {documentId: $documentId}) RETURN count(n) > 0 AS available",
                    Values.parameters("documentId", documentId)
                )
                .single()
                .get("available")
                .asBoolean(false);
        }
    }

    @Override
    public GraphSection findSectionById(Long documentId, Long sectionNodeId) {
        return oneSection("MATCH (n:KnowHubDocumentSection {documentId: $documentId, nodeId: $value}) RETURN n",
            documentId, sectionNodeId);
    }

    @Override
    public GraphSection findSectionByCode(Long documentId, String nodeCode) {
        return oneSection("MATCH (n:KnowHubDocumentSection {documentId: $documentId, nodeCode: $value}) RETURN n",
            documentId, nodeCode);
    }

    @Override
    public GraphSection findSectionByTitle(Long documentId, String title) {
        if (documentId == null || StrUtil.isBlank(title)) {
            return null;
        }
        String normalized = normalize(title);
        return listSections(documentId).stream()
            .filter(section -> normalized.equals(normalize(section.getTitle()))
                || normalized.equals(normalize(section.getAnchorText()))
                || normalized.equals(normalize(section.getSectionPath())))
            .findFirst()
            .orElse(null);
    }

    @Override
    public GraphSection findSectionByCanonicalPath(Long documentId, String canonicalPath) {
        return oneSection("MATCH (n:KnowHubDocumentSection {documentId: $documentId, canonicalPath: $value}) RETURN n",
            documentId, canonicalPath);
    }

    @Override
    public GraphSection findBestSection(Long documentId, String topic, String facet) {
        String normalizedTopic = normalize(topic);
        String normalizedFacet = normalize(facet);
        if (documentId == null || (normalizedTopic.isBlank() && normalizedFacet.isBlank())) {
            return null;
        }
        GraphSection bestSection = null;
        int bestScore = 0;
        for (GraphSection section : listSections(documentId)) {
            int score = 0;
            String title = normalize(section.getTitle());
            String path = normalize(section.getSectionPath());
            String anchor = normalize(section.getAnchorText());
            String content = normalize(section.getContentText());
            if (StrUtil.isNotBlank(normalizedTopic)) {
                if (title.contains(normalizedTopic) || path.contains(normalizedTopic)) {
                    score += 8;
                } else if (anchor.contains(normalizedTopic)) {
                    score += 6;
                } else if (content.contains(normalizedTopic)) {
                    score += 2;
                }
            }
            if (StrUtil.isNotBlank(normalizedFacet)) {
                if (title.contains(normalizedFacet) || path.contains(normalizedFacet)) {
                    score += 5;
                } else if (content.contains(normalizedFacet)) {
                    score += 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestSection = section;
            }
        }
        return bestScore > 0 ? bestSection : null;
    }

    @Override
    public List<GraphSection> listSections(Long documentId) {
        if (documentId == null) {
            return List.of();
        }
        try (Session session = session()) {
            return session.run(
                    "MATCH (n:KnowHubDocumentSection {documentId: $documentId}) RETURN n ORDER BY n.nodeNo",
                    Values.parameters("documentId", documentId)
                )
                .list(this::toSection);
        }
    }

    @Override
    public List<GraphSection> listChildren(Long documentId, Long sectionNodeId) {
        if (documentId == null || sectionNodeId == null) {
            return List.of();
        }
        try (Session session = session()) {
            return session.run(
                    "MATCH (n:KnowHubDocumentSection {documentId: $documentId, parentNodeId: $sectionNodeId}) RETURN n ORDER BY n.nodeNo",
                    Values.parameters("documentId", documentId, "sectionNodeId", sectionNodeId)
                )
                .list(this::toSection);
        }
    }

    @Override
    public GraphSection parentSection(Long documentId, Long sectionNodeId) {
        GraphSection section = findSectionById(documentId, sectionNodeId);
        return section == null || section.getParentNodeId() == null ? null : findSectionById(documentId, section.getParentNodeId());
    }

    @Override
    public GraphSection previousSibling(Long documentId, Long sectionNodeId) {
        GraphSection section = findSectionById(documentId, sectionNodeId);
        return section == null || section.getPrevSiblingNodeId() == null ? null : findSectionById(documentId, section.getPrevSiblingNodeId());
    }

    @Override
    public GraphSection nextSibling(Long documentId, Long sectionNodeId) {
        GraphSection section = findSectionById(documentId, sectionNodeId);
        return section == null || section.getNextSiblingNodeId() == null ? null : findSectionById(documentId, section.getNextSiblingNodeId());
    }

    @Override
    public GraphItem findItemByIndex(Long documentId, Long sectionNodeId, Integer itemIndex) {
        if (documentId == null || sectionNodeId == null || itemIndex == null) {
            return null;
        }
        return listItems(documentId, sectionNodeId).stream()
            .filter(item -> Objects.equals(itemIndex, item.getItemIndex()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public List<GraphItem> listItems(Long documentId, Long sectionNodeId) {
        if (documentId == null || sectionNodeId == null) {
            return List.of();
        }
        try (Session session = session()) {
            return session.run(
                    "MATCH (n:KnowHubDocumentItem {documentId: $documentId, sectionNodeId: $sectionNodeId}) RETURN n ORDER BY n.nodeNo",
                    Values.parameters("documentId", documentId, "sectionNodeId", sectionNodeId)
                )
                .list(this::toItem);
        }
    }

    @Override
    public List<GraphItem> searchItemsInSection(Long documentId, Long sectionNodeId, String keyword) {
        String normalizedKeyword = normalize(keyword);
        List<GraphItem> items = listItems(documentId, sectionNodeId);
        if (normalizedKeyword.isBlank()) {
            return items;
        }
        return items.stream()
            .filter(item -> normalize(item.displayText()).contains(normalizedKeyword))
            .sorted(Comparator.comparing(GraphItem::getNodeNo, Comparator.nullsLast(Integer::compareTo)))
            .toList();
    }

    private GraphSection oneSection(String cypher, Long documentId, Object value) {
        if (documentId == null || value == null || (value instanceof String text && StrUtil.isBlank(text))) {
            return null;
        }
        try (Session session = session()) {
            return session.run(cypher, Values.parameters("documentId", documentId, "value", value))
                .stream()
                .findFirst()
                .map(this::toSection)
                .orElse(null);
        }
    }

    private GraphSection toSection(Record record) {
        Map<String, Object> map = record.get("n").asMap();
        return GraphSection.builder()
            .nodeId(longValue(map.get("nodeId")))
            .documentId(longValue(map.get("documentId")))
            .parseTaskId(longValue(map.get("parseTaskId")))
            .nodeNo(intValue(map.get("nodeNo")))
            .depth(intValue(map.get("depth")))
            .parentNodeId(longValue(map.get("parentNodeId")))
            .prevSiblingNodeId(longValue(map.get("prevSiblingNodeId")))
            .nextSiblingNodeId(longValue(map.get("nextSiblingNodeId")))
            .nodeCode(text(map.get("nodeCode")))
            .title(text(map.get("title")))
            .anchorText(text(map.get("anchorText")))
            .sectionPath(text(map.get("sectionPath")))
            .canonicalPath(text(map.get("canonicalPath")))
            .contentText(text(map.get("contentText")))
            .build();
    }

    private GraphItem toItem(Record record) {
        Map<String, Object> map = record.get("n").asMap();
        return GraphItem.builder()
            .nodeId(longValue(map.get("nodeId")))
            .documentId(longValue(map.get("documentId")))
            .parseTaskId(longValue(map.get("parseTaskId")))
            .nodeNo(intValue(map.get("nodeNo")))
            .nodeType(text(map.get("nodeType")))
            .sectionNodeId(longValue(map.get("sectionNodeId")))
            .prevSiblingNodeId(longValue(map.get("prevSiblingNodeId")))
            .nextSiblingNodeId(longValue(map.get("nextSiblingNodeId")))
            .title(text(map.get("title")))
            .anchorText(text(map.get("anchorText")))
            .sectionPath(text(map.get("sectionPath")))
            .canonicalPath(text(map.get("canonicalPath")))
            .contentText(text(map.get("contentText")))
            .itemIndex(intValue(map.get("itemIndex")))
            .build();
    }

    private Session session() {
        return driver.session(SessionConfig.forDatabase(properties.getNeo4j().getDatabase()));
    }

    private String normalize(String value) {
        return StrUtil.blankToDefault(value, "")
            .replaceAll("[\\s>`*#_\\-]+", "")
            .toLowerCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
