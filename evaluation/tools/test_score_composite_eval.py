import tempfile
import unittest
from pathlib import Path

from evaluation.tools.score_composite_eval import (
    answer_fact_groups,
    fact_matched,
    score_case,
    strict_fact_matched,
    summarize,
    write_markdown,
)


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


if __name__ == "__main__":
    unittest.main()
