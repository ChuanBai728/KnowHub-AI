import tempfile
import unittest
from pathlib import Path

from evaluation.tools.score_composite_eval import (
    CALIBER_FORCED_RETRIEVAL,
    CALIBER_ROUTING_ENABLED,
    CALIBER_UNKNOWN,
    answer_fact_groups,
    component_score,
    fact_matched,
    route_scope_metrics,
    run_caliber,
    run_config_of,
    score_case,
    strict_fact_matched,
    summarize,
    write_markdown,
)


# 与 test_enterprise_rag_bench_server_eval 使用同一组快照样本，锁住两份实现的判别一致。
ROUTING_ROUTE_STAGE = {
    "stageCode": "ROUTE",
    "executionMode": "AUTO_DOCUMENT",
    "snapshot": {
        "confidence": "0.5080",
        "routeStatus": "LOW_CONFIDENCE",
        "candidateDocumentCount": "100",
        "retrievalScopeDocumentCount": "100",
        "fullCorpusRetrieval": False,
        "knowledgeRouteBypassed": False,
    },
}
NAVIGATION_ROUTE_STAGE = {
    "stageCode": "ROUTE",
    "executionMode": "RETRIEVAL",
    "snapshot": {"executionMode": "RETRIEVAL", "navigationSummary": "forceRetrievalMode"},
}
HIGH_CONFIDENCE_SNAPSHOT = {
    "stageCode": "ROUTE",
    "snapshot": {
        "confidence": "0.7410",
        "routeStatus": "SUCCESS",
        "candidateDocumentCount": "20",
        "retrievalScopeDocumentCount": "20",
        "fullCorpusRetrieval": False,
        "knowledgeRouteBypassed": False,
    },
}


class CompositeScoreTest(unittest.TestCase):

    def test_tolerates_short_explanatory_insertions_but_keeps_strict_metric(self):
        answer = "系统默认保留 10 个历史版本，单个对象的大小上限为 1 MiB。"
        self.assertTrue(fact_matched("默认 10 个", answer))
        self.assertTrue(fact_matched("上限 1 MiB", answer))
        self.assertFalse(strict_fact_matched("默认 10 个", answer))

    def test_cmrc_answers_are_aliases_in_one_or_group(self):
        gold = {"component": "cmrc2018", "answer_facts": ["北京大学", "北大"]}
        self.assertEqual([["北京大学", "北大"]], answer_fact_groups(gold))

    def test_cmrc_passes_when_any_annotator_alias_matches(self):
        gold = {
            "question_id": "CMRC-test",
            "component": "cmrc2018",
            "question_type": "extractive_qa",
            "expected_doc_ids": [],
            "allowed_execution_modes": ["RETRIEVAL"],
            "answer_facts": ["北京大学", "北大"],
        }
        observation = {"answer": "文中所指的是北大。", "answer_status": "COMPLETED"}
        row = score_case(gold, observation, {})
        self.assertEqual(1.0, row["answer_pass"])
        self.assertEqual(1.0, row["strict_answer_pass"])

    def test_non_cmrc_facts_remain_and_requirements(self):
        gold = {
            "question_id": "TECH-test",
            "component": "technical_docs",
            "question_type": "procedural",
            "expected_doc_ids": [],
            "allowed_execution_modes": ["RETRIEVAL"],
            "answer_facts": ["第一步", "第二步"],
        }
        observation = {"answer": "只执行第一步。", "answer_status": "COMPLETED"}
        row = score_case(gold, observation, {})
        self.assertEqual(0.5, row["fact_recall"])
        self.assertEqual(0.0, row["answer_pass"])

    def test_accepts_conservative_technical_paraphrases(self):
        answer = (
            "机密数据不应该存放在 ConfigMap 中，应使用 Secret 资源来存储机密数据，"
            "或者借助其他第三方工具保证私密性。"
        )
        self.assertTrue(fact_matched("机密数据使用 Secret 或第三方工具", answer))
        self.assertTrue(fact_matched("可挂载存储卷", "超过限制时建议考虑挂载存储卷。"))
        self.assertTrue(fact_matched("可使用独立数据库或文件服务", "也可以使用独立的数据库或文件服务。"))
        self.assertTrue(fact_matched("需要重启 Pod", "必须重新启动 Pod 才能生效。"))
        self.assertTrue(fact_matched(
            "非可选 Secret 未就绪时容器不启动",
            "在所有非可选 Secret 都可用之前，Pod 的所有容器都不会启动。",
        ))
        self.assertTrue(fact_matched(
            "可以提供任意字符串",
            "stringData 字段中可以使用任何字符串作为其取值。",
        ))
        self.assertTrue(fact_matched(
            "存储在 etcd",
            "Secret 未加密地存储在 API 服务器的底层数据存储（etcd）中。",
        ))
        self.assertTrue(fact_matched(
            "默认未加密",
            "默认情况下，Kubernetes Secret 未加密地存储在 etcd 中。",
        ))
        self.assertTrue(fact_matched(
            "最小特权和 RBAC",
            "以最小特权访问 Secret 并启用或配置 RBAC 规则。",
        ))
        self.assertTrue(fact_matched(
            "限制到特定容器",
            "限制 Secret 对特定容器的访问。",
        ))

    def test_paraphrase_matching_preserves_negation(self):
        self.assertFalse(fact_matched("默认未加密", "Secret 默认加密存储。"))
        self.assertFalse(fact_matched(
            "非可选 Secret 未就绪时容器不启动",
            "非可选 Secret 可用后，容器会启动。",
        ))

    def test_reports_route_and_full_corpus_retrieval_separately(self):
        gold = {
            "question_id": "T2-test",
            "component": "t2retrieval",
            "question_type": "retrieval",
            "expected_doc_ids": ["doc-gold"],
            "allowed_execution_modes": ["RETRIEVAL"],
            "answer_facts": [],
        }
        observation = {
            "answer": "ok",
            "answer_status": "COMPLETED",
            "route_document_ids": ["doc-other"],
            "retrieved_document_ids": ["doc-gold"],
            "exchange_detail": {
                "stageTraces": [{"snapshot": {"fullCorpusRetrieval": True}}]
            },
        }

        row = score_case(gold, observation, {})
        self.assertEqual("FULL_CORPUS", row["retrieval_scope"])
        self.assertEqual(0.0, row["route_recall@20"])
        self.assertEqual(1.0, row["recall@10"])

    def test_reports_hit_at_one_three_and_five(self):
        gold = {
            "question_id": "T2-hit-test",
            "component": "t2retrieval",
            "question_type": "retrieval",
            "expected_doc_ids": ["doc-gold"],
            "allowed_execution_modes": ["RETRIEVAL"],
            "answer_facts": [],
        }
        observation = {
            "answer": "ok",
            "answer_status": "COMPLETED",
            "retrieved_document_ids": ["doc-other-1", "doc-other-2", "doc-gold"],
        }

        row = score_case(gold, observation, {})
        self.assertEqual(0.0, row["hit@1"])
        self.assertEqual(1.0, row["hit@3"])
        self.assertEqual(1.0, row["hit@5"])
        metrics = summarize([row])
        self.assertEqual(0.0, metrics["hit@1"])
        self.assertEqual(1.0, metrics["hit@3"])
        self.assertEqual(1.0, metrics["hit@5"])

    def test_writes_single_component_subset_report_as_non_official_score(self):
        metrics = summarize([])
        metrics["score"] = 12.5
        result = {
            "score_scope": "observed_subset",
            "overall": summarize([]),
            "components": {"technical_docs": metrics},
            "composite_score": 12.5,
        }

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "metrics-smoke.md"
            write_markdown(path, result)
            report = path.read_text(encoding="utf-8")

        self.assertIn("非正式子集分：12.50/100", report)
        self.assertIn("| technical_docs |", report)
        self.assertNotIn("| t2retrieval |", report)
        self.assertIn("不是正式四子集等权综合分", report)


class ComponentScoreFormulaTest(unittest.TestCase):
    """锁住 component_score 与 score_formula 里写死的权重一致。"""

    METRICS = {
        "recall@10": 0.5,
        "mrr@10": 0.4,
        "final_recall@5": 0.3,
        "answer_pass_rate": 0.8,
        "grounded_citation_rate": 0.9,
        "recall@5": 0.7,
        "abstention_pass_rate": 0.6,
        "forbidden_claim_avoidance_rate": 1.0,
        "route_accuracy": 0.46,
    }

    def test_weights_match_the_documented_formulas(self):
        self.assertAlmostEqual(
            0.45 * 0.5 + 0.35 * 0.4 + 0.20 * 0.3,
            component_score("t2retrieval", self.METRICS),
        )
        self.assertAlmostEqual(
            0.60 * 0.8 + 0.20 * 0.9 + 0.20 * 0.7,
            component_score("cmrc2018", self.METRICS),
        )
        self.assertAlmostEqual(
            0.50 * 0.8 + 0.25 * 0.6 + 0.25 * 1.0,
            component_score("rgb", self.METRICS),
        )
        self.assertAlmostEqual(
            0.45 * 0.8 + 0.20 * 0.9 + 0.35 * 0.46,
            component_score("technical_docs", self.METRICS),
        )

    def test_unknown_component_scores_zero(self):
        self.assertEqual(0.0, component_score("nope", self.METRICS))


class RunCaliberTest(unittest.TestCase):

    def test_unknown_when_no_record_is_stamped(self):
        self.assertEqual(CALIBER_UNKNOWN, run_caliber({}))
        self.assertEqual(CALIBER_UNKNOWN, run_caliber({"clarification_enabled": True}))

    def test_detects_forced_and_enabled_routing(self):
        self.assertEqual(CALIBER_FORCED_RETRIEVAL, run_caliber({"force_retrieval_mode": True}))
        self.assertEqual(CALIBER_ROUTING_ENABLED, run_caliber({"force_retrieval_mode": False}))

    def test_run_config_of_skips_unstamped_records(self):
        observations = [
            {"question_id": "q-1"},
            {"question_id": "q-2", "run_config": {"force_retrieval_mode": True}},
        ]
        self.assertEqual({"force_retrieval_mode": True}, run_config_of(observations))
        self.assertEqual({}, run_config_of([{"question_id": "q-1"}]))


class RouteScopeMetricsTest(unittest.TestCase):

    def test_prefers_flat_fields_over_stage_traces(self):
        observation = {
            "retrieval_scope_document_count": 7,
            "route_status": "SUCCESS",
            "exchange_detail": {"stageTraces": [ROUTING_ROUTE_STAGE]},
        }
        scope = route_scope_metrics(observation)

        self.assertEqual(7, scope["retrieval_scope_document_count"])
        self.assertEqual("high", scope["route_confidence_bucket"])

    def test_falls_back_to_the_routing_stage_snapshot(self):
        observation = {"exchange_detail": {"stageTraces": [NAVIGATION_ROUTE_STAGE, ROUTING_ROUTE_STAGE]}}
        scope = route_scope_metrics(observation)

        self.assertEqual(100, scope["retrieval_scope_document_count"])
        self.assertEqual("LOW_CONFIDENCE", scope["route_status"])
        self.assertEqual("low", scope["route_confidence_bucket"])

    def test_missing_scope_yields_none_not_a_guess(self):
        scope = route_scope_metrics({})

        self.assertIsNone(scope["retrieval_scope_document_count"])
        self.assertEqual("unknown", scope["route_confidence_bucket"])

    def test_buckets_matches_the_backend_confidence_split(self):
        self.assertEqual("high", route_scope_metrics({"route_status": "SUCCESS"})["route_confidence_bucket"])
        self.assertEqual(
            "low", route_scope_metrics({"route_status": "LOW_CONFIDENCE"})["route_confidence_bucket"]
        )


class RetrievalScopeSummaryTest(unittest.TestCase):

    @staticmethod
    def _row(question_id: str, stage: dict) -> dict:
        gold = {
            "question_id": question_id,
            "component": "technical_docs",
            "question_type": "fact_qa",
            "expected_doc_ids": ["doc-1"],
            "allowed_execution_modes": ["RETRIEVAL"],
            "answer_facts": [],
        }
        observation = {
            "answer": "ok",
            "answer_status": "COMPLETED",
            "retrieved_document_ids": ["doc-1"],
            "exchange_detail": {"stageTraces": [stage]},
        }
        return score_case(gold, observation, {})

    def test_splits_by_confidence_so_the_average_cannot_hide_the_tail(self):
        rows = [
            self._row("q-high-1", HIGH_CONFIDENCE_SNAPSHOT),
            self._row("q-high-2", HIGH_CONFIDENCE_SNAPSHOT),
            self._row("q-low-1", ROUTING_ROUTE_STAGE),
        ]

        scope = summarize(rows)["retrieval_scope"]

        self.assertEqual(3, scope["cases"])
        self.assertEqual(20.0, scope["high_confidence_scope_mean"])
        self.assertEqual(2, scope["high_confidence_cases"])
        self.assertEqual(100.0, scope["low_confidence_scope_mean"])
        self.assertEqual(1, scope["low_confidence_cases"])
        # 22.0 的总体均值会掩盖"多数问题其实退回 100 篇兜底"，所以分组是必须的。
        self.assertAlmostEqual((20 + 20 + 100) / 3, scope["scope_document_count_mean"])
        self.assertEqual(20.0, scope["scope_document_count_median"])

    def test_scope_metrics_are_never_none(self):
        scope = summarize([])["retrieval_scope"]

        for key, value in scope.items():
            self.assertIsNotNone(value, key)

    def test_route_accuracy_stays_numeric_but_is_flagged_when_not_applicable(self):
        rows = [self._row("q-1", ROUTING_ROUTE_STAGE)]

        forced = summarize(rows, route_applicable=False)

        self.assertIs(False, forced["route_accuracy_applicable"])
        # 不静默改分：数字照常输出（此处执行模式无法确定，故为 0.0），由报告显式告警。
        self.assertIsNotNone(forced["route_accuracy"])
        self.assertEqual(0.0, forced["route_accuracy"])
        self.assertIs(True, summarize(rows)["route_accuracy_applicable"])


class MarkdownCaliberRenderingTest(unittest.TestCase):

    @staticmethod
    def _result(**overrides) -> dict:
        metrics = summarize([])
        metrics["score"] = 12.5
        result = {
            "score_scope": "observed_subset",
            "overall": summarize([]),
            "components": {"technical_docs": metrics},
            "composite_score": 12.5,
        }
        result.update(overrides)
        return result

    def _render(self, result: dict) -> str:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "metrics.md"
            write_markdown(path, result)
            return path.read_text(encoding="utf-8")

    def test_legacy_result_renders_exactly_as_before(self):
        report = self._render(self._result())

        self.assertNotIn("运行口径", report)
        self.assertNotIn("检索范围", report)
        self.assertNotIn("N/A", report)

    def test_states_caliber_and_warns_on_forced_retrieval(self):
        report = self._render(self._result(
            run_caliber=CALIBER_FORCED_RETRIEVAL,
            run_config={"force_retrieval_mode": True, "clarification_enabled": False},
            route_accuracy_applicable=False,
            warnings=["强制检索口径下路由准确率不可比。"],
        ))

        self.assertIn("本次运行口径", report)
        self.assertIn("force-retrieval-mode=True", report)
        self.assertIn("**注意**", report)

    def test_renders_retrieval_scope_bullets_only_when_data_exists(self):
        result = self._result()
        result["overall"]["retrieval_scope"] = summarize([])["retrieval_scope"]
        result["overall"]["retrieval_scope"].update({
            "cases": 3,
            "scope_document_count_mean": 46.67,
            "scope_document_count_median": 20.0,
            "scope_document_count_p95": 100.0,
            "high_confidence_cases": 2,
            "high_confidence_scope_mean": 20.0,
            "high_confidence_scope_median": 20.0,
            "low_confidence_cases": 1,
            "low_confidence_scope_mean": 100.0,
            "low_confidence_scope_median": 100.0,
        })

        report = self._render(result)

        self.assertIn("检索范围（3 题有记录）", report)
        self.assertIn("高置信路由（2 题）", report)
        self.assertIn("低置信路由（1 题）", report)


if __name__ == "__main__":
    unittest.main()
