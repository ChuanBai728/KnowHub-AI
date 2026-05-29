package ai.knowhub.document.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRetrieveDtoTest {

    @Test
    void resolvesSingleDocumentAndTaskIdsAsLists() {
        DocumentRetrieveDto dto = new DocumentRetrieveDto(
            "question",
            "retrieval query",
            10L,
            20L,
            5,
            null,
            List.of("context")
        );

        assertThat(dto.resolvedDocumentIds()).containsExactly(10L);
        assertThat(dto.resolvedTaskIds()).containsExactly(20L);
    }

    @Test
    void explicitListsTakePriorityOverSingleIds() {
        DocumentRetrieveDto dto = new DocumentRetrieveDto();
        dto.setDocumentId(10L);
        dto.setTaskId(20L);
        dto.setDocumentIds(List.of(11L, 12L));
        dto.setTaskIds(List.of(21L, 22L));

        assertThat(dto.resolvedDocumentIds()).containsExactly(11L, 12L);
        assertThat(dto.resolvedTaskIds()).containsExactly(21L, 22L);
    }
}
