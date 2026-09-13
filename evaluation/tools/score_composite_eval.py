#!/usr/bin/env python3
"""Score the 300-case KnowHub composite evaluation from live observations.

Run caliber
-----------
评分依据观测记录自带的 `run_config`（由 enterprise_rag_bench_server_eval 写入）判定口径：

- `routing_enabled` / `unknown`（旧观测文件没有该字段）：行为与引入本机制之前完全一致，
  保证历史结果可复现；
- `forced_retrieval`（`force-retrieval-mode: true`）：技术文档子集的「路由准确率」结构性
  失效，此时**不静默改分**——数字照常输出，但结果里标记 `route_accuracy_applicable=false`、
  写入 `warnings`，并在 Markdown 头部与指标行就地告警。该子集分不可与生产口径对照。

Retrieval scope
---------------
`summarize()` 的 `retrieval_scope` 块报告真实检索范围（候选数与范围数的均值/中位/P95），
并**按高/低置信分组**：实测分布是双峰的，只看均值会掩盖「多数问题其实退回了兜底范围」。
取值优先用观测记录里的扁平字段，缺失时回退到路由阶段快照，因此 2026-09-12 那批观测文件
无需重跑即可回溯出真实范围。
"""

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

# 与 enterprise_rag_bench_server_eval.ROUTE_SCOPE_SNAPSHOT_KEYS 保持一致。两份拷贝是刻意的：
# 评分脚本不应依赖 runner（也就会连带依赖 requests），而且二者分别在 unittest discover 下
# 独立加载。两边的测试用同一组快照样本锁住行为一致。
ROUTE_SCOPE_SNAPSHOT_KEYS = (
    "routeStatus",
    "candidateDocumentCount",
    "retrievalScopeDocumentCount",
    "knowledgeRouteBypassed",
)

CALIBER_FORCED_RETRIEVAL = "forced_retrieval"
CALIBER_ROUTING_ENABLED = "routing_enabled"
CALIBER_UNKNOWN = "unknown"
REFUSAL_MARKERS = (
    "无法回答", "无法从", "没有足够", "资料不足", "信息不足", "证据不足",
    "未找到", "不能给出", "暂时不能", "无法确定", "无法确认",
)
SEMANTIC_REPLACEMENTS = (
    ("任何", "任意"),
    ("可以使用任意字符串作为其取值", "可以提供任意字符串"),
    ("可使用任意字符串作为取值", "可提供任意字符串"),
    ("存储在api服务器的底层数据存储etcd", "存储在etcd"),
    ("以最小特权访问secret并启用或配置rbac规则", "最小特权和rbac"),
    ("限制secret对特定容器的访问", "限制到特定容器"),
    ("默认情况下", "默认"),
    ("尚不可用时", "可用之前"),
    ("未就绪时", "可用之前"),
    ("重新启动", "重启"),
    ("重新开启", "重启"),
    ("不会", "不"),
    ("不能", "不"),
    ("必须", "需要"),
    ("建议考虑", "可"),
    ("或者", "或"),
)
LEADING_MODAL_MARKERS = ("可以", "可", "需要", "应该", "应当", "建议")


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


def median(values: Iterable[float]) -> float:
    items = list(values)
    return float(statistics.median(items)) if items else 0.0


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


def strict_fact_matched(fact: Any, answer: str) -> bool:
    normalized_answer = normalize_text(answer)
    return any(
        normalize_text(choice) and normalize_text(choice) in normalized_answer
        for choice in fact_choices(fact)
    )


def bounded_subsequence(needle: str, haystack: str) -> bool:
    """Allow short explanatory insertions without accepting arbitrarily scattered characters."""
    if len(needle) < 3 or len(needle) > len(haystack):
        return False
    max_span = max(len(needle) * 4, len(needle) + 12)
    for start, char in enumerate(haystack):
        if char != needle[0]:
            continue
        cursor = start + 1
        for expected in needle[1:]:
            cursor = haystack.find(expected, cursor)
            if cursor < 0:
                break
            cursor += 1
        else:
            if cursor - start <= max_span:
                return True
    return False


def semantic_variants(value: Any) -> list[str]:
    normalized = normalize_text(value)
    for source, replacement in SEMANTIC_REPLACEMENTS:
        normalized = normalized.replace(source, replacement)
    variants = [normalized]
    for marker in LEADING_MODAL_MARKERS:
        if normalized.startswith(marker) and len(normalized) > len(marker) + 1:
            variants.append(normalized[len(marker):])
    return unique(variants)


def ngram_coverage(needle: str, haystack: str, size: int) -> float:
    if len(needle) < size:
        return 0.0
    expected = {needle[index:index + size] for index in range(len(needle) - size + 1)}
    actual = {haystack[index:index + size] for index in range(len(haystack) - size + 1)}
    return len(expected & actual) / len(expected) if expected else 0.0


def conservative_paraphrase_match(needle: str, haystack: str) -> bool:
    """Accept local reordering while preserving numbers, Latin identifiers, and polarity."""
    if len(needle) < 8:
        return False
    literals = re.findall(r"[a-z]+[a-z0-9.]*|\d+(?:\.\d+)*", needle)
    if any(literal not in haystack for literal in literals):
        return False
    if any(marker in needle for marker in ("不", "未", "无", "非")) \
        and not any(marker in haystack for marker in ("不", "未", "无", "非")):
        return False
    return ngram_coverage(needle, haystack, 2) >= 0.80 \
        and ngram_coverage(needle, haystack, 3) >= 0.60


def fact_matched(fact: Any, answer: str) -> bool:
    answer_variants = semantic_variants(answer)
    for choice in fact_choices(fact):
        for normalized_choice in semantic_variants(choice):
            if not normalized_choice:
                continue
            for normalized_answer in answer_variants:
                if normalized_choice in normalized_answer:
                    return True
                bounded_answer = normalized_answer
                if not re.search(r"[a-z0-9]", normalized_choice):
                    # 中文事实中插入产品/字段名属于解释限定，不应把相邻语义拆成漏判。
                    bounded_answer = re.sub(r"[a-z0-9.]+", "", normalized_answer)
                if bounded_subsequence(normalized_choice, bounded_answer):
                    return True
                if conservative_paraphrase_match(normalized_choice, normalized_answer):
                    return True
    return False


def answer_fact_groups(gold: dict[str, Any]) -> list[list[Any]]:
    """Return AND-ed fact groups whose members are equivalent OR-ed answer aliases."""
    explicit_groups = gold.get("answer_fact_groups") or []
    if explicit_groups:
        return [list(group) for group in explicit_groups if group]
    facts = list(gold.get("answer_facts") or [])
    if gold.get("component") == "cmrc2018" and facts:
        return [facts]
    return [[fact] for fact in facts]


def matched_groups(groups: list[list[Any]], answer: str, *, strict: bool = False) -> int:
    matcher = strict_fact_matched if strict else fact_matched
    return sum(any(matcher(choice, answer) for choice in group) for group in groups)


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


def truthy(value: Any) -> bool:
    return value is True or str(value).lower() == "true"


def retrieval_scope(observation: dict[str, Any]) -> str:
    detail = observation.get("exchange_detail") or {}
    for stage in detail.get("stageTraces") or []:
        snapshot = stage.get("snapshot") or {}
        if truthy(snapshot.get("fullCorpusRetrieval")):
            return "FULL_CORPUS"
    return "ROUTE_CANDIDATES"


def as_count(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def route_scope_snapshot(stage_traces: list[dict[str, Any]] | None) -> dict[str, Any]:
    """stageTraces 里有两条 ROUTE 阶段，只有知识范围路由那条带候选数量信息，按键集判别。"""
    for stage in stage_traces or []:
        snapshot = stage.get("snapshot") if isinstance(stage, dict) else None
        if isinstance(snapshot, dict) and any(key in snapshot for key in ROUTE_SCOPE_SNAPSHOT_KEYS):
            return snapshot
    return {}


def confidence_bucket(status: Any) -> str:
    normalized = str(status or "").upper()
    if normalized == "SUCCESS":
        return "high"
    if normalized == "LOW_CONFIDENCE":
        return "low"
    return "unknown"


def route_scope_metrics(observation: dict[str, Any]) -> dict[str, Any]:
    """真实检索范围。

    优先读观测记录里的扁平字段；缺失时回退到路由阶段快照——2026-09-12 那批观测文件没有
    扁平字段但已带快照，回退让它们无需重跑就能算出真实范围。
    """
    flat_count = observation.get("retrieval_scope_document_count")
    status = observation.get("route_status")
    if flat_count is not None or status:
        return {
            "route_candidate_document_count": as_count(observation.get("route_candidate_document_count")),
            "retrieval_scope_document_count": as_count(flat_count),
            "route_status": str(status or ""),
            "route_confidence": observation.get("route_confidence"),
            "route_confidence_bucket": confidence_bucket(status),
        }
    detail = observation.get("exchange_detail") or {}
    snapshot = route_scope_snapshot(detail.get("stageTraces"))
    return {
        "route_candidate_document_count": as_count(snapshot.get("candidateDocumentCount")),
        "retrieval_scope_document_count": as_count(snapshot.get("retrievalScopeDocumentCount")),
        "route_status": str(snapshot.get("routeStatus") or ""),
        "route_confidence": snapshot.get("confidence"),
        "route_confidence_bucket": confidence_bucket(snapshot.get("routeStatus")),
    }


def run_config_of(observations: Iterable[dict[str, Any]]) -> dict[str, Any]:
    """取观测记录里自带的运行口径；旧文件没有该键时返回空字典。"""
    for observation in observations:
        config = observation.get("run_config")
        if isinstance(config, dict) and config:
            return config
    return {}


def run_caliber(run_config: dict[str, Any]) -> str:
    """把运行口径归成三类。

    只有明确记录为强制检索时才降级路由准确率；未记录（旧观测文件）一律按今天的行为处理，
    保证历史结果可复现。
    """
    forced = run_config.get("force_retrieval_mode")
    if forced is True:
        return CALIBER_FORCED_RETRIEVAL
    if forced is False:
        return CALIBER_ROUTING_ENABLED
    return CALIBER_UNKNOWN


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
    routed = unique(str(item) for item in observation.get("route_document_ids") or [])
    allowed_modes = [normalize_mode(item) for item in gold.get("allowed_execution_modes") or []]
    retrieval_expected = bool(expected) and any(
        mode in {"RETRIEVAL", "GRAPH_THEN_EVIDENCE"} for mode in allowed_modes
    )
    fact_groups = answer_fact_groups(gold)
    fact_hits = matched_groups(fact_groups, answer) if completed else 0
    strict_fact_hits = matched_groups(fact_groups, answer, strict=True) if completed else 0
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
        "retrieval_scope": retrieval_scope(observation),
        "route_recall@20": metric_recall(expected, routed, 20) if retrieval_expected else None,
        "route_hit@20": metric_hit(expected, routed, 20) if retrieval_expected else None,
        "recall@5": metric_recall(expected, retrieved, 5) if retrieval_expected else None,
        "recall@10": metric_recall(expected, retrieved, 10) if retrieval_expected else None,
        "recall@20": metric_recall(expected, retrieved, 20) if retrieval_expected else None,
        "hit@1": metric_hit(expected, retrieved, 1) if retrieval_expected else None,
        "hit@3": metric_hit(expected, retrieved, 3) if retrieval_expected else None,
        "hit@5": metric_hit(expected, retrieved, 5) if retrieval_expected else None,
        "mrr@10": metric_mrr(expected, retrieved, 10) if retrieval_expected else None,
        "final_recall@5": metric_recall(expected, selected, 5) if retrieval_expected else None,
        "answer_scored": bool(fact_groups),
        "fact_recall": fact_hits / len(fact_groups) if fact_groups else None,
        "answer_pass": float(fact_hits == len(fact_groups)) if fact_groups else None,
        "strict_fact_recall": strict_fact_hits / len(fact_groups) if fact_groups else None,
        "strict_answer_pass": float(strict_fact_hits == len(fact_groups)) if fact_groups else None,
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
        **route_scope_metrics(observation),
    }


def selected(rows: list[dict[str, Any]], key: str) -> list[float]:
    return [float(row[key]) for row in rows if row.get(key) is not None]


def summarize(rows: list[dict[str, Any]], route_applicable: bool = True) -> dict[str, Any]:
    retrieval = [row for row in rows if row["retrieval_expected"]]
    full_corpus_retrieval = [row for row in retrieval if row["retrieval_scope"] == "FULL_CORPUS"]
    route_scoped_retrieval = [row for row in retrieval if row["retrieval_scope"] == "ROUTE_CANDIDATES"]
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
        "route_recall@20": mean(selected(retrieval, "route_recall@20")),
        "route_hit@20": mean(selected(retrieval, "route_hit@20")),
        "hit@1": mean(selected(retrieval, "hit@1")),
        "hit@3": mean(selected(retrieval, "hit@3")),
        "hit@5": mean(selected(retrieval, "hit@5")),
        "recall@5": mean(selected(retrieval, "recall@5")),
        "recall@10": mean(selected(retrieval, "recall@10")),
        "recall@20": mean(selected(retrieval, "recall@20")),
        "mrr@10": mean(selected(retrieval, "mrr@10")),
        "final_recall@5": mean(selected(retrieval, "final_recall@5")),
        "full_corpus_retrieval_cases": len(full_corpus_retrieval),
        "full_corpus_recall@10": mean(selected(full_corpus_retrieval, "recall@10")),
        "route_scoped_retrieval_cases": len(route_scoped_retrieval),
        "route_scoped_recall@10": mean(selected(route_scoped_retrieval, "recall@10")),
        "answer_cases": len(answer),
        "fact_recall": mean(selected(answer, "fact_recall")),
        "answer_pass_rate": mean(selected(answer, "answer_pass")),
        "strict_fact_recall": mean(selected(answer, "strict_fact_recall")),
        "strict_answer_pass_rate": mean(selected(answer, "strict_answer_pass")),
        "citation_cases": len(citations),
        "citation_presence_rate": mean(selected(citations, "citation_present")),
        "grounded_citation_rate": mean(selected(citations, "citation_grounded")),
        "abstention_cases": len(abstentions),
        "abstention_pass_rate": mean(selected(abstentions, "abstention_pass")),
        "forbidden_claim_cases": len(forbidden),
        "forbidden_claim_avoidance_rate": mean(selected(forbidden, "forbidden_avoidance")),
        "route_accuracy": mean(selected(rows, "route_pass")),
        # 口径异常（强制检索）时该指标结构性失效，但此处不静默改分：数字照常输出，
        # 由 main() 写入 warnings 并在报告头部醒目标注，避免"看起来像正常分数"。
        "route_accuracy_applicable": route_applicable,
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
        "retrieval_scope": retrieval_scope_summary(rows),
    }


def retrieval_scope_summary(rows: list[dict[str, Any]]) -> dict[str, Any]:
    """候选/实际检索范围统计，必须按置信度分组。

    实测分布是双峰的（16 题 20 篇 + 34 题 100 篇），只看均值会掩盖"大多数问题其实没拿到
    精确限域"这一事实，而测试指南明确要求低置信样本不能被平均数掩盖。
    """
    scoped = [row for row in rows if row.get("retrieval_scope_document_count") is not None]
    candidates = [row for row in rows if row.get("route_candidate_document_count") is not None]
    high = [row for row in scoped if row.get("route_confidence_bucket") == "high"]
    low = [row for row in scoped if row.get("route_confidence_bucket") == "low"]
    unknown = [row for row in scoped if row.get("route_confidence_bucket") == "unknown"]

    def scope_values(items: list[dict[str, Any]]) -> list[float]:
        return [float(row["retrieval_scope_document_count"]) for row in items]

    return {
        "cases": len(scoped),
        "candidate_document_count_mean": mean(
            float(row["route_candidate_document_count"]) for row in candidates
        ),
        "candidate_document_count_median": median(
            float(row["route_candidate_document_count"]) for row in candidates
        ),
        "candidate_document_count_p95": percentile(
            [float(row["route_candidate_document_count"]) for row in candidates], 0.95
        ),
        "scope_document_count_mean": mean(scope_values(scoped)),
        "scope_document_count_median": median(scope_values(scoped)),
        "scope_document_count_p95": percentile(scope_values(scoped), 0.95),
        "high_confidence_cases": len(high),
        "high_confidence_scope_mean": mean(scope_values(high)),
        "high_confidence_scope_median": median(scope_values(high)),
        "high_confidence_scope_p95": percentile(scope_values(high), 0.95),
        "low_confidence_cases": len(low),
        "low_confidence_scope_mean": mean(scope_values(low)),
        "low_confidence_scope_median": median(scope_values(low)),
        "low_confidence_scope_p95": percentile(scope_values(low), 0.95),
        "unknown_confidence_cases": len(unknown),
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


def fmt_number(value: Any, digits: int = 2) -> str:
    if value is None:
        return "N/A"
    return f"{float(value):.{digits}f}"


def describe_caliber(run_config: dict[str, Any]) -> str:
    if not run_config:
        return "未记录"
    parts = [
        f"force-retrieval-mode={run_config.get('force_retrieval_mode')}",
        f"force-retrieval-full-corpus={run_config.get('force_retrieval_full_corpus')}",
        f"clarification-enabled={run_config.get('clarification_enabled')}",
        f"keyword-channel-enabled={run_config.get('keyword_channel_enabled')}",
        f"rerank-enabled={run_config.get('rerank_enabled')}",
    ]
    if run_config.get("rrf_k") is not None:
        parts.append(
            "rrf(k={}, vector={}, keyword={}, adaptive={})".format(
                run_config.get("rrf_k"),
                run_config.get("rrf_vector_weight"),
                run_config.get("rrf_keyword_weight"),
                run_config.get("rrf_adaptive_keyword_weight_enabled"),
            )
        )
    if run_config.get("sql_log_impl"):
        parts.append(f"sql-log={str(run_config['sql_log_impl']).rsplit('.', 1)[-1]}")
    return " ".join(parts)


def write_markdown(path: Path, result: dict[str, Any]) -> None:
    overall = result["overall"]
    is_subset = result.get("score_scope") == "observed_subset"
    score_label = "非正式子集分" if is_subset else "综合分"
    # 数字照常显示（不静默改分），但在失效口径下就地标注，避免被当成正常指标引用。
    route_suffix = (
        "（强制检索口径下不具可比性，仅作诊断）"
        if overall.get("route_accuracy_applicable") is False
        else ""
    )
    lines = [
        "# KnowHub 复合评测结果", "",
        f"- 完成率：{overall['completed']}/{overall['cases']}（{fmt(overall['completion_rate'])}）",
        f"- {score_label}：{result['composite_score']:.2f}/100", "",
    ]
    # 口径写在分数之前：读者必须先知道这份数字是按什么配置跑的，再看数字。
    if result.get("run_caliber") and result["run_caliber"] != CALIBER_UNKNOWN:
        lines.append(f"- 本次运行口径：{describe_caliber(result.get('run_config') or {})}")
    for warning in result.get("warnings") or []:
        lines.append(f"- **注意**：{warning}")
    if result.get("run_caliber") and result["run_caliber"] != CALIBER_UNKNOWN:
        lines.append("")
    lines.extend([
        "| 子集 | 题数 | 子集分 | Recall@10 | MRR@10 | 答案通过率 | 拒答通过率 | 路由准确率 |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ])
    canonical_order = ("t2retrieval", "cmrc2018", "rgb", "technical_docs")
    component_order = [component for component in canonical_order if component in result["components"]]
    component_order.extend(sorted(set(result["components"]) - set(canonical_order)))
    for component in component_order:
        item = result["components"][component]
        lines.append(
            f"| {component} | {item['cases']} | {item['score']:.2f} | {fmt(item['recall@10'])} | "
            f"{fmt(item['mrr@10'])} | {fmt(item['answer_pass_rate'])} | "
            f"{fmt(item['abstention_pass_rate'])} | {fmt(item['route_accuracy'])} |"
        )
    lines.extend([
        "", "## 总体关键指标", "",
        f"- 路由候选 Recall@20 / Hit@20：{fmt(overall['route_recall@20'])} / {fmt(overall['route_hit@20'])}",
        f"- Hit@1 / Hit@3 / Hit@5：{fmt(overall['hit@1'])} / {fmt(overall['hit@3'])} / {fmt(overall['hit@5'])}",
        f"- Recall@10 / MRR@10：{fmt(overall['recall@10'])} / {fmt(overall['mrr@10'])}",
        f"- 全语料检索 Recall@10：{fmt(overall['full_corpus_recall@10'])}（{overall['full_corpus_retrieval_cases']} 题）",
        f"- 路由限域检索 Recall@10：{fmt(overall['route_scoped_recall@10'])}（{overall['route_scoped_retrieval_cases']} 题）",
        f"- 最终证据 Recall@5：{fmt(overall['final_recall@5'])}",
        f"- 事实覆盖率 / 答案通过率：{fmt(overall['fact_recall'])} / {fmt(overall['answer_pass_rate'])}",
        f"- 严格事实覆盖率 / 严格答案通过率：{fmt(overall['strict_fact_recall'])} / {fmt(overall['strict_answer_pass_rate'])}",
        f"- 引用出现率 / 正确文档引用率：{fmt(overall['citation_presence_rate'])} / {fmt(overall['grounded_citation_rate'])}",
        f"- 拒答通过率 / 禁止事实规避率：{fmt(overall['abstention_pass_rate'])} / {fmt(overall['forbidden_claim_avoidance_rate'])}",
        f"- 路由准确率 / 澄清召回率：{fmt(overall['route_accuracy'])}{route_suffix} / {fmt(overall['clarification_recall'])}",
        f"- 总延迟 P50 / P95：{overall['latency_ms']['total_p50']:.0f} ms / {overall['latency_ms']['total_p95']:.0f} ms", "",
    ])
    scope = overall.get("retrieval_scope") or {}
    if scope.get("cases"):
        lines.extend([
            f"- 检索范围（{scope['cases']} 题有记录）：均值 {fmt_number(scope['scope_document_count_mean'])} 篇 / "
            f"中位 {fmt_number(scope['scope_document_count_median'])} 篇 / P95 {fmt_number(scope['scope_document_count_p95'])} 篇",
            f"- 高置信路由（{scope['high_confidence_cases']} 题）：均值 {fmt_number(scope['high_confidence_scope_mean'])} 篇 / "
            f"中位 {fmt_number(scope['high_confidence_scope_median'])} 篇",
            f"- 低置信路由（{scope['low_confidence_cases']} 题）：均值 {fmt_number(scope['low_confidence_scope_mean'])} 篇 / "
            f"中位 {fmt_number(scope['low_confidence_scope_median'])} 篇",
            "",
        ])
    if is_subset:
        lines.append("当前为 `--only-observed` 子集烟测，分数仅为已执行子集分的平均值，不是正式四子集等权综合分。")
    else:
        lines.append("综合分为四个子集分的等权平均；各子集权重公式见对应 JSON 指标文件。")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--observations-file", default="observations.jsonl")
    parser.add_argument("--metrics-prefix", default="metrics")
    parser.add_argument(
        "--only-observed",
        action="store_true",
        help="Score only question IDs present in the selected observation file (useful for smoke runs).",
    )
    args = parser.parse_args(argv)
    output_dir = Path(args.output_dir)
    gold = load_jsonl(output_dir / "gold.jsonl")
    observations_by_id = {
        str(item["question_id"]): item for item in load_jsonl(output_dir / Path(args.observations_file).name)
    }
    if args.only_observed:
        gold = [item for item in gold if str(item["question_id"]) in observations_by_id]
    state = json.loads((output_dir / "import-state.json").read_text(encoding="utf-8"))
    runtime_to_official = {
        str(item["runtime_document_id"]): str(item["doc_id"])
        for item in state.get("items", []) if item.get("runtime_document_id")
    }
    rows = [score_case(item, observations_by_id.get(str(item["question_id"])), runtime_to_official) for item in gold]

    # 运行口径来自观测记录本身（见 enterprise_rag_bench_server_eval.load_rag_run_config）。
    # 旧观测文件没有该字段 → unknown → 行为与加这个机制之前完全一致。
    run_config = run_config_of(observations_by_id.values())
    caliber = run_caliber(run_config)
    route_applicable = caliber != CALIBER_FORCED_RETRIEVAL
    warnings: list[str] = []
    if caliber == CALIBER_FORCED_RETRIEVAL:
        warnings.append(
            "本次运行 force-retrieval-mode=true：所有请求被强制走 RETRIEVAL 且澄清被关闭，"
            "技术文档子集的「路由准确率」结构性失效（期望 GRAPH_ONLY/GRAPH_THEN_EVIDENCE/"
            "CLARIFICATION 的题目必然判错），该子集分不可与生产口径或其他口径的结果对照。"
        )

    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        groups[row["component"]].append(row)
    components = {}
    for component, component_rows in groups.items():
        metrics = summarize(component_rows, route_applicable=route_applicable)
        metrics["score"] = component_score(component, metrics) * 100
        components[component] = metrics
    result = {
        "dataset": "knowhub-cn-composite-rag-v1",
        "score_scope": "observed_subset" if args.only_observed else "full_dataset",
        "official_composite": not args.only_observed,
        "run_caliber": caliber,
        "run_config": run_config,
        "route_accuracy_applicable": route_applicable,
        "warnings": warnings,
        "overall": summarize(rows, route_applicable=route_applicable),
        "components": components,
        "composite_score": mean(item["score"] for item in components.values()),
        "score_formula": {
            "composite": (
                "已执行子集分等权平均（非正式子集分）"
                if args.only_observed else "四个子集分等权平均"
            ),
            "t2retrieval": "45% Recall@10 + 35% MRR@10 + 20% Final Recall@5",
            "cmrc2018": "60% 答案通过率 + 20% 正确文档引用率 + 20% Recall@5",
            "rgb": "50% 答案通过率 + 25% 拒答通过率 + 25% 禁止事实规避率",
            "technical_docs": "45% 答案通过率 + 20% 正确文档引用率 + 35% 路由准确率",
        },
        "rows": rows,
    }
    metrics_prefix = Path(args.metrics_prefix).name
    (output_dir / f"{metrics_prefix}.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    write_markdown(output_dir / f"{metrics_prefix}.md", result)
    print(json.dumps({
        "completed": result["overall"]["completed"],
        "total": result["overall"]["cases"],
        "composite_score": round(result["composite_score"], 2),
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
