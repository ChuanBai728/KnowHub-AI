package ai.knowhub.chat.rag.service;

import ai.knowhub.chat.rag.config.ChatRagProperties;
import ai.knowhub.document.model.KnowledgeDocumentDescriptor;
import ai.knowhub.document.model.route.DocumentRouteCandidate;
import ai.knowhub.document.model.route.KnowledgeRouteDecision;
import ai.knowhub.document.service.DocumentKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatPreparationOrchestratorTest {

    private ChatRagProperties properties;
    private DocumentKnowledgeService documentKnowledgeService;
    private ChatPreparationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        properties = new ChatRagProperties();
        properties.setRouteDocumentTopK(1);
        properties.setLowConfidenceRouteDocumentTopK(3);
        documentKnowledgeService = mock(DocumentKnowledgeService.class);
        when(documentKnowledgeService.listRetrievableDocuments()).thenReturn(List.of(
            descriptor(1L, "Kubernetes 调度器配置", 101L, "platform"),
            descriptor(2L, "Dubbo 注册中心参考", 102L, "platform"),
            descriptor(3L, "采购审批制度", 103L, "finance"),
            descriptor(4L, "Kubernetes Secret", 104L, "platform")
        ));
        orchestrator = new ChatPreparationOrchestrator(
            properties, null, null, null, null, null, documentKnowledgeService
        );
    }

    @Test
    void lowConfidenceExpandsCandidatesInsteadOfKeepingNormalTopK() {
        KnowledgeRouteDecision decision = decision(
            new BigDecimal("0.30"),
            candidate("2", "Dubbo 注册中心参考", "102", "platform", 8)
        );

        List<DocumentRouteCandidate> candidates = orchestrator.selectAutoCandidates(
            decision, "注册中心如何配置", "注册中心如何配置"
        );

        assertEquals(3, candidates.size());
    }

    @Test
    void explicitDocumentNameTakesPriorityAndDoesNotClarify() {
        KnowledgeRouteDecision decision = decision(
            new BigDecimal("0.20"),
            candidate("3", "采购审批制度", "103", "finance", 10)
        );

        List<DocumentRouteCandidate> candidates = orchestrator.selectAutoCandidates(
            decision,
            "请根据《Kubernetes 调度器配置》说明步骤",
            "Kubernetes 调度器配置步骤"
        );

        assertEquals("1", candidates.get(0).getDocumentId());
        assertFalse(orchestrator.shouldAskClarification(decision, candidates, "请根据 Kubernetes 调度器配置说明步骤"));
    }

    @Test
    void lowConfidenceWithinOneScopeContinuesRetrieval() {
        KnowledgeRouteDecision decision = decision(
            new BigDecimal("0.20"),
            candidate("1", "文档甲", "101", "platform", 10),
            candidate("2", "文档乙", "102", "platform", 9)
        );

        assertFalse(orchestrator.shouldAskClarification(decision, decision.getDocuments(), "注册中心默认端口是多少"));
    }

    @Test
    void closeCandidatesAcrossScopesStillClarify() {
        KnowledgeRouteDecision decision = decision(
            new BigDecimal("0.20"),
            candidate("1", "文档甲", "101", "platform", 10),
            candidate("3", "文档乙", "103", "finance", 9)
        );

        assertTrue(orchestrator.shouldAskClarification(decision, decision.getDocuments(), "默认端口是多少"));
    }

    @Test
    void fullCorpusCandidatesContainEveryRetrievableDocument() {
        List<DocumentRouteCandidate> candidates = orchestrator.fullCorpusCandidates();

        assertEquals(List.of("1", "2", "3", "4"), candidates.stream()
            .map(DocumentRouteCandidate::getDocumentId)
            .toList());
    }

    @Test
    void forcedFullCorpusRetrievalBypassesKnowledgeRoute() {
        properties.setForceRetrievalMode(true);
        properties.setForceRetrievalFullCorpus(true);

        assertTrue(orchestrator.shouldBypassKnowledgeRoute(ai.knowhub.enums.ChatQueryMode.AUTO_DOCUMENT));
        assertFalse(orchestrator.shouldBypassKnowledgeRoute(ai.knowhub.enums.ChatQueryMode.DOCUMENT));

        properties.setForceRetrievalMode(false);
        assertFalse(orchestrator.shouldBypassKnowledgeRoute(ai.knowhub.enums.ChatQueryMode.AUTO_DOCUMENT));
    }

    @Test
    void lowConfidenceBroadDiagnosticQuestionStillClarifies() {
        KnowledgeRouteDecision decision = decision(
            new BigDecimal("0.20"),
            candidate("1", "文档甲", "101", "platform", 10),
            candidate("2", "文档乙", "102", "platform", 9)
        );

        assertTrue(orchestrator.shouldAskClarification(
            decision,
            decision.getDocuments(),
            "Pod 有问题，我该怎么调试？"
        ));
    }

    @Test
    void contextualFollowUpDoesNotClarifyAgain() {
        KnowledgeRouteDecision decision = decision(
            new BigDecimal("0.20"),
            candidate("1", "文档甲", "101", "platform", 10),
            candidate("2", "文档乙", "102", "platform", 9)
        );

        assertFalse(orchestrator.shouldAskClarification(
            decision,
            decision.getDocuments(),
            "不想自己做这种编码转换时该用哪个字段？",
            true
        ));
    }

    @Test
    void routingHistoryPromotesUniquelyNamedTechnicalDocument() {
        KnowledgeRouteDecision decision = decision(
            new BigDecimal("0.20"),
            candidate("2", "Dubbo 注册中心参考", "102", "platform", 10)
        );

        List<DocumentRouteCandidate> candidates = orchestrator.selectAutoCandidates(
            decision,
            "不想自己做这种编码转换时该用哪个字段？",
            "不想自己做这种编码转换时该用哪个字段？",
            "用户：Secret 的 data 字段值需要采用 base64 编码。"
        );

        assertEquals("4", candidates.get(0).getDocumentId());
    }

    @Test
    void titleMatchingHandlesVendorPrefixAndNaturalTitleVariants() {
        assertTrue(orchestrator.explicitDocumentTitleScore(
            "Apache Dubbo 服务发现", "Dubbo 服务发现文档中介绍了什么"
        ) > 0D);
        assertTrue(orchestrator.explicitDocumentTitleScore(
            "调试运行中的 Pod", "调试运行中 Pod 的文档里日志命令在哪"
        ) > 0D);
        assertTrue(orchestrator.explicitDocumentTitleScore(
            "部署 Dubbo 应用到 Kubernetes", "Dubbo Kubernetes 部署文档"
        ) > 0D);
        assertTrue(orchestrator.explicitDocumentTitleScore(
            "Kubernetes ConfigMap", "ConfigMap 以环境变量方式注入 Pod"
        ) > 0D);
        assertTrue(orchestrator.explicitDocumentTitleScore(
            "Kubernetes Secret", "Secret 的 stringData 字段"
        ) > 0D);
        assertEquals(0D, orchestrator.explicitDocumentTitleScore(
            "采购审批制度", "Kubernetes ConfigMap 如何使用"
        ));
    }

    @Test
    void injectsRouteHistoryOnlyWhenThereIsAPreviousTurn() {
        assertEquals("", orchestrator.resolveRouteHistory(""));
        assertEquals("", orchestrator.resolveRouteHistory("   "));
        assertEquals("", orchestrator.resolveRouteHistory(null));
    }

    @Test
    void clipsRouteHistoryFromTheTailAndKeepsItWithinBudget() {
        properties.setRouteHistoryMaxChars(20);
        String history = "旧话题的内容很长很长" + "最近一轮用户问题在这里";

        String resolved = orchestrator.resolveRouteHistory(history);

        assertEquals(20, resolved.length());
        assertTrue(resolved.startsWith("…"), resolved);
        // 尾部是最近的对话，必须保留；头部旧内容被丢弃。
        assertTrue(resolved.endsWith("最近一轮用户问题在这里"), resolved);
    }

    @Test
    void routeHistoryCanBeDisabledByConfiguration() {
        properties.setRouteHistoryMaxChars(0);

        assertEquals("", orchestrator.resolveRouteHistory("用户：上一轮的问题"));
    }

    @Test
    void keepsShortRouteHistoryIntact() {
        properties.setRouteHistoryMaxChars(200);

        assertEquals("用户：上一轮的问题", orchestrator.resolveRouteHistory("用户：上一轮的问题"));
    }

    private KnowledgeDocumentDescriptor descriptor(long documentId,
                                                   String name,
                                                   long taskId,
                                                   String scope) {
        return new KnowledgeDocumentDescriptor(documentId, name, taskId, scope, scope, "", "");
    }

    private KnowledgeRouteDecision decision(BigDecimal confidence,
                                            DocumentRouteCandidate... candidates) {
        KnowledgeRouteDecision decision = new KnowledgeRouteDecision();
        decision.setConfidence(confidence);
        decision.setDocuments(List.of(candidates));
        return decision;
    }

    private DocumentRouteCandidate candidate(String documentId,
                                             String name,
                                             String taskId,
                                             String scope,
                                             int score) {
        return new DocumentRouteCandidate(
            documentId, name, taskId, scope, scope, "", "", BigDecimal.valueOf(score), "route"
        );
    }
}
