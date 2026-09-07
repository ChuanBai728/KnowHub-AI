package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.rag.model.DocumentNavigationAction;
import ai.knowhub.chat.rag.model.DocumentNavigationDecision;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.rag.model.RagRewriteResult;
import ai.knowhub.chat.service.ObservedChatModelService;
import ai.knowhub.document.model.graph.GraphSection;
import ai.knowhub.document.service.DocumentNavigationIndexService;
import ai.knowhub.document.service.DocumentStructureGraphService;
import ai.knowhub.prompt.PromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentQuestionRouterTest {

    @Test
    void prefersExplicitQuotedSectionTitleForChildSectionQuestion() {
        DocumentStructureGraphService graphService = mock(DocumentStructureGraphService.class);
        GraphSection accountLifecycle = GraphSection.builder()
            .nodeId(200L)
            .documentId(10L)
            .nodeCode("2")
            .title("2. 账号生命周期")
            .sectionPath("信息系统账号与访问控制规范 > 2. 账号生命周期")
            .canonicalPath("/document/access/2")
            .build();
        GraphSection documentRoot = GraphSection.builder()
            .nodeId(100L)
            .documentId(10L)
            .title("信息系统账号与访问控制规范")
            .build();
        when(graphService.findSectionByTitle(10L, "账号生命周期")).thenReturn(null);
        when(graphService.listSections(10L)).thenReturn(List.of(documentRoot, accountLifecycle));

        @SuppressWarnings("unchecked")
        ObjectProvider<DocumentNavigationIndexService> navigationIndexProvider = mock(ObjectProvider.class);
        DocumentQuestionRouter router = new DocumentQuestionRouter(
            graphService,
            navigationIndexProvider,
            mock(ObservedChatModelService.class),
            mock(PromptTemplateService.class),
            new ObjectMapper()
        );
        String question = "《信息系统账号与访问控制规范》的“账号生命周期”包含哪些下级章节？";

        DocumentNavigationDecision decision = router.route(
            10L,
            question,
            new RagRewriteResult(question, List.of(question))
        );

        assertThat(decision.getExecutionMode()).isEqualTo(ExecutionMode.GRAPH_ONLY);
        assertThat(decision.getNavigationAction()).isEqualTo(DocumentNavigationAction.CHILD_SECTION_DESCEND);
        assertThat(decision.getStructureAnchor().getStructureNodeId()).isEqualTo(200L);
        assertThat(decision.getStructureAnchor().getTargetSectionHint()).contains("账号生命周期");
        verify(graphService).findSectionByTitle(10L, "账号生命周期");
        verify(graphService).listSections(10L);
    }
}
