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
    void routesContentLocationQuestionToGraphOnly() {
        DocumentStructureGraphService graphService = mock(DocumentStructureGraphService.class);
        GraphSection section = GraphSection.builder()
            .nodeId(201L)
            .documentId(10L)
            .title("使用 ConfigMap")
            .sectionPath("ConfigMap > 使用 ConfigMap")
            .build();
        when(graphService.listSections(10L)).thenReturn(List.of());
        when(graphService.findBestSection(10L, "Kubernetes ConfigMap 文档中，介绍 ConfigMap 各种使用方式的内容位于哪个一级章节？", ""))
            .thenReturn(section);

        @SuppressWarnings("unchecked")
        ObjectProvider<DocumentNavigationIndexService> navigationIndexProvider = mock(ObjectProvider.class);
        DocumentQuestionRouter router = new DocumentQuestionRouter(
            graphService,
            navigationIndexProvider,
            mock(ObservedChatModelService.class),
            mock(PromptTemplateService.class),
            new ObjectMapper()
        );
        String question = "Kubernetes ConfigMap 文档中，介绍 ConfigMap 各种使用方式的内容位于哪个一级章节？";

        DocumentNavigationDecision decision = router.route(
            10L,
            question,
            new RagRewriteResult(question, List.of(question))
        );

        assertThat(decision.getExecutionMode()).isEqualTo(ExecutionMode.GRAPH_ONLY);
        assertThat(decision.getNavigationAction()).isEqualTo(DocumentNavigationAction.SECTION_ADJACENCY_LOOKUP);
        assertThat(decision.getStructureAnchor().getTargetSectionHint()).contains("使用 ConfigMap");
    }

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

    @Test
    void routesContextualContentFollowUpThroughGraphThenEvidence() {
        DocumentStructureGraphService graphService = mock(DocumentStructureGraphService.class);
        GraphSection section = GraphSection.builder()
            .nodeId(301L)
            .documentId(10L)
            .title("创建 Secret")
            .sectionPath("使用 Secret > 创建 Secret")
            .contentText("可以使用 stringData 字段提供任意字符串。")
            .build();
        when(graphService.listSections(10L)).thenReturn(List.of());
        String question = "不想自己做这种编码转换时该用哪个字段？";
        String history = "用户：Secret 的 data 字段值需要采用什么编码？\n"
            + "助手：Docker 示例很多，但可以使用 `stringData` 避免手动执行 Base64 转换。";
        String contextualQuestion = "data stringData base64";
        when(graphService.findBestSection(10L, contextualQuestion, "")).thenReturn(section);

        @SuppressWarnings("unchecked")
        ObjectProvider<DocumentNavigationIndexService> navigationIndexProvider = mock(ObjectProvider.class);
        DocumentQuestionRouter router = new DocumentQuestionRouter(
            graphService,
            navigationIndexProvider,
            mock(ObservedChatModelService.class),
            mock(PromptTemplateService.class),
            new ObjectMapper()
        );

        DocumentNavigationDecision decision = router.route(
            10L,
            question,
            new RagRewriteResult(question, List.of(question)),
            true,
            history
        );

        assertThat(decision.getExecutionMode()).isEqualTo(ExecutionMode.GRAPH_THEN_EVIDENCE);
        assertThat(decision.getNavigationAction()).isEqualTo(DocumentNavigationAction.TOPIC_CONTINUE);
        assertThat(decision.getStructureAnchor().getStructureNodeId()).isEqualTo(301L);
        verify(graphService).findBestSection(10L, contextualQuestion, "");
    }
}
