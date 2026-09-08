#!/usr/bin/env python3
"""Validate the structure, provenance and evidence anchors of the 300-case set."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


EXPECTED_COMPONENTS = {
    "t2retrieval": 150,
    "cmrc2018": 50,
    "rgb": 50,
    "technical_docs": 50,
}
ID_PREFIXES = {
    "t2retrieval": "CMP-T2-",
    "cmrc2018": "CMP-CMRC-",
    "rgb": "CMP-RGB-",
    "technical_docs": "CMP-DOC-",
}
VALID_MODES = {"GRAPH_ONLY", "GRAPH_THEN_EVIDENCE", "RETRIEVAL", "CLARIFICATION"}
VALID_REVIEW_STATUSES = {"auto_converted", "pending_human_review", "approved", "rejected"}


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def validate(dataset_dir: Path) -> dict[str, Any]:
    errors: list[str] = []
    warnings: list[str] = []
    metadata = read_json(dataset_dir / "dataset.json")
    manifest = read_json(dataset_dir / "manifest.json")
    cases = read_jsonl(dataset_dir / "gold.jsonl")
    sources = read_json(dataset_dir / "sources.json")

    if len(cases) != 300:
        errors.append(f"expected 300 cases, found {len(cases)}")
    if metadata.get("case_count") != len(cases):
        errors.append("dataset.json case_count does not match gold.jsonl")
    if metadata.get("document_count") != len(manifest):
        errors.append("dataset.json document_count does not match manifest.json")

    component_counts = Counter(str(case.get("component")) for case in cases)
    if dict(component_counts) != EXPECTED_COMPONENTS:
        errors.append(f"component counts mismatch: {dict(component_counts)}")

    case_ids: set[str] = set()
    doc_ids: set[str] = set()
    docs: dict[str, dict[str, Any]] = {}
    for document in manifest:
        doc_id = str(document.get("doc_id") or "")
        if not doc_id or doc_id in doc_ids:
            errors.append(f"invalid or duplicate document id: {doc_id!r}")
            continue
        doc_ids.add(doc_id)
        docs[doc_id] = document
        path = dataset_dir / str(document.get("path") or "")
        if not path.is_file():
            errors.append(f"missing corpus file for {doc_id}: {path}")
            continue
        materialized_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        if materialized_hash != document.get("materialized_sha256"):
            errors.append(f"materialized SHA-256 mismatch for {doc_id}")
        for field in ("source_record_id", "source_url", "source_revision", "license", "attribution"):
            if not document.get(field):
                errors.append(f"{doc_id} missing provenance field {field}")

    pending_manual = 0
    manual_review_required = 0
    for case in cases:
        question_id = str(case.get("question_id") or "")
        component = str(case.get("component") or "")
        if question_id in case_ids:
            errors.append(f"duplicate question id: {question_id}")
        case_ids.add(question_id)
        if not question_id.startswith(ID_PREFIXES.get(component, "!")):
            errors.append(f"question id/component mismatch: {question_id}/{component}")
        if not str(case.get("question") or "").strip():
            errors.append(f"empty question: {question_id}")
        modes = set(case.get("allowed_execution_modes") or [])
        if not modes or not modes <= VALID_MODES:
            errors.append(f"invalid execution modes for {question_id}: {sorted(modes)}")
        expected_docs = [str(value) for value in case.get("expected_doc_ids") or []]
        missing = [doc_id for doc_id in expected_docs if doc_id not in doc_ids]
        if missing:
            errors.append(f"{question_id} references missing expected docs: {missing}")
        if case.get("should_abstain") and expected_docs:
            errors.append(f"abstention case {question_id} must not have expected docs")
        if case.get("should_clarify") and "CLARIFICATION" not in modes:
            errors.append(f"clarification case {question_id} does not allow CLARIFICATION")
        review = case.get("review") or {}
        review_status = str(review.get("status") or "")
        if review_status not in VALID_REVIEW_STATUSES:
            errors.append(f"invalid review status for {question_id}: {review_status!r}")
        if component == "technical_docs":
            if not review.get("manual_review_required"):
                errors.append(f"technical docs case lacks manual-review flag: {question_id}")
            else:
                manual_review_required += 1
            if review_status == "pending_human_review":
                pending_manual += 1
            if review_status == "approved" and not str(review.get("reviewer") or "").strip():
                errors.append(f"approved case lacks reviewer: {question_id}")
        elif review.get("manual_review_required"):
            errors.append(f"non-technical case unexpectedly requires manual review: {question_id}")
        for evidence in case.get("gold_evidence") or []:
            doc_id = str(evidence.get("doc_id") or "")
            document = docs.get(doc_id)
            if not document:
                errors.append(f"{question_id} evidence references missing doc {doc_id}")
                continue
            anchor = str(evidence.get("text_anchor") or "").strip()
            if not anchor:
                errors.append(f"{question_id} has empty evidence anchor")
                continue
            content = (dataset_dir / document["path"]).read_text(encoding="utf-8")
            if anchor not in content:
                errors.append(f"{question_id} anchor not found in {doc_id}: {anchor[:80]!r}")

    declared_review = metadata.get("manual_review") or {}
    if manual_review_required != 50:
        errors.append(f"expected 50 manual-review cases, found {manual_review_required}")
    if declared_review.get("required") != manual_review_required:
        errors.append("dataset.json manual_review.required does not match gold.jsonl")
    if declared_review.get("pending") != pending_manual:
        errors.append("dataset.json manual_review.pending does not match gold.jsonl")
    if pending_manual:
        warnings.append(f"{pending_manual} technical-doc cases still require human review")
    licences = {str(item.get("license")) for item in sources.get("sources") or []}
    if "CC-BY-NC-SA-4.0" not in licences:
        errors.append("sources.json does not preserve RGB's non-commercial licence")
    if metadata.get("commercial_use") is not False:
        errors.append("dataset must explicitly disable commercial use because it includes RGB")

    if len(doc_ids) < 300:
        warnings.append(f"only {len(doc_ids)} corpus documents; retrieval competition may be weak")
    return {
        "valid": not errors,
        "errors": errors,
        "warnings": warnings,
        "case_count": len(cases),
        "document_count": len(doc_ids),
        "component_counts": dict(sorted(component_counts.items())),
        "pending_human_review": pending_manual,
    }


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "dataset_dir", type=Path, nargs="?",
        default=repo_root / "evaluation" / "datasets" / "knowhub-cn-composite-rag-v1",
    )
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = validate(args.dataset_dir.resolve())
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered + "\n", encoding="utf-8")
    return 0 if report["valid"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
