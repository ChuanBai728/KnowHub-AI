import json
import unittest
from pathlib import Path

from evaluation.tools.build_composite_eval import (
    answer_anchor,
    evenly_spaced,
    parse_jsonl,
    slug,
    unique_strings,
    usable_zh_passages,
)
from evaluation.tools.validate_composite_eval import validate


class CompositeEvalUtilityTest(unittest.TestCase):
    def test_evenly_spaced_is_deterministic_and_covers_range(self):
        values = list(range(100))
        self.assertEqual([0, 20, 40, 60, 80], evenly_spaced(values, 5))

    def test_jsonl_and_string_normalization(self):
        rows = parse_jsonl('{"id": 1}\n\n{"id": 2}\n', "fixture")
        self.assertEqual([1, 2], [row["id"] for row in rows])
        self.assertEqual(["a", "b"], unique_strings(["a", " a ", "", "b"]))

    def test_answer_anchor_prefers_answer_span(self):
        self.assertEqual("北京", answer_anchor("首都是北京。", ["北京"]))
        self.assertEqual("没有直接答案", answer_anchor("没有直接答案", ["上海"]))

    def test_slug_is_file_safe(self):
        self.assertEqual("a-b-c", slug("a/b c"))

    def test_usable_zh_passages_rejects_corrupt_or_non_chinese_text(self):
        valid = "这是一段长度足够的中文材料，用来确认评测数据清洗逻辑能够保留正常内容。"
        corrupt = "这是一段长度足够但包含替换字符的中文材料，应该被过滤掉。\ufffd"
        self.assertEqual([valid], usable_zh_passages([valid, corrupt, "English only"]))

    def test_generated_dataset_passes_strict_validation(self):
        dataset_dir = (
            Path(__file__).resolve().parents[1]
            / "datasets"
            / "knowhub-cn-composite-rag-v1"
        )
        if not dataset_dir.is_dir():
            self.skipTest("generated composite dataset is not present")
        report = validate(dataset_dir)
        self.assertTrue(report["valid"], report["errors"])


if __name__ == "__main__":
    unittest.main()
