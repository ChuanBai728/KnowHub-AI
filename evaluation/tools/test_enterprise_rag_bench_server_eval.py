import unittest

from enterprise_rag_bench_server_eval import observation_completed, ranked_document_ids


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


if __name__ == "__main__":
    unittest.main()
