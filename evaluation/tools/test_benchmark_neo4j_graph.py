import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from benchmark_neo4j_graph import latency_summary, parse_neo4j_config, percentile


class Neo4jGraphBenchmarkTest(unittest.TestCase):

    def test_percentile_interpolates_and_summarizes(self):
        self.assertEqual(2.5, percentile([1.0, 2.0, 3.0, 4.0], 0.5))
        summary = latency_summary([1.0, 2.0, 3.0, 4.0])
        self.assertEqual(4, summary["samples"])
        self.assertEqual(2.5, summary["p50_ms"])
        self.assertEqual(2.5, summary["avg_ms"])

    def test_config_parser_resolves_default_and_environment(self):
        yaml = """app:
  manage:
    neo4j:
      uri: bolt://127.0.0.1:7687
      username: neo4j
      password: ${TEST_NEO4J_PASSWORD:local-default}
      database: neo4j
    minio:
      enabled: true
"""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "application.yaml"
            path.write_text(yaml, encoding="utf-8")
            with patch.dict(os.environ, {"TEST_NEO4J_PASSWORD": "from-env"}):
                config = parse_neo4j_config(path)
        self.assertEqual("bolt://127.0.0.1:7687", config["uri"])
        self.assertEqual("from-env", config["password"])
        self.assertEqual("neo4j", config["database"])

    def test_latency_summary_handles_empty_samples(self):
        self.assertEqual({
            "samples": 0,
            "p50_ms": 0.0,
            "p95_ms": 0.0,
            "p99_ms": 0.0,
            "avg_ms": 0.0,
            "max_ms": 0.0,
        }, latency_summary([]))


if __name__ == "__main__":
    unittest.main()
