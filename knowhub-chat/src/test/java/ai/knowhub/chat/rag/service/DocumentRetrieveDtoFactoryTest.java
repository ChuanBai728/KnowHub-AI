package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.document.model.DocumentRetrieveDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRetrieveDtoFactoryTest {

    private final DocumentRetrieveDtoFactory factory = new DocumentRetrieveDtoFactory();

    @Test
    void buildsDocumentRetrieveDtoWithKnowHubScope() {
        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .selectedDocumentId(100L)
            .selectedTaskId(200L)
            .retrievalDocumentIds(List.of(100L, 101L))
            .retrievalTaskIds(List.of(200L, 201L))
            .build();

        DocumentRetrieveDto dto = factory.build("2026 部署手册", plan, 8);

        assertThat(dto.getQuestion()).isEqualTo("2026 部署手册");
        assertThat(dto.getTopK()).isEqualTo(8);
        assertThat(dto.resolvedDocumentIds()).containsExactly(100L, 101L);
        assertThat(dto.resolvedTaskIds()).containsExactly(200L, 201L);
        assertThat(dto.getFilters().getYearHints()).containsExactly("2026");
        assertThat(dto.getFilters().getDocumentNameHints()).contains("部署手册");
    }
}
