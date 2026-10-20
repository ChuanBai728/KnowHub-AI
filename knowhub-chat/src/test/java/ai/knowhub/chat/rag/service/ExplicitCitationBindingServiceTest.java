package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.rag.model.PromptEvidenceManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExplicitCitationBindingServiceTest {
    private final ExplicitCitationBindingService service = new ExplicitCitationBindingService();

    @Test
    void bindsOnlyVisibleSourcesInFirstTokenOrderWithoutRenumbering() {
        var manifest = new PromptEvidenceManifest(List.of(reference("1", 101), reference("3", 103)));
        var result = service.bind("事实 [3]，另一事实 [1][3]，不存在 [2][999999999999999999999999]。", manifest);

        assertThat(result.references()).extracting(SearchReference::getReferenceId).containsExactly("3", "1");
        assertThat(result.rejectedTokens()).containsExactly("[2]", "[999999999999999999999999]");
    }

    @Test
    void neverInventsReferencesForUncitedAnswersOrMissingManifest() {
        assertThat(service.bind("没有引用的回答", new PromptEvidenceManifest(List.of(reference("1", 101))))
            .references()).isEmpty();
        assertThat(service.bind("事实 [1]", null).references()).isEmpty();
        assertThat(service.bind(null, null).references()).isEmpty();
    }

    @Test
    void rejectsNonAsciiZeroNegativeLeadingZeroAndIncompleteTokens() {
        var manifest = new PromptEvidenceManifest(List.of(reference("1", 101)));
        assertThat(service.bind("[0][-1][01][１][١]［1］[ 1 ][1", manifest).references()).isEmpty();
    }

    @Test
    void rejectsConflictingIdsButAllowsRepeatedIdentity() {
        var manifest = new PromptEvidenceManifest(List.of(
            reference("1", 101), reference("1", 102), reference("2", 103), reference("2", 103)));
        var result = service.bind("[1][2][2]", manifest);
        assertThat(result.references()).extracting(SearchReference::getChunkId).containsExactly(103L);
        assertThat(result.rejectedTokens()).containsExactly("[1]");
    }

    @Test
    void manifestCannotBeChangedByMutatingCandidatesOrReturnedReferences() {
        SearchReference original = reference("1", 101);
        var manifest = new PromptEvidenceManifest(List.of(original));
        original.setReferenceId("2");
        original.setSnippet("被修改的内容");
        manifest.references().get(0).setChunkId(999L);

        var result = service.bind("[1]", manifest);
        assertThat(result.references()).singleElement().satisfies(reference -> {
            assertThat(reference.getChunkId()).isEqualTo(101L);
            assertThat(reference.getSnippet()).isEqualTo("原文");
        });
    }

    private SearchReference reference(String id, long chunkId) {
        SearchReference reference = new SearchReference();
        reference.setReferenceId(id);
        reference.setSourceType("DOCUMENT");
        reference.setChunkId(chunkId);
        reference.setSnippet("原文");
        return reference;
    }
}
