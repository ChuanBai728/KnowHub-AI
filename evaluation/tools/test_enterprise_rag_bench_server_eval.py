import unittest
import json
import tempfile
from pathlib import Path
from unittest.mock import patch

from enterprise_rag_bench_server_eval import (
    extract_route_scope,
    load_rag_run_config,
    observation_completed,
    ranked_document_ids,
    replay_conversation,
    route_scope_snapshot,
    write_report,
)


# 真实的 ROUTE 阶段会出现两次：第一条是知识范围路由（带候选信息），第二条是执行路径
# 路由（只有 executionMode/navigationSummary）。下面的样本按真实结构构造。
ROUTING_ROUTE_STAGE = {
    "stageCode": "ROUTE",
    "executionMode": "AUTO_DOCUMENT",
    "durationMs": "0",
    "snapshot": {
        "confidence": "0.5080",
        "routeStatus": "LOW_CONFIDENCE",
        "topDocumentId": "2499372345785929460",
        "candidateDocumentCount": "100",
        "retrievalScopeDocumentCount": "100",
        "fullCorpusRetrieval": False,
        "knowledgeRouteBypassed": False,
    },
}
NAVIGATION_ROUTE_STAGE = {
    "stageCode": "ROUTE",
    "executionMode": "RETRIEVAL",
    "durationMs": "0",
    "snapshot": {
        "executionMode": "RETRIEVAL",
        "targetItemIndex": "",
        "navigationSummary": "forceRetrievalMode",
        "targetSectionHint": "",
    },
}


class RankedDocumentIdsTest(unittest.TestCase):

    def test_uses_rrf_rank_and_deduplicates_channel_rows(self):
        results = [
            {"documentId": "doc-b", "channelType": "keyword", "rrfRank": 2},
            {"documentId": "doc-a", "channelType": "vector", "rrfRank": 1},
            {"documentId": "doc-a", "channelType": "keyword", "rrfRank": 1},
        ]

        self.assertEqual(
            ["doc-a", "doc-b"],
            ranked_document_ids(results, ("rrfRank", "rrf_rank")),
        )

    def test_filters_selected_rows_and_orders_by_final_rank(self):
        results = [
            {"documentId": "doc-b", "selected": True, "finalRank": 2},
            {"documentId": "doc-a", "selected": False, "finalRank": None},
            {"documentId": "doc-c", "selected": True, "finalRank": 1},
        ]

        self.assertEqual(
            ["doc-c", "doc-b"],
            ranked_document_ids(
                results,
                ("finalRank", "final_rank"),
                selected_only=True,
            ),
        )

    def test_detects_explicit_and_legacy_transport_failures(self):
        self.assertTrue(observation_completed({"answer_status": "COMPLETED", "answer": "8小时"}))
        self.assertFalse(observation_completed({"answer_status": "FAILED", "answer": ""}))
        self.assertFalse(observation_completed({"answer": "Connection prematurely closed BEFORE response"}))

    @patch("enterprise_rag_bench_server_eval.wait_for_chat_terminal_status")
    def test_replay_waits_for_each_turn_to_finish(self, wait_for_status):
        wait_for_status.return_value = {"latestTurnStatus": "COMPLETED"}

        class Client:
            def __init__(self):
                self.payloads = []

            def stream_chat(self, payload, timeout):
                self.payloads.append((payload, timeout))
                return "answer", []

        client = Client()
        question = {
            "question_id": "TECH-MULTI-001",
            "question": "第二问",
            "chat_mode": "AUTO_DOCUMENT",
            "conversation": [
                {"role": "user", "content": "第一问"},
                {"role": "assistant", "content": "第一答"},
                {"role": "user", "content": "第二问"},
            ],
        }

        self.assertEqual(1, replay_conversation(client, question, "conv-1", {}, 45))
        wait_for_status.assert_called_once_with(client, "conv-1", timeout_seconds=45)
        self.assertEqual("第一问", client.payloads[0][0]["question"])

    @patch("enterprise_rag_bench_server_eval.wait_for_chat_terminal_status")
    def test_replay_fails_fast_when_prior_turn_failed(self, wait_for_status):
        wait_for_status.return_value = {
            "latestTurnStatus": "FAILED",
            "latestTurnError": "boom",
        }

        class Client:
            def stream_chat(self, payload, timeout):
                return "", []

        question = {
            "question_id": "TECH-MULTI-002",
            "question": "第二问",
            "chat_mode": "AUTO_DOCUMENT",
            "conversation": [
                {"role": "user", "content": "第一问"},
                {"role": "user", "content": "第二问"},
            ],
        }

        with self.assertRaisesRegex(RuntimeError, "boom"):
            replay_conversation(Client(), question, "conv-2", {}, 45)

    def test_write_report_uses_isolated_observation_file(self):
        with tempfile.TemporaryDirectory() as directory:
            output_dir = Path(directory)
            (output_dir / "gold.jsonl").write_text(
                json.dumps({"question_id": "q-1"}, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            observation = {
                "question_id": "q-1",
                "retrieval_expected": True,
                "expected_doc_ids": ["doc-1"],
                "retrieved_document_ids": ["doc-1"],
                "selected_document_ids": ["doc-1"],
            }
            (output_dir / "observations-custom.jsonl").write_text(
                json.dumps(observation, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )

            write_report(output_dir, "observations-custom.jsonl", "report-custom")

            report = json.loads((output_dir / "report-custom.json").read_text(encoding="utf-8"))
            self.assertEqual(1, report["completed"])
            self.assertEqual(1.0, report["recall@10"])
            self.assertTrue((output_dir / "report-custom.md").exists())
            self.assertFalse((output_dir / "report.json").exists())


class RouteScopeSnapshotTest(unittest.TestCase):

    def test_ignores_the_navigation_route_stage(self):
        snapshot = route_scope_snapshot([ROUTING_ROUTE_STAGE, NAVIGATION_ROUTE_STAGE])

        self.assertEqual("LOW_CONFIDENCE", snapshot["routeStatus"])
        self.assertEqual("100", snapshot["candidateDocumentCount"])

    def test_returns_empty_when_only_navigation_stage_is_present(self):
        self.assertEqual({}, route_scope_snapshot([NAVIGATION_ROUTE_STAGE]))
        self.assertEqual({}, route_scope_snapshot(None))

    def test_normalizes_string_counts_to_ints(self):
        scope = extract_route_scope(route_scope_snapshot([ROUTING_ROUTE_STAGE]))

        self.assertEqual(100, scope["retrieval_scope_document_count"])
        self.assertEqual(100, scope["route_candidate_document_count"])
        self.assertEqual(0.508, scope["route_confidence"])
        self.assertEqual("LOW_CONFIDENCE", scope["route_status"])
        self.assertFalse(scope["full_corpus_retrieval"])
        self.assertFalse(scope["knowledge_route_bypassed"])

    def test_returns_empty_for_missing_snapshot(self):
        self.assertEqual({}, extract_route_scope({}))

    def test_unparseable_counts_become_none(self):
        scope = extract_route_scope({"candidateDocumentCount": "not-a-number"})

        self.assertIsNone(scope["route_candidate_document_count"])


class LoadRagRunConfigTest(unittest.TestCase):

    CONFIG = """
app:
  chat:
    rag:
      force-retrieval-mode: false
      force-retrieval-full-corpus: false
      clarification-enabled: true
      low-confidence-route-document-top-k: 100
      rrf:
        k: 20
        vector-weight: 1.0
        keyword-weight: 0.55
        adaptive-keyword-weight-enabled: true
      answer-system-prompt: |
        这是一段巨型提示词，不应进入每条观测记录。
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
"""

    def test_captures_caliber_flags_and_excludes_prompt_text(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config.yaml"
            path.write_text(self.CONFIG, encoding="utf-8")

            config = load_rag_run_config(path)

        self.assertIs(False, config["force_retrieval_mode"])
        self.assertEqual(100, config["low_confidence_route_document_top_k"])
        self.assertEqual(20, config["rrf_k"])
        self.assertIs(True, config["rrf_adaptive_keyword_weight_enabled"])
        self.assertIn("NoLoggingImpl", config["sql_log_impl"])
        self.assertNotIn("answer_system_prompt", config)
        self.assertNotIn("answer-system-prompt", config)

    def test_missing_file_returns_empty(self):
        self.assertEqual({}, load_rag_run_config(Path("does-not-exist.yaml")))

    def test_unparseable_file_returns_empty(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config.yaml"
            path.write_text("app: [unclosed\n", encoding="utf-8")

            self.assertEqual({}, load_rag_run_config(path))

    def test_returns_empty_when_yaml_is_unavailable(self):
        with patch("enterprise_rag_bench_server_eval.yaml", None):
            self.assertEqual({}, load_rag_run_config(Path("anything.yaml")))


class WriteReportRetrievalScopeTest(unittest.TestCase):

    def test_surfaces_scope_counts_and_keeps_legacy_rows_working(self):
        with tempfile.TemporaryDirectory() as directory:
            output_dir = Path(directory)
            gold = [
                {"question_id": f"q-{index}", "component": "technical_docs", "expected_doc_ids": [f"doc-{index}"]}
                for index in (1, 2, 3)
            ]
            (output_dir / "gold.jsonl").write_text(
                "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in gold),
                encoding="utf-8",
            )
            # q-1 走扁平字段（高置信，20 篇），q-2 只有阶段快照（低置信，100 篇），
            # q-3 完全没有范围信息（旧格式，必须不报错、不产生 None）。
            observations = [
                {
                    "question_id": "q-1",
                    "retrieval_expected": True,
                    "expected_doc_ids": ["doc-1"],
                    "retrieved_document_ids": ["doc-1"],
                    "selected_document_ids": ["doc-1"],
                    "retrieval_scope_document_count": 20,
                    "route_status": "SUCCESS",
                },
                {
                    "question_id": "q-2",
                    "retrieval_expected": True,
                    "expected_doc_ids": ["doc-2"],
                    "retrieved_document_ids": ["doc-2"],
                    "selected_document_ids": ["doc-2"],
                    "exchange_detail": {"stageTraces": [ROUTING_ROUTE_STAGE]},
                },
                {
                    "question_id": "q-3",
                    "retrieval_expected": True,
                    "expected_doc_ids": ["doc-3"],
                    "retrieved_document_ids": [],
                    "selected_document_ids": [],
                },
            ]
            (output_dir / "observations-scope.jsonl").write_text(
                "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in observations),
                encoding="utf-8",
            )

            write_report(output_dir, "observations-scope.jsonl", "report-scope")

            report = json.loads((output_dir / "report-scope.json").read_text(encoding="utf-8"))
            self.assertEqual(1, report["high_confidence_route_cases"])
            self.assertEqual(1, report["low_confidence_route_cases"])
            self.assertEqual(60.0, report["avg_retrieval_scope_document_count"])
            self.assertEqual(20.0, report["avg_high_confidence_scope_document_count"])
            markdown = (output_dir / "report-scope.md").read_text(encoding="utf-8")
            self.assertIn("Avg retrieval scope document count", markdown)
            self.assertIn("High-confidence routes (SUCCESS)", markdown)

    def test_write_report_accepts_run_config_and_states_caliber(self):
        with tempfile.TemporaryDirectory() as directory:
            output_dir = Path(directory)
            (output_dir / "gold.jsonl").write_text(
                json.dumps({"question_id": "q-1"}, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            (output_dir / "observations-cfg.jsonl").write_text(
                json.dumps({
                    "question_id": "q-1",
                    "retrieval_expected": True,
                    "expected_doc_ids": ["doc-1"],
                    "retrieved_document_ids": ["doc-1"],
                    "selected_document_ids": ["doc-1"],
                }, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )

            write_report(
                output_dir,
                "observations-cfg.jsonl",
                "report-cfg",
                run_config={"force_retrieval_mode": True, "clarification_enabled": False},
            )

            report = json.loads((output_dir / "report-cfg.json").read_text(encoding="utf-8"))
            self.assertIs(True, report["run_config"]["force_retrieval_mode"])
            # 强制检索口径必须显式告警，不能被当成正常分数引用。
            markdown = (output_dir / "report-cfg.md").read_text(encoding="utf-8")
            self.assertIn("WARNING", markdown)
            self.assertIn("force-retrieval-mode=true", markdown)


if __name__ == "__main__":
    unittest.main()
