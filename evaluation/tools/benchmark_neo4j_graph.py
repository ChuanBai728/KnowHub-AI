"""Benchmark KnowHub's Neo4j document-structure graph with read-only queries."""

from __future__ import annotations

import argparse
import base64
import json
import math
import os
import re
import statistics
import sys
import time
from collections import defaultdict
from datetime import datetime
from http.client import HTTPConnection
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


PLACEHOLDER_PATTERN = re.compile(r"^\$\{([^:}]+)(?::([^}]*))?}$")


def resolve_placeholder(value: str) -> str:
    value = value.strip().strip('"\'')
    match = PLACEHOLDER_PATTERN.match(value)
    if not match:
        return value
    return os.getenv(match.group(1)) or (match.group(2) or "")


def parse_neo4j_config(path: Path) -> dict[str, str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    start = next(
        (index for index, line in enumerate(lines) if re.match(r"^\s{4}neo4j:\s*$", line)),
        None,
    )
    if start is None:
        raise ValueError(f"Neo4j configuration not found in {path}")
    values: dict[str, str] = {}
    for line in lines[start + 1:]:
        if line.strip() and len(line) - len(line.lstrip()) <= 4:
            break
        match = re.match(r"^\s{6}([\w-]+):\s*(.+?)\s*$", line)
        if match:
            values[match.group(1)] = resolve_placeholder(match.group(2))
    required = ("uri", "username", "password", "database")
    missing = [key for key in required if not values.get(key)]
    if missing:
        raise ValueError(f"Missing Neo4j settings: {', '.join(missing)}")
    return values


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def latency_summary(values: list[float]) -> dict[str, float | int]:
    return {
        "samples": len(values),
        "p50_ms": round(percentile(values, 0.50), 3),
        "p95_ms": round(percentile(values, 0.95), 3),
        "p99_ms": round(percentile(values, 0.99), 3),
        "avg_ms": round(statistics.fmean(values), 3) if values else 0.0,
        "max_ms": round(max(values), 3) if values else 0.0,
    }


class Neo4jHttpClient:
    def __init__(self, uri: str, username: str, password: str, database: str, timeout: int = 15):
        parsed = urlparse(uri.replace("bolt://", "http://").replace("bolt+s://", "https://"))
        host = parsed.hostname or "127.0.0.1"
        port = 7474 if parsed.port in (None, 7687) else parsed.port
        self.host = host
        self.port = port
        self.database = database
        self.timeout = timeout
        token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
        self.headers = {"Authorization": f"Basic {token}", "Content-Type": "application/json"}
        self.connection: HTTPConnection | None = None

    def close(self) -> None:
        if self.connection is not None:
            self.connection.close()
            self.connection = None

    def execute(self, statement: str, parameters: dict[str, Any] | None = None) -> list[list[Any]]:
        payload = json.dumps({
            "statements": [{"statement": statement, "parameters": parameters or {}}],
        }, ensure_ascii=False).encode("utf-8")
        for attempt in range(2):
            try:
                if self.connection is None:
                    self.connection = HTTPConnection(self.host, self.port, timeout=self.timeout)
                self.connection.request(
                    "POST",
                    f"/db/{self.database}/tx/commit",
                    body=payload,
                    headers=self.headers,
                )
                response = self.connection.getresponse()
                body = json.loads(response.read().decode("utf-8"))
                if response.status != 200:
                    raise RuntimeError(f"Neo4j HTTP {response.status}")
                if body.get("errors"):
                    raise RuntimeError(body["errors"][0].get("message") or "Neo4j query failed")
                return [item.get("row") or [] for item in body["results"][0].get("data") or []]
            except (OSError, RuntimeError):
                self.close()
                if attempt:
                    raise
        return []


def timed_query(client: Neo4jHttpClient,
                latencies: dict[str, list[float]],
                operation: str,
                statement: str,
                parameters: dict[str, Any]) -> list[list[Any]]:
    started = time.perf_counter_ns()
    rows = client.execute(statement, parameters)
    latencies[operation].append((time.perf_counter_ns() - started) / 1_000_000)
    return rows


def run_benchmark(client: Neo4jHttpClient, samples: int, warmup: int) -> dict[str, Any]:
    overview_row = client.execute("""
        MATCH (n:KnowHubDocumentNode)
        RETURN count(n) AS nodes,
               count(DISTINCT n.documentId) AS documents
    """)[0]
    type_rows = client.execute("""
        MATCH (n:KnowHubDocumentNode)
        RETURN n.nodeType AS nodeType, count(*) AS count ORDER BY count DESC
    """)
    relationship_rows = client.execute("""
        MATCH ()-[r]->() RETURN type(r) AS relationship, count(*) AS count ORDER BY count DESC
    """)
    relationship_count = sum(int(row[1]) for row in relationship_rows)
    consistency_row = client.execute("""
        CALL {
          MATCH (s:KnowHubDocumentSection)
          WHERE NOT ()-[:PARENT_OF]->(s)
          RETURN count(s) AS rootSections
        }
        CALL {
          MATCH (i:KnowHubDocumentItem)
          WHERE NOT ()-[:PARENT_OF]->(i)
          RETURN count(i) AS itemsWithoutProjectedParent
        }
        CALL {
          MATCH (:KnowHubDocumentItem)-[:PARENT_OF]->(i:KnowHubDocumentItem)
          RETURN count(i) AS nestedItems
        }
        CALL {
          MATCH (parent:KnowHubDocumentNode)-[:PARENT_OF]->(child:KnowHubDocumentNode)
          WHERE parent.documentId <> child.documentId OR parent.nodeId <> child.parentNodeId
          RETURN count(*) AS parentEdgeMismatches
        }
        CALL {
          MATCH (previous:KnowHubDocumentNode)-[:NEXT_SIBLING]->(current:KnowHubDocumentNode)
          WHERE previous.documentId <> current.documentId
             OR current.prevSiblingNodeId <> previous.nodeId
             OR previous.nextSiblingNodeId <> current.nodeId
          RETURN count(*) AS siblingEdgeMismatches
        }
        CALL {
          MATCH (s:KnowHubDocumentSection)
          WHERE s.canonicalPath IS NOT NULL AND s.canonicalPath <> ''
          WITH s.documentId AS documentId, s.canonicalPath AS canonicalPath, count(*) AS pathCount
          RETURN coalesce(sum(CASE WHEN pathCount > 1 THEN pathCount - 1 ELSE 0 END), 0)
            AS duplicateCanonicalPaths
        }
        RETURN rootSections, itemsWithoutProjectedParent, nestedItems,
               parentEdgeMismatches, siblingEdgeMismatches, duplicateCanonicalPaths
    """)[0]
    case_rows = client.execute("""
        MATCH (s:KnowHubDocumentSection)
        OPTIONAL MATCH (s)-[:PARENT_OF]->(i:KnowHubDocumentItem)
        OPTIONAL MATCH (parent:KnowHubDocumentSection)-[:PARENT_OF]->(s)
        OPTIONAL MATCH (s)-[:NEXT_SIBLING]->(next:KnowHubDocumentSection)
        RETURN s.documentId AS documentId, s.nodeId, s.nodeCode, s.canonicalPath,
               parent.nodeId AS parentNodeId, next.nodeId AS nextSiblingNodeId,
               count(DISTINCT i) AS itemCount,
               s.nodeNo AS nodeNo
        ORDER BY itemCount DESC, documentId, nodeNo
        LIMIT $limit
    """, {"limit": max(samples, warmup, 1)})
    if not case_rows:
        raise RuntimeError("Neo4j contains no document sections")

    operations = {
        "availability": "MATCH (n:KnowHubDocumentNode {documentId:$documentId}) RETURN count(n) > 0",
        "list_sections": "MATCH (n:KnowHubDocumentSection {documentId:$documentId}) RETURN n.nodeId ORDER BY n.nodeNo",
        "find_by_id": "MATCH (n:KnowHubDocumentSection {documentId:$documentId, nodeId:$value}) RETURN n.nodeId",
        "find_by_code": "MATCH (n:KnowHubDocumentSection {documentId:$documentId, nodeCode:$value}) RETURN n.nodeId",
        "find_by_path": "MATCH (n:KnowHubDocumentSection {documentId:$documentId, canonicalPath:$value}) RETURN n.nodeId",
        "list_children": "MATCH (n:KnowHubDocumentSection {documentId:$documentId, parentNodeId:$value}) RETURN n.nodeId ORDER BY n.nodeNo",
        "list_items": "MATCH (n:KnowHubDocumentItem {documentId:$documentId, parentNodeId:$value}) RETURN n.nodeId ORDER BY n.nodeNo",
    }

    for index in range(warmup):
        row = case_rows[index % len(case_rows)]
        client.execute(operations["find_by_id"], {"documentId": row[0], "value": row[1]})

    latencies: dict[str, list[float]] = defaultdict(list)
    failures: list[dict[str, str]] = []
    validation_failures: list[dict[str, Any]] = []
    for index in range(samples):
        row = case_rows[index % len(case_rows)]
        document_id, node_id, node_code, canonical_path, parent_id, next_id, item_count, _ = row
        cases = [
            ("availability", operations["availability"], {"documentId": document_id}),
            ("list_sections", operations["list_sections"], {"documentId": document_id}),
            ("find_by_id", operations["find_by_id"], {"documentId": document_id, "value": node_id}),
            ("list_children", operations["list_children"], {"documentId": document_id, "value": node_id}),
            ("list_items", operations["list_items"], {"documentId": document_id, "value": node_id}),
        ]
        if node_code:
            cases.append(("find_by_code", operations["find_by_code"], {"documentId": document_id, "value": node_code}))
        if canonical_path:
            cases.append(("find_by_path", operations["find_by_path"], {"documentId": document_id, "value": canonical_path}))
        if parent_id is not None:
            cases.append(("parent_lookup", operations["find_by_id"], {"documentId": document_id, "value": parent_id}))
        if next_id is not None:
            cases.append(("next_sibling_lookup", operations["find_by_id"], {"documentId": document_id, "value": next_id}))
        for operation, statement, parameters in cases:
            try:
                rows = timed_query(client, latencies, operation, statement, parameters)
                if operation in {
                    "availability", "list_sections", "find_by_id", "find_by_code",
                    "find_by_path", "parent_lookup", "next_sibling_lookup",
                } and not rows:
                    validation_failures.append({"operation": operation, "reason": "empty_result"})
                if operation == "list_items" and len(rows) != int(item_count):
                    validation_failures.append({
                        "operation": operation,
                        "reason": "item_count_mismatch",
                        "expected": int(item_count),
                        "actual": len(rows),
                    })
            except Exception as exc:  # noqa: BLE001
                failures.append({"operation": operation, "error": str(exc)})

    executed = sum(len(values) for values in latencies.values()) + len(failures)
    unsuccessful = len(failures) + len(validation_failures)
    return {
        "generated_at": datetime.now().astimezone().isoformat(),
        "sampled_sections": samples,
        "warmup_queries": warmup,
        "overview": {
            "nodes": overview_row[0],
            "documents": overview_row[1],
            "relationships": relationship_count,
            "node_types": {str(row[0]): row[1] for row in type_rows},
            "relationship_types": {str(row[0]): row[1] for row in relationship_rows},
        },
        "consistency": {
            "root_sections": consistency_row[0],
            "items_without_projected_parent": consistency_row[1],
            "nested_items": consistency_row[2],
            "parent_edge_mismatches": consistency_row[3],
            "sibling_edge_mismatches": consistency_row[4],
            "duplicate_canonical_paths": consistency_row[5],
        },
        "query_success_rate": round((executed - unsuccessful) / executed, 6) if executed else 0.0,
        "query_failures": failures[:20],
        "validation_failures": validation_failures[:20],
        "latency": {name: latency_summary(values) for name, values in sorted(latencies.items())},
    }


def write_report(result: dict[str, Any], output_dir: Path) -> tuple[Path, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / "neo4j-graph-benchmark.json"
    md_path = output_dir / "neo4j-graph-benchmark.md"
    json_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    overview = result["overview"]
    consistency = result["consistency"]
    lines = [
        "# Neo4j document graph benchmark",
        "",
        f"- Documents: {overview['documents']}",
        f"- Nodes: {overview['nodes']}",
        f"- Relationships: {overview['relationships']}",
        f"- Query success rate: {result['query_success_rate']:.2%}",
        f"- Root sections: {consistency['root_sections']}",
        f"- Items without projected parent: {consistency['items_without_projected_parent']}",
        f"- Nested items: {consistency['nested_items']}",
        f"- Parent/sibling edge mismatches: {consistency['parent_edge_mismatches']} / {consistency['sibling_edge_mismatches']}",
        f"- Duplicate canonical paths: {consistency['duplicate_canonical_paths']}",
        "",
        "| Operation | Samples | P50 ms | P95 ms | P99 ms | Avg ms | Max ms |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for name, item in result["latency"].items():
        lines.append(
            f"| {name} | {item['samples']} | {item['p50_ms']:.3f} | {item['p95_ms']:.3f} "
            f"| {item['p99_ms']:.3f} | {item['avg_ms']:.3f} | {item['max_ms']:.3f} |"
        )
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return json_path, md_path


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config-yaml", default="evaluation/application-eval.yaml")
    parser.add_argument("--samples", type=int, default=100)
    parser.add_argument("--warmup", type=int, default=20)
    parser.add_argument("--output-dir", default="evaluation/reports/neo4j-graph-benchmark")
    args = parser.parse_args(argv)
    config = parse_neo4j_config(Path(args.config_yaml))
    client = Neo4jHttpClient(
        config["uri"], config["username"], config["password"], config["database"]
    )
    try:
        result = run_benchmark(client, max(1, args.samples), max(0, args.warmup))
    finally:
        client.close()
    json_path, md_path = write_report(result, Path(args.output_dir))
    print(json.dumps({
        "success_rate": result["query_success_rate"],
        "json": str(json_path),
        "markdown": str(md_path),
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
