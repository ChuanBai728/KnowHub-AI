package ai.knowhub.chat.rag.executor;

import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.model.debug.ChatDebugTrace;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.ExecutionMode;
import ai.knowhub.chat.rag.model.RagRetrievalContext;
import ai.knowhub.chat.rag.model.SubQuestionEvidence;
import ai.knowhub.chat.rag.service.ExplicitCitationBindingService;
import ai.knowhub.chat.rag.service.RagPromptAssemblyService;
import ai.knowhub.chat.rag.service.RagRetrievalEngine;
import ai.knowhub.chat.service.ObservedChatModelService;
import ai.knowhub.chat.service.TaskInfo;
import ai.knowhub.chat.support.StreamEventWriter;
import ai.knowhub.enums.ChatQueryMode;
import ai.knowhub.prompt.PromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagChatExecutorCitationTest {
    @Test
    void streamedAnswerCanOnlyBindEvidenceRenderedByThisExecution() {
        RagRetrievalEngine retrieval = mock(RagRetrievalEngine.class);
        ObservedChatModelService model = mock(ObservedChatModelService.class);
        ChatRagProperties properties = new ChatRagProperties();
        properties.setTotalEvidenceMaxChars(200);
        properties.setPerSubQuestionEvidenceMaxChars(200);
        RagPromptAssemblyService assembly = new RagPromptAssemblyService(properties,
            new PromptTemplateService(new DefaultResourceLoader()));
        RagChatExecutor executor = new RagChatExecutor(retrieval, assembly,
            new StreamEventWriter(new ObjectMapper()), model);
        TaskInfo task = new TaskInfo("conversation", 7L, "问题", ChatQueryMode.AUTO_DOCUMENT, "trace",
            null, null, null, LocalDate.of(2026, 10, 20), "2026-10-20",
            ConversationExecutionPlan.builder().mode(ExecutionMode.RETRIEVAL).originalQuestion("问题").build(),
            new ChatDebugTrace(), null, null, Sinks.many().unicast().onBackpressureBuffer(), null, null, null,
            new ArrayList<>(), new ArrayList<>(), new HashSet<>(), System.currentTimeMillis());

        SubQuestionEvidence evidence = new SubQuestionEvidence();
        evidence.setReferences(List.of(reference("1", "已展示"), reference("2", "被省略".repeat(400))));
        RagRetrievalContext context = new RagRetrievalContext();
        context.setSubQuestionEvidenceList(List.of(evidence));
        when(retrieval.retrieve(task.executionPlan(), null)).thenReturn(context);
        when(model.streamText(eq("rag_answer"), anyString(), anyString(), isNull()))
            .thenAnswer(invocation -> {
                // 模型被调用前清单已经发布；token 可跨多个流式片段。
                assertThat(task.promptEvidenceManifest().references()).extracting(SearchReference::getReferenceId)
                    .containsExactly("1");
                return Flux.just("事实 [", "1]，错误来源 [2]");
            });

        executor.execute(task).doOnNext(task.answerBuffer()::append).blockLast(Duration.ofSeconds(10));

        assertThat(task.references()).isEmpty();
        assertThat(new ExplicitCitationBindingService().bind(task.answerBuffer().toString(), task.promptEvidenceManifest())
            .references()).extracting(SearchReference::getReferenceId).containsExactly("1");
    }

    private SearchReference reference(String id, String snippet) {
        SearchReference reference = new SearchReference();
        reference.setReferenceId(id);
        reference.setChunkId(Long.valueOf(id));
        reference.setSnippet(snippet);
        return reference;
    }
}
