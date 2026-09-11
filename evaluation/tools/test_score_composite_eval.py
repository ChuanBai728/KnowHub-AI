import unittest

from evaluation.tools.score_composite_eval import (
    answer_fact_groups,
    fact_matched,
    score_case,
    strict_fact_matched,
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


if __name__ == "__main__":
    unittest.main()
