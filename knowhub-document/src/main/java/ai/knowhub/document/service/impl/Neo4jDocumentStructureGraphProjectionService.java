package ai.knowhub.document.service.impl;

import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.data.KnowHubDocumentStructureNode;
import ai.knowhub.document.service.DocumentStructureGraphProjectionService;
import ai.knowhub.document.service.DocumentStructureNodeService;
import ai.knowhub.enums.DocumentStructureNodeTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnBean(name = "documentManageNeo4jDriver")
public class Neo4jDocumentStructureGraphProjectionService implements DocumentStructureGraphProjectionService {

    private final Driver driver;
    private final DocumentManageProperties properties;
    private final DocumentStructureNodeService structureNodeService;

    public Neo4jDocumentStructureGraphProjectionService(
        Driver driver,
        DocumentManageProperties properties,
        DocumentStructureNodeService structureNodeService
    ) {
        this.driver = driver;
        this.properties = properties;
        this.structureNodeService = structureNodeService;
    }

    @Override
    public boolean enabled() {
        return Boolean.TRUE.equals(properties.getNeo4j().getEnabled());
    }

    @Override
    public void projectToGraph(Long documentId, Long parseTaskId) {
        if (!enabled() || documentId == null) {
            return;
        }
        List<KnowHubDocumentStructureNode> nodes = structureNodeService.listDocumentNodes(documentId, parseTaskId);
        try (Session session = session()) {
            deleteByDocumentId(documentId);
            session.executeWrite(tx -> {
                for (KnowHubDocumentStructureNode node : nodes) {
                    if (node == null || node.getId() == null) {
                        continue;
                    }
                    DocumentStructureNodeTypeEnum nodeType = DocumentStructureNodeTypeEnum.getRc(node.getNodeType());
                    if (DocumentStructureNodeTypeEnum.SECTION.equals(nodeType)) {
                        tx.run("""
                            MERGE (n:KnowHubDocumentNode:KnowHubDocumentSection {documentId: $documentId, nodeId: $nodeId})
                            SET n += $props
                            """, Values.parameters("documentId", documentId, "nodeId", node.getId(), "props", props(node, nodeType)));
                    } else if (DocumentStructureNodeTypeEnum.STEP.equals(nodeType)
                        || DocumentStructureNodeTypeEnum.LIST_ITEM.equals(nodeType)) {
                        tx.run("""
                            MERGE (n:KnowHubDocumentNode:KnowHubDocumentItem {documentId: $documentId, nodeId: $nodeId})
                            SET n += $props
                            """, Values.parameters("documentId", documentId, "nodeId", node.getId(), "props", props(node, nodeType)));
                    }
                }
                for (KnowHubDocumentStructureNode node : nodes) {
                    if (node == null || node.getId() == null) {
                        continue;
                    }
                    if (node.getParentNodeId() != null) {
                        tx.run("""
                            MATCH (parent:KnowHubDocumentNode {documentId: $documentId, nodeId: $parentNodeId})
                            MATCH (child:KnowHubDocumentNode {documentId: $documentId, nodeId: $nodeId})
                            MERGE (parent)-[:PARENT_OF]->(child)
                            """, Values.parameters("documentId", documentId, "parentNodeId", node.getParentNodeId(), "nodeId", node.getId()));
                    }
                    if (node.getPrevSiblingNodeId() != null) {
                        tx.run("""
                            MATCH (prev:KnowHubDocumentNode {documentId: $documentId, nodeId: $prevNodeId})
                            MATCH (current:KnowHubDocumentNode {documentId: $documentId, nodeId: $nodeId})
                            MERGE (prev)-[:NEXT_SIBLING]->(current)
                            """, Values.parameters("documentId", documentId, "prevNodeId", node.getPrevSiblingNodeId(), "nodeId", node.getId()));
                    }
                }
                return null;
            });
            log.info("Projected document structure to Neo4j: documentId={}, parseTaskId={}, nodeCount={}",
                documentId, parseTaskId, nodes.size());
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        try (Session session = session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (n:KnowHubDocumentNode {documentId: $documentId}) DETACH DELETE n",
                    Values.parameters("documentId", documentId));
                return null;
            });
        }
    }

    private Session session() {
        return driver.session(SessionConfig.forDatabase(properties.getNeo4j().getDatabase()));
    }

    private Map<String, Object> props(KnowHubDocumentStructureNode node, DocumentStructureNodeTypeEnum nodeType) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("nodeId", node.getId());
        props.put("documentId", node.getDocumentId());
        props.put("parseTaskId", node.getParseTaskId());
        props.put("nodeNo", node.getNodeNo());
        props.put("nodeType", nodeType == null ? "" : nodeType.name());
        props.put("parentNodeId", node.getParentNodeId());
        props.put("prevSiblingNodeId", node.getPrevSiblingNodeId());
        props.put("nextSiblingNodeId", node.getNextSiblingNodeId());
        props.put("depth", node.getDepth());
        props.put("nodeCode", blank(node.getNodeCode()));
        props.put("title", blank(node.getTitle()));
        props.put("anchorText", blank(node.getAnchorText()));
        props.put("canonicalPath", blank(node.getCanonicalPath()));
        props.put("sectionPath", blank(node.getSectionPath()));
        props.put("contentText", blank(node.getContentText()));
        props.put("itemIndex", node.getItemIndex());
        props.entrySet().removeIf(entry -> entry.getValue() == null);
        return props;
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
