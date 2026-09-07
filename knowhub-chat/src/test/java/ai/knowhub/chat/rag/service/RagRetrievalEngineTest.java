package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.model.ChannelExecutionVo;
import ai.knowhub.chat.model.RetrievalResultVo;
import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.chat.rag.model.ConversationExecutionPlan;
import ai.knowhub.chat.rag.model.RagRetrievalContext;
import ai.knowhub.chat.rag.model.SubQuestionEvidence;
import ai.knowhub.chat.rag.retrieve.channel.RetrievalChannel;
import ai.knowhub.chat.rag.retrieve.channel.RetrievalChannelResult;
import ai.knowhub.chat.service.ConversationTraceRecorder;
import ai.knowhub.document.service.DocumentKnowledgeService;
import ai.knowhub.document.support.DocumentKnowledgeMetadataKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalEngineTest {

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @AfterEach
    void shutdownExecutor() {
        executorService.shutdownNow();
    }

    @Test
    void keepsChunksUntilRerankThenDeduplicatesDocumentsAndRecordsParentSelection() {
        Document distractor = childDocument(101L, 1001L, 1L, "同一文档中的前置说明", 0.95);
        Document target = childDocument(102L, 1002L, 1L, "真正命中问题的阈值条款", 0.90);
        Document other = childDocument(201L, 2001L, 2L, "另一份文档的补充证据", 0.85);

        RetrievalChannel vector = mock(RetrievalChannel.class);
        when(vector.channelName()).thenReturn("vector");
        when(vector.supports(any(ConversationExecutionPlan.class))).thenReturn(true);
        when(vector.retrieve(any(String.class), any(ConversationExecutionPlan.class)))
            .thenReturn(new RetrievalChannelResult("vector", List.of(distractor, target, other)));

        DocumentKnowledgeService knowledgeService = mock(DocumentKnowledgeService.class);
        when(knowledgeService.elevateToParentBlocks(anyList(), anyInt())).thenAnswer(invocation -> {
            List<Document> children = invocation.getArgument(0);
            List<Document> parents = new ArrayList<>();
            for (Document child : children) {
                Map<String, Object> metadata = new HashMap<>(child.getMetadata());
                parents.add(Document.builder()
                    .id("parent-" + metadata.get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID))
                    .text("父块：" + child.getText())
                    .metadata(metadata)
                    .score(child.getScore())
                    .build());
            }
            return parents;
        });

        HttpDocumentRerankPostProcessor reranker = mock(HttpDocumentRerankPostProcessor.class);
        when(reranker.process(any(Query.class), anyList())).thenAnswer(invocation -> {
            List<Document> candidates = invocation.getArgument(1);
            Document targetParent = findByChunkId(candidates, 102L);
            Document distractorParent = findByChunkId(candidates, 101L);
            Document otherParent = findByChunkId(candidates, 201L);
            targetParent.getMetadata().put("rerankScore", 0.99D);
            return List.of(targetParent, otherParent, distractorParent);
        });

        ChatRagProperties properties = new ChatRagProperties();
        properties.setCandidateTopK(20);
        properties.setPreRerankTopK(20);
        properties.setFinalTopK(3);
        properties.setMinVectorSimilarity(0.0);
        properties.setRerankEnabled(true);
        properties.setDocumentLevelDedupEnabled(true);

        RagRetrievalEngine engine = new RagRetrievalEngine(
            List.of(vector), properties, reranker, knowledgeService, executorService
        );
        ConversationTraceRecorder recorder = mock(ConversationTraceRecorder.class);
        when(recorder.exchangeId()).thenReturn(42L);
        when(recorder.traceId()).thenReturn("trace-42");

        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .retrievalQuestion("阈值是多少")
            .retrievalSubQuestions(List.of("阈值是多少"))
            .build();

        RagRetrievalContext context = engine.retrieve(plan, recorder);
        SubQuestionEvidence evidence = context.getSubQuestionEvidenceList().get(0);

        assertThat(evidence.getFusedCandidateCount()).isEqualTo(3);
        assertThat(evidence.getDocuments())
            .extracting(document -> document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_ID))
            .containsExactly(102L, 201L, 101L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RetrievalResultVo>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(recorder).recordRetrievalResults(resultCaptor.capture());
        RetrievalResultVo targetObservation = resultCaptor.getValue().stream()
            .filter(item -> Long.valueOf(102L).equals(item.getChunkId()))
            .findFirst()
            .orElseThrow();
        assertThat(targetObservation.isSelected()).isTrue();
        assertThat(targetObservation.getFinalRank()).isEqualTo(1);
        assertThat(targetObservation.getRrfRank()).isEqualTo(2);
        assertThat(targetObservation.getRerankScore()).isEqualByComparingTo("0.99");
        assertThat(targetObservation.getParentBlockId()).isEqualTo(1002L);
        assertThat(targetObservation.isElevated()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChannelExecutionVo>> channelCaptor = ArgumentCaptor.forClass(List.class);
        verify(recorder).recordChannelExecutions(channelCaptor.capture());
        ChannelExecutionVo execution = channelCaptor.getValue().get(0);
        assertThat(execution.getExecutionState()).isEqualTo(2);
        assertThat(execution.getStartTime()).isNotNull();
        assertThat(execution.getEndTime()).isNotNull();
        assertThat(execution.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(execution.getFinalSelectedCount()).isEqualTo(3);
    }

    @Test
    void promotesSameDocumentEvidenceThatAddsAnUncoveredQuestionAnchor() {
        Document lodging = childDocument(101L, 1001L, 1L,
            "住宿费含税上限：A类城市650元，B类城市480元", 0.95);
        Document cover = childDocument(102L, 1002L, 1L,
            "差旅制度，版本3.0，责任部门财务部", 0.90);
        Document unrelatedA = childDocument(201L, 2001L, 2L, "采购审批规则", 0.85);
        Document unrelatedB = childDocument(301L, 3001L, 3L, "合同付款规则", 0.80);
        Document unrelatedC = childDocument(401L, 4001L, 4L, "访客登记规则", 0.75);
        Document cityClass = childDocument(103L, 1003L, 1L,
            "城市分级：A类城市包括北京、上海、广州、深圳", 0.70);

        RetrievalChannel vector = mock(RetrievalChannel.class);
        when(vector.channelName()).thenReturn("vector");
        when(vector.supports(any(ConversationExecutionPlan.class))).thenReturn(true);
        when(vector.retrieve(any(String.class), any(ConversationExecutionPlan.class)))
            .thenReturn(new RetrievalChannelResult("vector",
                List.of(lodging, cover, unrelatedA, unrelatedB, unrelatedC, cityClass)));

        DocumentKnowledgeService knowledgeService = mock(DocumentKnowledgeService.class);
        when(knowledgeService.elevateToParentBlocks(anyList(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        HttpDocumentRerankPostProcessor reranker = mock(HttpDocumentRerankPostProcessor.class);
        when(reranker.process(any(Query.class), anyList())).thenReturn(
            List.of(lodging, cover, unrelatedA, unrelatedB, unrelatedC, cityClass)
        );

        ChatRagProperties properties = new ChatRagProperties();
        properties.setFinalTopK(5);
        properties.setMinVectorSimilarity(0.0);
        properties.setRerankEnabled(true);
        properties.setDocumentLevelDedupEnabled(true);
        properties.setMaxEvidencePerDocument(2);

        RagRetrievalEngine engine = new RagRetrievalEngine(
            List.of(vector), properties, reranker, knowledgeService, executorService
        );
        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .retrievalQuestion("到深圳出差，住宿费含税上限是多少？")
            .retrievalSubQuestions(List.of("到深圳出差，住宿费含税上限是多少？"))
            .build();

        List<Document> selected = engine.retrieve(plan, null)
            .getSubQuestionEvidenceList().get(0).getDocuments();

        assertThat(selected)
            .extracting(document -> document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_ID))
            .containsExactly(101L, 103L, 201L, 301L, 401L);
    }

    private static Document childDocument(long chunkId, long parentBlockId, long documentId,
                                           String text, double score) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_ID, chunkId);
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID, parentBlockId);
        metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_ID, documentId);
        metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME, "文档-" + documentId);
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, score);
        return Document.builder()
            .id(String.valueOf(chunkId))
            .text(text)
            .metadata(metadata)
            .score(score)
            .build();
    }

    private static Document findByChunkId(List<Document> documents, long chunkId) {
        return documents.stream()
            .filter(document -> Long.valueOf(chunkId).equals(
                document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_ID)))
            .findFirst()
            .orElseThrow();
    }
}
