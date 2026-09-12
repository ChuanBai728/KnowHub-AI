package ai.knowhub.document.service.impl;

import ai.knowhub.document.config.DocumentManageProperties;
import ai.knowhub.document.model.graph.GraphItem;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Neo4jDocumentStructureGraphServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void listItemsUsesProjectedParentNodeIdAndMapsItAsSectionNodeId() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result result = mock(Result.class);
        Record record = mock(Record.class);
        Value node = mock(Value.class);
        DocumentManageProperties properties = new DocumentManageProperties();
        properties.getNeo4j().setDatabase("neo4j");

        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.run(anyString(), any(Value.class))).thenReturn(result);
        when(record.get("n")).thenReturn(node);
        when(node.asMap()).thenReturn(Map.of(
            "nodeId", 11L,
            "documentId", 22L,
            "parentNodeId", 33L,
            "nodeNo", 1,
            "nodeType", "LIST_ITEM"
        ));
        when(result.list(any(Function.class))).thenAnswer(invocation -> {
            Function<Record, GraphItem> mapper = invocation.getArgument(0);
            return List.of(mapper.apply(record));
        });

        Neo4jDocumentStructureGraphService service =
            new Neo4jDocumentStructureGraphService(driver, properties);

        List<GraphItem> items = service.listItems(22L, 33L);

        assertEquals(1, items.size());
        assertEquals(33L, items.get(0).getSectionNodeId());
        verify(session).run(
            "MATCH (n:KnowHubDocumentItem {documentId: $documentId, parentNodeId: $sectionNodeId}) RETURN n ORDER BY n.nodeNo",
            Values.parameters("documentId", 22L, "sectionNodeId", 33L)
        );
    }
}
