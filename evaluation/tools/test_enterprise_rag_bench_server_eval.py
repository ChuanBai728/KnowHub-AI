import unittest
from unittest.mock import patch

from enterprise_rag_bench_server_eval import (
    observation_completed,
    ranked_document_ids,
    replay_conversation,
)


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


if __name__ == "__main__":
    unittest.main()
