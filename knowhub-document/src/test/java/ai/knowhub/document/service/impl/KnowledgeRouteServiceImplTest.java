package ai.knowhub.document.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由文本拼装的单元测试。
 *
 * 背景：多轮追问常常只有指代词、没有独立主谓（例如"P2C 负载均衡下节点的哪一个最好选一个？"）。
 * 若只用问题本身去路由，会命中泛化词并把正确文档挤出候选池——实测 50 道技术文档题里，
 * 全部 2 例漏召回都出自这类追问。因此路由文本需要并入最近历史。
 */
class KnowledgeRouteServiceImplTest {

    @Test
    void appendsHistoryAfterTheQuestionSoTheQuestionStaysDominant() {
        String routingText = KnowledgeRouteServiceImpl.buildRoutingText(
            "P2C 负载均衡下节点的哪一个最好选一个？",
            "P2C 负载均衡下节点的哪一个最好选一个？",
            "用户：P2C 的第一个是不是最好选择，比如网络节点？"
        );

        assertEquals(
            "P2C 负载均衡下节点的哪一个最好选一个？ 用户：P2C 的第一个是不是最好选择，比如网络节点？",
            routingText
        );
    }

    @Test
    void keepsOriginalBehaviourWhenHistoryIsAbsent() {
        assertEquals(
            "注册中心如何配置",
            KnowledgeRouteServiceImpl.buildRoutingText("注册中心如何配置", "注册中心如何配置", "")
        );
        assertEquals(
            "注册中心如何配置",
            KnowledgeRouteServiceImpl.buildRoutingText("注册中心如何配置", "", null)
        );
    }

    @Test
    void stillJoinsTheRewriteWhenItDiffers() {
        assertEquals(
            "注册中心如何配置 如何配置 Dubbo 注册中心",
            KnowledgeRouteServiceImpl.buildRoutingText(
                "注册中心如何配置", "如何配置 Dubbo 注册中心", "")
        );
    }

    @Test
    void usesHistoryAsTheWholeTextWhenTheQuestionIsBlank() {
        assertEquals(
            "用户：上一轮的问题",
            KnowledgeRouteServiceImpl.buildRoutingText("", "", "用户：上一轮的问题")
        );
    }

    @Test
    void collapsesNewlinesInHistorySoTheyDoNotDisturbTokenizing() {
        String routingText = KnowledgeRouteServiceImpl.buildRoutingText(
            "问题", "问题", "### 最近对话原文\n用户：上一轮\n回答：内容"
        );

        assertFalse(routingText.contains("\n"), routingText);
        assertTrue(routingText.startsWith("问题 "), routingText);
        assertTrue(routingText.contains("用户：上一轮"), routingText);
    }
}
