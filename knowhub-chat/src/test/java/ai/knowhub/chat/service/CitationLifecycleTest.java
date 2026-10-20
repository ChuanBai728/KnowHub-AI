package ai.knowhub.chat.service;

import ai.knowhub.chat.config.ChatAgentProperties;
import ai.knowhub.chat.model.ConversationExchangeVo;
import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.model.debug.ChatDebugTrace;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.rag.model.PromptEvidenceManifest;
import ai.knowhub.chat.rag.service.ExplicitCitationBindingService;
import ai.knowhub.chat.support.StreamEventWriter;
import ai.knowhub.enums.ChatQueryMode;
import ai.knowhub.enums.ChatTurnStatus;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Sinks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CitationLifecycleTest {
    @Mock ReactAgent agent;
    @Mock ConversationArchiveStore archive;
    @Mock ChatRuntimeRegistry registry;
    @Mock RecommendationService recommendations;
    @Mock ConversationMemoryService memory;
    @Spy ChatAgentProperties properties = new ChatAgentProperties();
    @Spy StreamEventWriter writer = new StreamEventWriter(new ObjectMapper());
    @Spy ExplicitCitationBindingService binding = new ExplicitCitationBindingService();
    @InjectMocks BusinessChatService service;

    @ParameterizedTest
    @EnumSource(value = ChatTurnStatus.class, names = {"COMPLETED", "FAILED", "STOPPED"})
    void terminalPathsArchiveOnlyCitedVisibleSources(ChatTurnStatus status) {
        TaskInfo task = task(ExecutionMode.RETRIEVAL);
        String answer = "先引用 [3]，省略证据 [2]，再引用 [1][3]。";
        task.answerBuffer().append(answer);
        task.promptEvidenceManifest(new PromptEvidenceManifest(List.of(reference("1"), reference("3"))));
        task.references().add(reference("2"));

        switch (status) {
            case COMPLETED -> ReflectionTestUtils.invokeMethod(service, "finishSuccessfully", task);
            case FAILED -> ReflectionTestUtils.invokeMethod(service, "finishWithFailure", task, new IllegalStateException("失败"));
            case STOPPED -> ReflectionTestUtils.invokeMethod(service, "stopTask", task, "用户停止");
            default -> throw new AssertionError(status);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SearchReference>> captured = ArgumentCaptor.forClass(List.class);
        verify(archive).completeExchange(eq("conversation"), eq(7L), eq(answer), anyList(),
            captured.capture(), anyList(), anyList(), any(), eq(status), anyString(), nullable(Long.class), anyLong());
        assertThat(captured.getValue()).extracting(SearchReference::getReferenceId).containsExactly("3", "1");
        if (status == ChatTurnStatus.COMPLETED) {
            verify(writer).references(captured.getValue(), null);
        }
    }

    @Test
    void runningViewWaitsForCompleteTokensAndUsesSameBindingOrder() {
        TaskInfo task = task(ExecutionMode.RETRIEVAL);
        task.promptEvidenceManifest(new PromptEvidenceManifest(List.of(reference("1"), reference("3"))));
        task.references().add(reference("2"));
        task.answerBuffer().append("事实 [3");
        assertThat(runtime(task).getReferences()).isEmpty();
        task.answerBuffer().append("][2][1]");
        assertThat(runtime(task).getReferences()).extracting(SearchReference::getReferenceId).containsExactly("3", "1");
    }

    @Test
    void missingManifestOrUncitedAnswerNeverFallsBackToCandidates() {
        TaskInfo task = task(ExecutionMode.RETRIEVAL);
        task.references().add(reference("1"));
        task.answerBuffer().append("[1]");
        assertThat(runtime(task).getReferences()).isEmpty();
        task.promptEvidenceManifest(new PromptEvidenceManifest(List.of(reference("1"))));
        task.answerBuffer().setLength(0);
        task.answerBuffer().append("无证据回复");
        assertThat(runtime(task).getReferences()).isEmpty();
    }

    @Test
    void graphAndAgentKeepTheirExistingReferenceMechanisms() {
        for (ExecutionMode mode : List.of(ExecutionMode.GRAPH_THEN_EVIDENCE, ExecutionMode.REACT_AGENT)) {
            TaskInfo task = task(mode);
            SearchReference reference = reference("1");
            task.references().add(reference);
            assertThat(runtime(task).getReferences()).containsExactly(reference);
        }
    }

    private ConversationExchangeVo runtime(TaskInfo task) {
        return ReflectionTestUtils.invokeMethod(service, "mergeRuntimeExchange", new ConversationExchangeVo(), task);
    }

    private TaskInfo task(ExecutionMode mode) {
        return new TaskInfo("conversation", 7L, "问题", ChatQueryMode.AUTO_DOCUMENT, "trace", null, null, null,
            LocalDate.of(2026, 10, 20), "2026-10-20", ConversationExecutionPlan.builder().mode(mode).build(),
            new ChatDebugTrace(), null, null, Sinks.many().unicast().onBackpressureBuffer(), null, null, null,
            new ArrayList<>(), new ArrayList<>(), new HashSet<>(), System.currentTimeMillis());
    }

    private SearchReference reference(String id) {
        SearchReference reference = new SearchReference();
        reference.setReferenceId(id);
        reference.setChunkId(Long.valueOf(id));
        reference.setSnippet("原文" + id);
        return reference;
    }
}
