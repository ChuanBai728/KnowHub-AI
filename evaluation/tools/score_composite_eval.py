#!/usr/bin/env python3
"""Score the 300-case KnowHub composite evaluation from live observations."""

from __future__ import annotations

import argparse
import json
import math
import re
import statistics
import sys
import unicodedata
from collections import OrderedDict, defaultdict
from pathlib import Path
from typing import Any, Iterable


KNOWN_MODES = {"RETRIEVAL", "GRAPH_ONLY", "GRAPH_THEN_EVIDENCE", "CLARIFICATION", "REACT_AGENT"}
REFUSAL_MARKERS = (
    "无法回答", "无法从", "没有足够", "资料不足", "信息不足", "证据不足",
    "未找到", "不能给出", "暂时不能", "无法确定", "无法确认",
)


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def unique(values: Iterable[str]) -> list[str]:
    return list(OrderedDict.fromkeys(str(value) for value in values if value))


def mean(values: Iterable[float]) -> float:
    items = list(values)
    return sum(items) / len(items) if items else 0.0


def percentile(values: Iterable[float], q: float) -> float:
    items = sorted(values)
    if not items:
        return 0.0
    position = (len(items) - 1) * q
    low = math.floor(position)
    high = math.ceil(position)
    if low == high:
        return float(items[low])
    return float(items[low] + (items[high] - items[low]) * (position - low))


def normalize_text(value: Any) -> str:
    text = unicodedata.normalize("NFKC", str(value or "")).lower()
    return "".join(char for char in text if char.isalnum())


def fact_choices(fact: Any) -> list[str]:
    text = str(fact or "").strip()
    if text.startswith("[") and text.endswith("]"):
        choices = re.findall(r"['\"]([^'\"]+)['\"]", text)
        if choices:
            return choices
    return [text] if text else []


def fact_matched(fact: Any, answer: str) -> bool:
    normalized_answer = normalize_text(answer)
    return any(
        normalize_text(choice) and normalize_text(choice) in normalized_answer
        for choice in fact_choices(fact)
    )


def metric_recall(expected: list[str], retrieved: list[str], k: int) -> float:
    return len(set(expected) & set(retrieved[:k])) / len(set(expected)) if expected else 0.0


def metric_hit(expected: list[str], retrieved: list[str], k: int) -> float:
    return float(bool(set(expected) & set(retrieved[:k])))


def metric_mrr(expected: list[str], retrieved: list[str], k: int) -> float:
    expected_set = set(expected)
    for rank, document_id in enumerate(retrieved[:k], 1):
        if document_id in expected_set:
            return 1.0 / rank
    return 0.0


def nested(data: dict[str, Any], *keys: str) -> Any:
    current: Any = data
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def normalize_mode(value: Any) -> str:
    text = str(value or "").strip()
    if not text:
        return ""
    text = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", text).upper().replace("-", "_")
    return text


def execution_mode(observation: dict[str, Any]) -> str:
    detail = observation.get("exchange_detail") or {}
    debug_mode = nested(detail, "exchange", "debugTrace", "executionMode")
    mode = normalize_mode(debug_mode)
    if mode in KNOWN_MODES:
        return mode
    for stage in reversed(detail.get("stageTraces") or []):
        mode = normalize_mode(stage.get("executionMode"))
        if mode in KNOWN_MODES:
            return mode
        mode = normalize_mode(nested(stage, "snapshot", "executionMode"))
        if mode in KNOWN_MODES:
            return mode
    return ""


def reference_document_ids(observation: dict[str, Any], runtime_to_official: dict[str, str]) -> list[str]:
    references = nested(observation, "exchange_detail", "exchange", "references") or []
    result = []
    for reference in references:
        if not isinstance(reference, dict):
            continue
        document_id = reference.get("documentId")
        if document_id is not None:
            runtime_id = str(document_id)
            result.append(runtime_to_official.get(runtime_id, runtime_id))
    return unique(result)


def score_case(gold: dict[str, Any], observation: dict[str, Any] | None,
               runtime_to_official: dict[str, str]) -> dict[str, Any]:
    observation = observation or {}
    answer = str(observation.get("answer") or "")
    completed = str(observation.get("answer_status") or "").upper() == "COMPLETED"
    expected = unique(str(item) for item in gold.get("expected_doc_ids") or [])
    retrieved = unique(str(item) for item in observation.get("retrieved_document_ids") or [])
    selected = unique(str(item) for item in observation.get("selected_document_ids") or [])
    allowed_modes = [normalize_mode(item) for item in gold.get("allowed_execution_modes") or []]
    retrieval_expected = bool(expected) and any(
        mode in {"RETRIEVAL", "GRAPH_THEN_EVIDENCE"} for mode in allowed_modes
    )
    facts = list(gold.get("answer_facts") or [])
    fact_hits = sum(fact_matched(fact, answer) for fact in facts) if completed else 0
    cited_documents = reference_document_ids(observation, runtime_to_official)
    citation_markers = bool(re.search(r"\[\d+\]", answer))
    citation_present = bool(cited_documents or citation_markers)
    forbidden = list(gold.get("forbidden_claims") or [])
    forbidden_hits = sum(fact_matched(claim, answer) for claim in forbidden) if completed else 0
    should_abstain = bool(gold.get("should_abstain"))
    refused = any(marker in answer for marker in REFUSAL_MARKERS)
    mode = execution_mode(observation)
    route_scored = gold.get("component") == "technical_docs"
    exchange = nested(observation, "exchange_detail", "exchange") or {}
    return {
        "question_id": gold["question_id"],
        "component": gold.get("component", "unknown"),
        "question_type": gold.get("question_type", "unknown"),
        "completed": completed,
        "retrieval_expected": retrieval_expected,
        "recall@5": metric_recall(expected, retrieved, 5) if retrieval_expected else None,
        "recall@10": metric_recall(expected, retrieved, 10) if retrieval_expected else None,
        "recall@20": metric_recall(expected, retrieved, 20) if retrieval_expected else None,
        "hit@5": metric_hit(expected, retrieved, 5) if retrieval_expected else None,
        "mrr@10": metric_mrr(expected, retrieved, 10) if retrieval_expected else None,
        "final_recall@5": metric_recall(expected, selected, 5) if retrieval_expected else None,
        "answer_scored": bool(facts),
        "fact_recall": fact_hits / len(facts) if facts else None,
        "answer_pass": float(fact_hits == len(facts)) if facts else None,
        "citation_required": bool(gold.get("citation_required")),
        "citation_present": float(citation_present),
        "citation_grounded": float(bool(set(expected) & set(cited_documents))),
        "should_abstain": should_abstain,
        "abstention_pass": float(completed and refused and forbidden_hits == 0) if should_abstain else None,
        "forbidden_scored": bool(forbidden),
        "forbidden_avoidance": float(forbidden_hits == 0) if forbidden else None,
        "should_clarify": bool(gold.get("should_clarify")),
        "execution_mode": mode,
        "route_pass": float(mode in allowed_modes) if route_scored else None,
        "first_response_time_ms": exchange.get("firstResponseTimeMs"),
        "total_response_time_ms": exchange.get("totalResponseTimeMs"),
    }


def selected(rows: list[dict[str, Any]], key: str) -> list[float]:
    return [float(row[key]) for row in rows if row.get(key) is not None]


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    retrieval = [row for row in rows if row["retrieval_expected"]]
    answer = [row for row in rows if row["answer_scored"]]
    citations = [row for row in rows if row["citation_required"]]
    abstentions = [row for row in rows if row["should_abstain"]]
    forbidden = [row for row in rows if row["forbidden_scored"]]
    clarifications = [row for row in rows if row["should_clarify"]]
    total_latencies = selected(rows, "total_response_time_ms")
    first_latencies = selected(rows, "first_response_time_ms")
    return {
        "cases": len(rows),
        "completed": sum(row["completed"] for row in rows),
        "completion_rate": mean(float(row["completed"]) for row in rows),
        "retrieval_cases": len(retrieval),
        "hit@5": mean(selected(retrieval, "hit@5")),
        "recall@5": mean(selected(retrieval, "recall@5")),
        "recall@10": mean(selected(retrieval, "recall@10")),
        "recall@20": mean(selected(retrieval, "recall@20")),
        "mrr@10": mean(selected(retrieval, "mrr@10")),
        "final_recall@5": mean(selected(retrieval, "final_recall@5")),
        "answer_cases": len(answer),
        "fact_recall": mean(selected(answer, "fact_recall")),
        "answer_pass_rate": mean(selected(answer, "answer_pass")),
        "citation_cases": len(citations),
        "citation_presence_rate": mean(selected(citations, "citation_present")),
        "grounded_citation_rate": mean(selected(citations, "citation_grounded")),
        "abstention_cases": len(abstentions),
        "abstention_pass_rate": mean(selected(abstentions, "abstention_pass")),
        "forbidden_claim_cases": len(forbidden),
        "forbidden_claim_avoidance_rate": mean(selected(forbidden, "forbidden_avoidance")),
        "route_accuracy": mean(selected(rows, "route_pass")),
        "clarification_cases": len(clarifications),
        "clarification_recall": mean(
            float(row["execution_mode"] == "CLARIFICATION") for row in clarifications
        ),
        "latency_ms": {
            "first_response_p50": percentile(first_latencies, 0.50),
            "first_response_p95": percentile(first_latencies, 0.95),
            "total_p50": percentile(total_latencies, 0.50),
            "total_p95": percentile(total_latencies, 0.95),
            "total_mean": statistics.fmean(total_latencies) if total_latencies else 0.0,
        },
    }


def component_score(component: str, metrics: dict[str, Any]) -> float:
    if component == "t2retrieval":
        return 0.45 * metrics["recall@10"] + 0.35 * metrics["mrr@10"] + 0.20 * metrics["final_recall@5"]
    if component == "cmrc2018":
        return 0.60 * metrics["answer_pass_rate"] + 0.20 * metrics["grounded_citation_rate"] + 0.20 * metrics["recall@5"]
    if component == "rgb":
        return 0.50 * metrics["answer_pass_rate"] + 0.25 * metrics["abstention_pass_rate"] + 0.25 * metrics["forbidden_claim_avoidance_rate"]
    if component == "technical_docs":
        return 0.45 * metrics["answer_pass_rate"] + 0.20 * metrics["grounded_citation_rate"] + 0.35 * metrics["route_accuracy"]
    return 0.0


def fmt(value: Any) -> str:
    return f"{float(value) * 100:.2f}%"


def write_markdown(path: Path, result: dict[str, Any]) -> None:
    overall = result["overall"]
    lines = [
        "# KnowHub 300 题复合评测结果", "",
        f"- 完成率：{overall['completed']}/{overall['cases']}（{fmt(overall['completion_rate'])}）",
        f"- 综合分：{result['composite_score']:.2f}/100", "",
        "| 子集 | 题数 | 子集分 | Recall@10 | MRR@10 | 答案通过率 | 拒答通过率 | 路由准确率 |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for component in ("t2retrieval", "cmrc2018", "rgb", "technical_docs"):
        item = result["components"][component]
        lines.append(
            f"| {component} | {item['cases']} | {item['score']:.2f} | {fmt(item['recall@10'])} | "
            f"{fmt(item['mrr@10'])} | {fmt(item['answer_pass_rate'])} | "
            f"{fmt(item['abstention_pass_rate'])} | {fmt(item['route_accuracy'])} |"
        )
    lines.extend([
        "", "## 总体关键指标", "",
        f"- Hit@5 / Recall@10 / MRR@10：{fmt(overall['hit@5'])} / {fmt(overall['recall@10'])} / {fmt(overall['mrr@10'])}",
        f"- 最终证据 Recall@5：{fmt(overall['final_recall@5'])}",
        f"- 事实覆盖率 / 答案全通过率：{fmt(overall['fact_recall'])} / {fmt(overall['answer_pass_rate'])}",
        f"- 引用出现率 / 正确文档引用率：{fmt(overall['citation_presence_rate'])} / {fmt(overall['grounded_citation_rate'])}",
        f"- 拒答通过率 / 禁止事实规避率：{fmt(overall['abstention_pass_rate'])} / {fmt(overall['forbidden_claim_avoidance_rate'])}",
        f"- 路由准确率 / 澄清召回率：{fmt(overall['route_accuracy'])} / {fmt(overall['clarification_recall'])}",
        f"- 总延迟 P50 / P95：{overall['latency_ms']['total_p50']:.0f} ms / {overall['latency_ms']['total_p95']:.0f} ms", "",
        "综合分为四个子集分的等权平均；各子集权重公式见 metrics.json。",
    ])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args(argv)
    output_dir = Path(args.output_dir)
    gold = load_jsonl(output_dir / "gold.jsonl")
    observations_by_id = {
        str(item["question_id"]): item for item in load_jsonl(output_dir / "observations.jsonl")
    }
    state = json.loads((output_dir / "import-state.json").read_text(encoding="utf-8"))
    runtime_to_official = {
        str(item["runtime_document_id"]): str(item["doc_id"])
        for item in state.get("items", []) if item.get("runtime_document_id")
    }
    rows = [score_case(item, observations_by_id.get(str(item["question_id"])), runtime_to_official) for item in gold]
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        groups[row["component"]].append(row)
    components = {}
    for component, component_rows in groups.items():
        metrics = summarize(component_rows)
        metrics["score"] = component_score(component, metrics) * 100
        components[component] = metrics
    result = {
        "dataset": "knowhub-cn-composite-rag-v1",
        "overall": summarize(rows),
        "components": components,
        "composite_score": mean(item["score"] for item in components.values()),
        "score_formula": {
            "composite": "四个子集分等权平均",
            "t2retrieval": "45% Recall@10 + 35% MRR@10 + 20% Final Recall@5",
            "cmrc2018": "60% 答案通过率 + 20% 正确文档引用率 + 20% Recall@5",
            "rgb": "50% 答案通过率 + 25% 拒答通过率 + 25% 禁止事实规避率",
            "technical_docs": "45% 答案通过率 + 20% 正确文档引用率 + 35% 路由准确率",
        },
        "rows": rows,
    }
    (output_dir / "metrics.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    write_markdown(output_dir / "metrics.md", result)
    print(json.dumps({
        "completed": result["overall"]["completed"],
        "total": result["overall"]["cases"],
        "composite_score": round(result["composite_score"], 2),
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
