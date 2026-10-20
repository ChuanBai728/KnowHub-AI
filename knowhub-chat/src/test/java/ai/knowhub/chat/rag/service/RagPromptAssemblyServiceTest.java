package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.model.SearchReference;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.RagRetrievalContext;
import ai.knowhub.chat.rag.model.SubQuestionEvidence;
import ai.knowhub.prompt.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptAssemblyServiceTest {
    private final ChatRagProperties properties = new ChatRagProperties();
    private final RagPromptAssemblyService service = new RagPromptAssemblyService(properties,
        new PromptTemplateService(new DefaultResourceLoader()));

    @Test
    void omittedEvidenceIsIneligibleEvenIfAnswerNamesItsNumber() {
        properties.setTotalEvidenceMaxChars(200);
        properties.setPerSubQuestionEvidenceMaxChars(200);
        var result = service.assemble(plan(), context(List.of(reference("1", "可见原文"),
            reference("2", "不应进入Prompt".repeat(150)))));

        assertThat(result.getUserPrompt()).contains("可见原文").doesNotContain("不应进入Prompt");
        assertThat(result.getEvidenceManifest().references()).extracting(SearchReference::getReferenceId)
            .containsExactly("1");
        assertThat(new ExplicitCitationBindingService().bind("[2][1]", result.getEvidenceManifest()).references())
            .extracting(SearchReference::getReferenceId).containsExactly("1");
    }

    @Test
    void reusedSourceKeepsOneVisibleSnapshotAcrossSubQuestions() {
        properties.setTotalEvidenceMaxChars(5000);
        properties.setPerSubQuestionEvidenceMaxChars(2000);
        RagRetrievalContext context = context(List.of(reference("1", "重复原文")));
        SubQuestionEvidence second = new SubQuestionEvidence();
        second.setSubQuestionIndex(2);
        second.setReferences(List.of(reference("1", "重复原文")));
        context.setSubQuestionEvidenceList(List.of(context.getSubQuestionEvidenceList().get(0), second));

        var result = service.assemble(plan(), context);
        assertThat(result.getRenderedReferenceCount()).isEqualTo(1);
        assertThat(result.getEvidenceManifest().references()).hasSize(1);
        assertThat(new ExplicitCitationBindingService().bind("[1][1]", result.getEvidenceManifest()).references())
            .hasSize(1);
    }

    @Test
    void snapshotContainsOnlyTheSnippetShownToTheModel() {
        properties.setTotalEvidenceMaxChars(5000);
        properties.setPerSubQuestionEvidenceMaxChars(5000);
        var result = service.assemble(plan(), context(List.of(reference("1", "a".repeat(1100) + "隐藏尾部"))));
        assertThat(result.getUserPrompt()).doesNotContain("隐藏尾部");
        assertThat(result.getEvidenceManifest().references().get(0).getSnippet()).isEqualTo("a".repeat(1100) + "...");
    }

    @Test
    void zeroBudgetDoesNotGrantCitationEligibility() {
        properties.setTotalEvidenceMaxChars(0);
        properties.setPerSubQuestionEvidenceMaxChars(0);
        assertThat(service.assemble(plan(), context(List.of(reference("1", "原文"))))
            .getEvidenceManifest().references()).isEmpty();
    }

    private ConversationExecutionPlan plan() {
        return ConversationExecutionPlan.builder().originalQuestion("问题").build();
    }

    private RagRetrievalContext context(List<SearchReference> references) {
        SubQuestionEvidence evidence = new SubQuestionEvidence();
        evidence.setSubQuestionIndex(1);
        evidence.setReferences(references);
        RagRetrievalContext context = new RagRetrievalContext();
        context.setSubQuestionEvidenceList(List.of(evidence));
        return context;
    }

    private SearchReference reference(String id, String snippet) {
        SearchReference reference = new SearchReference();
        reference.setReferenceId(id);
        reference.setChunkId(Long.valueOf(id));
        reference.setDocumentName("文档");
        reference.setSnippet(snippet);
        return reference;
    }
}
