package ai.knowhub.chat.rag.executor;

import ai.knowhub.chat.model.RetrievalResultVo;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.service.GraphAnswerRenderer;
import ai.knowhub.chat.rag.service.StructureGraphQueryEngine;
import ai.knowhub.chat.service.ConversationTraceRecorder;
import ai.knowhub.chat.service.TaskInfo;
import ai.knowhub.document.model.graph.GraphQueryResult;
import ai.knowhub.document.model.graph.GraphSection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphThenEvidenceExecutorTest {

    @Test
    void recordsGraphEvidenceAsReferenceAndRetrievalObservation() {
        GraphThenEvidenceExecutor executor = new GraphThenEvidenceExecutor(
            mock(StructureGraphQueryEngine.class),
            mock(GraphAnswerRenderer.class),
            null
        );
        TaskInfo taskInfo = mock(TaskInfo.class);
        ConversationTraceRecorder recorder = mock(ConversationTraceRecorder.class);
        List<SearchReference> references = new ArrayList<>();
        HashSet<String> usedTools = new HashSet<>();
        when(taskInfo.references()).thenReturn(references);
        when(taskInfo.usedTools()).thenReturn(usedTools);
        when(taskInfo.traceRecorder()).thenReturn(recorder);
        when(taskInfo.traceId()).thenReturn("trace-1");

        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .originalQuestion("那如果是环境变量方式呢？")
            .selectedDocumentId(42L)
            .selectedDocumentName("Kubernetes ConfigMap")
            .build();
        GraphSection section = GraphSection.builder()
            .nodeId(101L)
            .documentId(42L)
            .title("使用 Configmap 作为环境变量")
            .sectionPath("Kubernetes ConfigMap > 使用 ConfigMap > 使用 Configmap 作为环境变量")
            .canonicalPath("/document/Kubernetes-ConfigMap/使用-Configmap-作为环境变量")
            .contentText("使用 configMapKeyRef 将指定键写入环境变量。")
            .build();

        executor.recordGraphEvidence(taskInfo, plan, GraphQueryResult.builder().targetSection(section).build());

        assertThat(references).singleElement().satisfies(reference -> {
            assertThat(reference.getDocumentId()).isEqualTo(42L);
            assertThat(reference.getDocumentName()).isEqualTo("Kubernetes ConfigMap");
            assertThat(reference.getChannel()).isEqualTo("graph");
            assertThat(reference.getStructureNodeId()).isEqualTo(101L);
        });
        assertThat(usedTools).contains("graph");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RetrievalResultVo>> captor = ArgumentCaptor.forClass(List.class);
        verify(recorder).recordRetrievalResults(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(result -> {
            assertThat(result.getDocumentId()).isEqualTo(42L);
            assertThat(result.getChannelType()).isEqualTo("graph");
            assertThat(result.getFinalRank()).isEqualTo(1);
            assertThat(result.isSelected()).isTrue();
        });
    }
}
