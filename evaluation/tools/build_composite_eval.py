#!/usr/bin/env python3
"""Build KnowHub's 300-case Chinese composite RAG evaluation dataset.

The builder intentionally materializes only the passages referenced by the
selected questions. Upstream revisions, record IDs, licences and content
hashes are retained so the resulting benchmark is small and auditable.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import time
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable

import requests


DATASET_ID = "knowhub-cn-composite-rag-v1"
T2_REPO = "mteb/T2Retrieval"
T2_REVISION = "921dd3af6e78d1ae7ee0368aa8d7eaee02c8f08e"
CMRC_REVISION = "c0eb1b6ba219847457e6af3180da722bbeb656af"
RGB_REVISION = "65ec39e40e7dc9abb50e9bf1b4f32be3f6f16615"
KUBERNETES_REVISION = "3203edad9a1f98f4fd5a36c03512d9c73546c4e6"
DUBBO_REVISION = "84b35ebdab20a54da2c740aa8428a375ff43b481"

HF_ROWS_URL = "https://datasets-server.huggingface.co/rows"
HF_FILTER_URL = "https://datasets-server.huggingface.co/filter"
HF_DATASET_API_URL = f"https://huggingface.co/api/datasets/{T2_REPO}"
CMRC_URL = (
    "https://raw.githubusercontent.com/ymcui/cmrc2018/"
    f"{CMRC_REVISION}/data/cmrc2018_dev.json"
)
RGB_URLS = {
    "refine": (
        "https://raw.githubusercontent.com/chen700564/RGB/"
        f"{RGB_REVISION}/data/zh_refine.json"
    ),
    "integration": (
        "https://raw.githubusercontent.com/chen700564/RGB/"
        f"{RGB_REVISION}/data/zh_int.json"
    ),
    "counterfactual": (
        "https://raw.githubusercontent.com/chen700564/RGB/"
        f"{RGB_REVISION}/data/zh_fact.json"
    ),
}

COMPONENT_COUNTS = {
    "t2retrieval": 150,
    "cmrc2018": 50,
    "rgb": 50,
    "technical_docs": 50,
}


class SourceError(RuntimeError):
    """Raised when an upstream source no longer matches the pinned contract."""


def log(message: str) -> None:
    print(time.strftime("[%Y-%m-%d %H:%M:%S]"), message, flush=True)


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def normalize_corpus_content(content: str, max_line_chars: int = 160) -> str:
    """Materialize source text without parser-hostile HTML breaks or huge lines."""
    content = re.sub(r"<br\s*/?>", "\n", content, flags=re.IGNORECASE)
    result: list[str] = []
    for raw_line in content.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        line = raw_line.rstrip().replace("\t", "    ")
        while len(line) > max_line_chars:
            split_at = max(
                line.rfind(mark, 0, max_line_chars + 1)
                for mark in ("。", "！", "？", "；", ";", "，", ",", " ")
            )
            if split_at < max_line_chars // 2:
                split_at = max_line_chars
            else:
                split_at += 1
            result.append(line[:split_at].rstrip())
            line = line[split_at:].lstrip()
        result.append(line)
    return "\n".join(result).strip()


def slug(value: str) -> str:
    normalized = re.sub(r"[^A-Za-z0-9_.-]+", "-", value).strip("-.")
    return normalized or hashlib.sha256(value.encode("utf-8")).hexdigest()[:16]


def chunks(items: list[str], size: int) -> Iterable[list[str]]:
    for index in range(0, len(items), size):
        yield items[index:index + size]


def unique_strings(values: Iterable[Any]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        text = str(value).strip()
        if text and text not in seen:
            seen.add(text)
            result.append(text)
    return result


class SourceClient:
    def __init__(self, cache_dir: Path, offline: bool = False) -> None:
        self.cache_dir = cache_dir
        self.offline = offline
        self.session = requests.Session()
        self.session.headers["User-Agent"] = "KnowHub-composite-eval-builder/1.0"

    def _request(self, url: str, *, params: dict[str, Any] | None = None) -> requests.Response:
        if self.offline:
            raise SourceError(f"offline mode has no cached response for {url}")
        last_error: Exception | None = None
        for attempt in range(1, 9):
            try:
                response = self.session.get(url, params=params, timeout=(20, 180))
                response.raise_for_status()
                return response
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                if attempt < 8:
                    time.sleep(min(20, attempt * 2))
        raise SourceError(f"failed to fetch {url}: {last_error}")

    def text(self, url: str, cache_name: str) -> str:
        path = self.cache_dir / cache_name
        if path.exists() and path.stat().st_size:
            return path.read_text(encoding="utf-8-sig")
        response = self._request(url)
        text = response.content.decode("utf-8-sig")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return text

    def json(
        self,
        url: str,
        *,
        params: dict[str, Any] | None = None,
        cache_name: str | None = None,
    ) -> dict[str, Any]:
        path = self.cache_dir / cache_name if cache_name else None
        if path and path.exists() and path.stat().st_size:
            return json.loads(path.read_text(encoding="utf-8"))
        payload = self._request(url, params=params).json()
        if path:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        return payload


class DatasetWriter:
    def __init__(self, output_dir: Path) -> None:
        self.output_dir = output_dir
        self.corpus_dir = output_dir / "corpus"
        self.documents: dict[str, dict[str, Any]] = {}
        self.cases: list[dict[str, Any]] = []

    def add_document(
        self,
        *,
        doc_id: str,
        component: str,
        title: str,
        content: str,
        source_type: str,
        source_record_id: str,
        source_url: str,
        source_revision: str,
        license_id: str,
        attribution: str,
    ) -> str:
        existing = self.documents.get(doc_id)
        if existing:
            if existing["content_sha256"] != sha256_text(content):
                raise ValueError(f"conflicting content for {doc_id}")
            return doc_id
        relative_path = Path("corpus") / component / f"{slug(doc_id)}.md"
        path = self.output_dir / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        header = (
            f"# {title}\n\n"
            f"> 评测来源：{attribution}\n> 上游记录：{source_record_id}\n\n"
        )
        normalized_content = normalize_corpus_content(content)
        materialized = header + normalized_content + "\n"
        path.write_text(materialized, encoding="utf-8")
        self.documents[doc_id] = {
            "doc_id": doc_id,
            "component": component,
            "source_type": source_type,
            "title": title,
            "path": relative_path.as_posix(),
            "source_record_id": source_record_id,
            "source_url": source_url,
            "source_revision": source_revision,
            "license": license_id,
            "attribution": attribution,
            "content_sha256": sha256_text(content),
            "materialized_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "active": True,
            "knowledge_scope_code": component.upper(),
            "knowledge_scope_name": component,
            "business_category": "公开基准评测",
            "document_tags": f"正式评测,{component},{source_type}",
        }
        return doc_id

    def add_case(self, case: dict[str, Any]) -> None:
        self.cases.append(case)

    def finish(self, source_manifest: dict[str, Any]) -> None:
        self.output_dir.mkdir(parents=True, exist_ok=True)
        manifest = sorted(self.documents.values(), key=lambda item: item["doc_id"])
        (self.output_dir / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        with (self.output_dir / "gold.jsonl").open("w", encoding="utf-8", newline="\n") as handle:
            for case in self.cases:
                handle.write(json.dumps(case, ensure_ascii=False, separators=(",", ":")) + "\n")
        (self.output_dir / "sources.json").write_text(
            json.dumps(source_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )


def base_case(
    *,
    question_id: str,
    component: str,
    source_record_id: str,
    question_type: str,
    difficulty: str,
    question: str,
    expected_doc_ids: list[str],
    gold_answer: str,
    answer_facts: list[str],
    evidence: list[dict[str, Any]],
    modes: list[str],
    should_abstain: bool = False,
    should_clarify: bool = False,
    citation_required: bool = True,
    forbidden_claims: list[str] | None = None,
    tags: list[str] | None = None,
    conversation: list[dict[str, str]] | None = None,
    review_status: str = "auto_converted",
    reviewer: str | None = None,
    review_notes: str = "",
    reviewed_at: str | None = None,
) -> dict[str, Any]:
    return {
        "question_id": question_id,
        "split": "formal",
        "component": component,
        "source_record_id": source_record_id,
        "question_type": question_type,
        "difficulty": difficulty,
        "source_types": [component],
        "question": question,
        "conversation": conversation or [],
        "chat_mode": "AUTO_DOCUMENT",
        "selected_document_key": None,
        "expected_doc_ids": expected_doc_ids,
        "gold_answer": gold_answer,
        "answer_facts": answer_facts,
        "gold_evidence": evidence,
        "allowed_execution_modes": modes,
        "should_abstain": should_abstain,
        "should_clarify": should_clarify,
        "citation_required": citation_required,
        "forbidden_claims": forbidden_claims or [],
        "tags": tags or [],
        "review": {
            "manual_review_required": component == "technical_docs",
            "status": review_status,
            "reviewer": reviewer,
            "notes": review_notes,
            "reviewed_at": reviewed_at,
        },
    }


def hf_rows(client: SourceClient, config: str, offset: int, length: int) -> list[dict[str, Any]]:
    payload = client.json(
        HF_ROWS_URL,
        params={
            "dataset": T2_REPO,
            "config": config,
            "split": "dev",
            "offset": offset,
            "length": length,
        },
        cache_name=f"t2/rows-{config}-{offset}-{length}.json",
    )
    return [dict(item["row"]) for item in payload.get("rows", [])]


def hf_filter(client: SourceClient, config: str, column: str, values: list[str]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for batch in chunks(values, 20):
        predicates = [f'"{column}"=\'{value.replace(chr(39), chr(39) * 2)}\'' for value in batch]
        offset = 0
        while True:
            payload = client.json(
                HF_FILTER_URL,
                params={
                    "dataset": T2_REPO,
                    "config": config,
                    "split": "dev",
                    "where": " OR ".join(predicates),
                    "offset": offset,
                    "length": 100,
                },
                cache_name=(
                    f"t2/filter-{config}-{column}-{hashlib.sha256('|'.join(batch).encode()).hexdigest()[:16]}-"
                    f"{offset}.json"
                ),
            )
            if payload.get("partial"):
                raise SourceError(f"Hugging Face returned partial filter results for {config}")
            page = [dict(item["row"]) for item in payload.get("rows", [])]
            rows.extend(page)
            offset += len(page)
            total = int(payload.get("num_rows_total") or len(page))
            if not page or offset >= total:
                break
    return rows


def build_t2(client: SourceClient, writer: DatasetWriter) -> None:
    metadata = client.json(HF_DATASET_API_URL, cache_name="t2/dataset-metadata.json")
    actual_revision = str(metadata.get("sha") or "")
    if actual_revision != T2_REVISION:
        raise SourceError(
            f"T2Retrieval revision changed: expected {T2_REVISION}, got {actual_revision}"
        )
    query_candidates: list[dict[str, Any]] = []
    offset = 0
    candidate_count = COMPONENT_COUNTS["t2retrieval"] * 2
    while len(query_candidates) < candidate_count:
        page = hf_rows(client, "queries", offset, 100)
        if not page:
            break
        query_candidates.extend(item for item in page if str(item.get("text") or "").strip())
        offset += len(page)
    candidate_ids = [str(item["_id"]) for item in query_candidates]
    qrels = hf_filter(client, "default", "query-id", candidate_ids)
    relevant: dict[str, list[str]] = defaultdict(list)
    for item in qrels:
        if int(item.get("score") or 0) > 0:
            qid = str(item["query-id"])
            cid = str(item["corpus-id"])
            if cid not in relevant[qid] and len(relevant[qid]) < 2:
                relevant[qid].append(cid)
    queries = [item for item in query_candidates if relevant[str(item["_id"])]]
    queries = queries[:COMPONENT_COUNTS["t2retrieval"]]
    if len(queries) != COMPONENT_COUNTS["t2retrieval"]:
        raise SourceError(f"only found {len(queries)} usable T2 queries with qrels")
    query_ids = [str(item["_id"]) for item in queries]

    corpus_ids = unique_strings(cid for qid in query_ids for cid in relevant[qid])
    corpus_rows = hf_filter(client, "corpus", "_id", corpus_ids)
    corpus = {str(item["_id"]): item for item in corpus_rows}
    missing_corpus = [cid for cid in corpus_ids if cid not in corpus]
    if missing_corpus:
        raise SourceError(f"T2 corpus rows missing: {missing_corpus[:10]}")

    for cid in corpus_ids:
        row = corpus[cid]
        writer.add_document(
            doc_id=f"T2-{cid}",
            component="t2retrieval",
            title=str(row.get("title") or f"T2Retrieval passage {cid}"),
            content=str(row.get("text") or ""),
            source_type="retrieval_passage",
            source_record_id=cid,
            source_url=f"https://huggingface.co/datasets/{T2_REPO}",
            source_revision=T2_REVISION,
            license_id="Apache-2.0",
            attribution="T2Ranking / MTEB T2Retrieval",
        )

    for index, query in enumerate(queries, start=1):
        qid = str(query["_id"])
        expected = [f"T2-{cid}" for cid in relevant[qid]]
        writer.add_case(base_case(
            question_id=f"CMP-T2-{index:03d}",
            component="t2retrieval",
            source_record_id=qid,
            question_type="retrieval",
            difficulty="medium",
            question=str(query["text"]).strip(),
            expected_doc_ids=expected,
            gold_answer="",
            answer_facts=[],
            evidence=[],
            modes=["RETRIEVAL", "GRAPH_THEN_EVIDENCE"],
            citation_required=False,
            tags=["T2Retrieval", "RRF", "Rerank", "retrieval-only"],
        ))
    log(f"T2Retrieval: {len(queries)} cases, {len(corpus_ids)} passages")


def evenly_spaced(items: list[Any], count: int) -> list[Any]:
    if len(items) < count:
        raise SourceError(f"need {count} items, found {len(items)}")
    if len(items) == count:
        return list(items)
    return [items[(index * len(items)) // count] for index in range(count)]


def answer_anchor(content: str, answers: list[str]) -> str:
    for answer in answers:
        if answer and answer in content:
            return answer
    return content.strip()[:120]


def build_cmrc(client: SourceClient, writer: DatasetWriter) -> None:
    payload = json.loads(client.text(CMRC_URL, "cmrc2018/cmrc2018_dev.json"))
    eligible: list[tuple[str, str, dict[str, Any]]] = []
    if isinstance(payload, list):
        for context_item in payload:
            title = str(context_item.get("title") or "CMRC 2018")
            context = str(context_item.get("context_text") or "").strip()
            qas = [qa for qa in context_item.get("qas") or [] if qa.get("answers")]
            if 120 <= len(context) <= 3000 and len(qas) >= 2:
                eligible.append((
                    title,
                    str(context_item.get("context_id") or len(eligible)),
                    {"context": context, "qas": qas},
                ))
    else:
        for article in payload.get("data", []):
            title = str(article.get("title") or "CMRC 2018")
            for paragraph_index, paragraph in enumerate(article.get("paragraphs") or []):
                context = str(paragraph.get("context") or "").strip()
                qas = [qa for qa in paragraph.get("qas") or [] if qa.get("answers")]
                if 120 <= len(context) <= 3000 and len(qas) >= 2:
                    eligible.append((
                        title,
                        str(paragraph_index),
                        {"context": context, "qas": qas},
                    ))
    selected = evenly_spaced(eligible, 25)
    case_index = 0
    for doc_index, (title, context_id, paragraph) in enumerate(selected, start=1):
        doc_id = f"CMRC-{doc_index:03d}"
        context = paragraph["context"]
        source_record_id = f"dev:{context_id}"
        writer.add_document(
            doc_id=doc_id,
            component="cmrc2018",
            title=f"CMRC 2018：{title}",
            content=context,
            source_type="reading_comprehension_context",
            source_record_id=source_record_id,
            source_url="https://github.com/ymcui/cmrc2018",
            source_revision=CMRC_REVISION,
            license_id="CC-BY-SA-4.0",
            attribution="CMRC 2018, Cui et al.",
        )
        for qa in paragraph["qas"][:2]:
            case_index += 1
            raw_answers = qa.get("answers") or []
            answers = unique_strings(
                answer.get("text") if isinstance(answer, dict) else answer
                for answer in raw_answers
            )
            question_text = qa.get("question") or qa.get("query_text")
            question_id = qa.get("id") or qa.get("query_id") or source_record_id
            writer.add_case(base_case(
                question_id=f"CMP-CMRC-{case_index:03d}",
                component="cmrc2018",
                source_record_id=str(question_id),
                question_type="extractive_qa",
                difficulty="easy",
                question=str(question_text).strip(),
                expected_doc_ids=[doc_id],
                gold_answer=answers[0],
                answer_facts=answers,
                evidence=[{
                    "doc_id": doc_id,
                    "section_path": title,
                    "text_anchor": answer_anchor(context, answers),
                    "required": True,
                }],
                modes=["RETRIEVAL", "GRAPH_THEN_EVIDENCE"],
                tags=["CMRC2018", "基础问答", "引用"],
            ))
    if case_index != COMPONENT_COUNTS["cmrc2018"]:
        raise SourceError(f"CMRC produced {case_index} cases")
    log(f"CMRC 2018: {case_index} cases, {len(selected)} contexts")


def parse_jsonl(text: str, source_name: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_number, line in enumerate(text.splitlines(), start=1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError as exc:
            raise SourceError(f"invalid JSONL in {source_name}:{line_number}: {exc}") from exc
        if not isinstance(item, dict):
            raise SourceError(f"non-object JSONL row in {source_name}:{line_number}")
        rows.append(item)
    return rows


def add_rgb_passage(
    writer: DatasetWriter,
    *,
    group: str,
    record_id: str,
    kind: str,
    index: int,
    content: str,
) -> str:
    doc_id = f"RGB-{group.upper()}-{record_id}-{kind.upper()}-{index:02d}"
    return writer.add_document(
        doc_id=doc_id,
        component="rgb",
        title=f"RGB {group} {record_id} {kind} {index}",
        content=content,
        source_type=f"rgb_{kind}",
        source_record_id=f"{group}:{record_id}:{kind}:{index}",
        source_url="https://github.com/chen700564/RGB",
        source_revision=RGB_REVISION,
        license_id="CC-BY-NC-SA-4.0",
        attribution="RGB: Benchmarking Large Language Models in Retrieval-Augmented Generation",
    )


def rgb_answers(item: dict[str, Any]) -> list[str]:
    value = item.get("answer")
    if isinstance(value, list):
        return unique_strings(value)
    return unique_strings([value])


def usable_zh_passages(values: Iterable[Any]) -> list[str]:
    passages: list[str] = []
    for value in values:
        text = str(value).strip()
        cjk_count = len(re.findall(r"[\u3400-\u9fff]", text))
        if text and "\ufffd" not in text and cjk_count >= 20:
            passages.append(text)
    return passages


def usable_integration_groups(item: dict[str, Any]) -> list[list[str]]:
    groups: list[list[str]] = []
    for value in item.get("positive") or []:
        passages = usable_zh_passages(value if isinstance(value, list) else [value])
        if passages:
            groups.append(passages)
    return groups


def build_rgb(client: SourceClient, writer: DatasetWriter) -> None:
    sources = {
        name: parse_jsonl(client.text(url, f"rgb/{name}.jsonl"), name)
        for name, url in RGB_URLS.items()
    }
    refine = [
        item for item in sources["refine"]
        if usable_zh_passages(item.get("positive") or [])
        and len(usable_zh_passages(item.get("negative") or [])) >= 5
        and rgb_answers(item)
    ]
    integration = [
        item for item in sources["integration"]
        if len(usable_integration_groups(item)) >= 2
        and len(usable_zh_passages(item.get("negative") or [])) >= 3
        and rgb_answers(item)
    ]
    counterfactual = [
        item for item in sources["counterfactual"]
        if usable_zh_passages(item.get("positive") or [])
        and usable_zh_passages(item.get("positive_wrong") or [])
        and len(usable_zh_passages(item.get("negative") or [])) >= 2
        and rgb_answers(item)
    ]
    if len(refine) < 30 or len(integration) < 10 or len(counterfactual) < 10:
        raise SourceError("RGB source does not contain enough eligible rows")

    case_index = 0
    for item in refine[:20]:
        case_index += 1
        record_id = str(item["id"])
        answers = rgb_answers(item)
        positive_text = usable_zh_passages(item["positive"])[0]
        positive_id = add_rgb_passage(
            writer, group="noise", record_id=record_id, kind="positive", index=1,
            content=positive_text,
        )
        for passage_index, passage in enumerate(usable_zh_passages(item["negative"])[:4], start=1):
            add_rgb_passage(
                writer, group="noise", record_id=record_id, kind="negative",
                index=passage_index, content=str(passage),
            )
        writer.add_case(base_case(
            question_id=f"CMP-RGB-{case_index:03d}",
            component="rgb",
            source_record_id=f"zh_refine:{record_id}",
            question_type="noise_robustness",
            difficulty="hard",
            question=str(item["query"]).strip(),
            expected_doc_ids=[positive_id],
            gold_answer="、".join(answers),
            answer_facts=answers,
            evidence=[{
                "doc_id": positive_id,
                "section_path": "RGB positive evidence",
                "text_anchor": answer_anchor(positive_text, answers),
                "required": True,
            }],
            modes=["RETRIEVAL", "GRAPH_THEN_EVIDENCE"],
            tags=["RGB", "噪声鲁棒性", "noise-rate-0.8"],
        ))

    for item in refine[20:30]:
        case_index += 1
        record_id = str(item["id"])
        for passage_index, passage in enumerate(usable_zh_passages(item["negative"])[:5], start=1):
            add_rgb_passage(
                writer, group="reject", record_id=record_id, kind="negative",
                index=passage_index, content=str(passage),
            )
        writer.add_case(base_case(
            question_id=f"CMP-RGB-{case_index:03d}",
            component="rgb",
            source_record_id=f"zh_refine:{record_id}:negative-only",
            question_type="answer_rejection",
            difficulty="hard",
            question=str(item["query"]).strip(),
            expected_doc_ids=[],
            gold_answer="现有文档无法回答该问题。",
            answer_facts=[],
            evidence=[],
            modes=["RETRIEVAL", "CLARIFICATION"],
            should_abstain=True,
            citation_required=False,
            forbidden_claims=rgb_answers(item),
            tags=["RGB", "拒答", "negative-only"],
        ))

    for item in integration[:10]:
        case_index += 1
        record_id = str(item["id"])
        answers = rgb_answers(item)
        expected: list[str] = []
        evidence: list[dict[str, Any]] = []
        for group_index, positive_group in enumerate(usable_integration_groups(item)[:2], start=1):
            text = positive_group[0]
            doc_id = add_rgb_passage(
                writer, group="integration", record_id=record_id, kind="positive",
                index=group_index, content=text,
            )
            expected.append(doc_id)
            fact_answers = answers[group_index - 1:group_index]
            evidence.append({
                "doc_id": doc_id,
                "section_path": f"RGB integration evidence {group_index}",
                "text_anchor": answer_anchor(text, fact_answers),
                "required": True,
            })
        for passage_index, passage in enumerate(usable_zh_passages(item["negative"])[:3], start=1):
            add_rgb_passage(
                writer, group="integration", record_id=record_id, kind="negative",
                index=passage_index, content=str(passage),
            )
        writer.add_case(base_case(
            question_id=f"CMP-RGB-{case_index:03d}",
            component="rgb",
            source_record_id=f"zh_int:{record_id}",
            question_type="information_integration",
            difficulty="hard",
            question=str(item["query"]).strip(),
            expected_doc_ids=expected,
            gold_answer="、".join(answers),
            answer_facts=answers,
            evidence=evidence,
            modes=["RETRIEVAL", "GRAPH_THEN_EVIDENCE"],
            tags=["RGB", "信息整合", "多证据"],
        ))

    for item in counterfactual[:10]:
        case_index += 1
        record_id = str(item["id"])
        answers = rgb_answers(item)
        true_text = usable_zh_passages(item["positive"])[0]
        true_id = add_rgb_passage(
            writer, group="counterfactual", record_id=record_id, kind="positive",
            index=1, content=true_text,
        )
        add_rgb_passage(
            writer, group="counterfactual", record_id=record_id, kind="counterfactual",
            index=1, content=usable_zh_passages(item["positive_wrong"])[0],
        )
        for passage_index, passage in enumerate(usable_zh_passages(item["negative"])[:2], start=1):
            add_rgb_passage(
                writer, group="counterfactual", record_id=record_id, kind="negative",
                index=passage_index, content=str(passage),
            )
        writer.add_case(base_case(
            question_id=f"CMP-RGB-{case_index:03d}",
            component="rgb",
            source_record_id=f"zh_fact:{record_id}",
            question_type="counterfactual_robustness",
            difficulty="hard",
            question=str(item["query"]).strip(),
            expected_doc_ids=[true_id],
            gold_answer="、".join(answers),
            answer_facts=answers,
            evidence=[{
                "doc_id": true_id,
                "section_path": "RGB factual evidence",
                "text_anchor": answer_anchor(true_text, answers),
                "required": True,
            }],
            modes=["RETRIEVAL", "GRAPH_THEN_EVIDENCE"],
            forbidden_claims=unique_strings([item.get("fakeanswer")]),
            tags=["RGB", "反事实", "冲突证据"],
        ))
    if case_index != COMPONENT_COUNTS["rgb"]:
        raise SourceError(f"RGB produced {case_index} cases")
    log(f"RGB: {case_index} cases")


def raw_doc_url(project: str, path: str) -> tuple[str, str, str, str]:
    if project == "kubernetes":
        return (
            f"https://raw.githubusercontent.com/kubernetes/website/{KUBERNETES_REVISION}/{path}",
            KUBERNETES_REVISION,
            "CC-BY-4.0",
            "Kubernetes Authors",
        )
    if project == "dubbo":
        return (
            f"https://raw.githubusercontent.com/apache/dubbo-website/{DUBBO_REVISION}/{path}",
            DUBBO_REVISION,
            "Apache-2.0",
            "Apache Dubbo",
        )
    raise ValueError(f"unknown docs project {project}")


def build_technical_docs(
    client: SourceClient,
    writer: DatasetWriter,
    case_spec_path: Path,
) -> None:
    specs = parse_jsonl(case_spec_path.read_text(encoding="utf-8"), str(case_spec_path))
    if len(specs) != COMPONENT_COUNTS["technical_docs"]:
        raise SourceError(f"technical docs spec must contain 50 cases, found {len(specs)}")
    source_docs: dict[tuple[str, str], tuple[str, str]] = {}
    for spec in specs:
        project = str(spec["project"])
        path = str(spec["source_path"])
        key = (project, path)
        if key not in source_docs:
            url, revision, license_id, attribution = raw_doc_url(project, path)
            content = client.text(url, f"technical-docs/{project}/{path}")
            doc_id = f"DOC-{project.upper()}-{hashlib.sha256(path.encode()).hexdigest()[:10].upper()}"
            writer.add_document(
                doc_id=doc_id,
                component="technical_docs",
                title=str(spec["document_title"]),
                content=content,
                source_type=f"{project}_official_documentation",
                source_record_id=path,
                source_url=url,
                source_revision=revision,
                license_id=license_id,
                attribution=attribution,
            )
            source_docs[key] = (doc_id, content)

    for index, spec in enumerate(specs, start=1):
        key = (str(spec["project"]), str(spec["source_path"]))
        doc_id, content = source_docs[key]
        anchor = str(spec.get("text_anchor") or "").strip()
        if anchor and anchor not in content:
            raise SourceError(f"technical docs anchor not found for case {index}: {anchor!r}")
        conversation = list(spec.get("conversation") or [])
        question = str(spec["question"]).strip()
        if conversation and conversation[-1].get("role") == "user":
            question = str(conversation[-1]["content"]).strip()
        modes = list(spec.get("allowed_execution_modes") or ["GRAPH_THEN_EVIDENCE"])
        should_clarify = bool(spec.get("should_clarify", False))
        evidence = [] if should_clarify else [{
            "doc_id": doc_id,
            "section_path": str(spec["section_path"]),
            "text_anchor": anchor,
            "required": True,
        }]
        writer.add_case(base_case(
            question_id=f"CMP-DOC-{index:03d}",
            component="technical_docs",
            source_record_id=f"{key[0]}:{key[1]}:case-{index}",
            question_type=str(spec["question_type"]),
            difficulty=str(spec.get("difficulty") or "hard"),
            question=question,
            expected_doc_ids=[] if should_clarify else [doc_id],
            gold_answer=str(spec["gold_answer"]),
            answer_facts=unique_strings(spec.get("answer_facts") or []),
            evidence=evidence,
            modes=modes,
            should_clarify=should_clarify,
            citation_required=not should_clarify,
            forbidden_claims=unique_strings(spec.get("forbidden_claims") or []),
            tags=unique_strings([key[0], *list(spec.get("tags") or [])]),
            conversation=conversation,
            review_status=str(spec.get("review_status") or "pending_human_review"),
            reviewer=spec.get("reviewer"),
            review_notes=str(spec.get("review_notes") or ""),
            reviewed_at=spec.get("reviewed_at"),
        ))
    log(f"Technical docs: {len(specs)} cases, {len(source_docs)} source documents")


def dataset_metadata(writer: DatasetWriter) -> dict[str, Any]:
    counts: dict[str, int] = defaultdict(int)
    type_counts: dict[str, int] = defaultdict(int)
    for case in writer.cases:
        counts[case["component"]] += 1
        type_counts[case["question_type"]] += 1
    return {
        "dataset_id": DATASET_ID,
        "name": "KnowHub 中文复合 RAG 正式评测集 v1",
        "version": "1.0.0-rc1",
        "language": "zh-CN",
        "license": "mixed; see sources.json and THIRD_PARTY_NOTICES.md",
        "commercial_use": False,
        "description": "由 T2Retrieval、CMRC 2018、RGB 与 Kubernetes/Apache Dubbo 中文文档构成的 300 题小型复合评测集。",
        "document_count": len(writer.documents),
        "case_count": len(writer.cases),
        "component_counts": dict(sorted(counts.items())),
        "question_type_counts": dict(sorted(type_counts.items())),
        "manual_review": {
            "required": 50,
            "pending": sum(
                1 for case in writer.cases
                if case["review"]["status"] == "pending_human_review"
            ),
        },
        "supported_execution_modes": [
            "GRAPH_ONLY", "GRAPH_THEN_EVIDENCE", "RETRIEVAL", "CLARIFICATION"
        ],
    }


def source_manifest() -> dict[str, Any]:
    return {
        "generated_at": "deterministic-build; timestamp intentionally omitted",
        "selection_policy": {
            "t2retrieval": "first 150 non-empty queries that have qrels; at most first 2 positive qrels per query",
            "cmrc2018": "25 evenly-spaced eligible dev paragraphs; first 2 questions per paragraph",
            "rgb": "20 noise + 10 negative-only rejection + 10 integration + 10 counterfactual",
            "technical_docs": "50 curated candidates from pinned official Chinese docs; human review pending",
        },
        "sources": [
            {"name": "T2Retrieval", "revision": T2_REVISION, "license": "Apache-2.0", "url": f"https://huggingface.co/datasets/{T2_REPO}"},
            {"name": "CMRC 2018", "revision": CMRC_REVISION, "license": "CC-BY-SA-4.0", "url": "https://github.com/ymcui/cmrc2018"},
            {"name": "RGB", "revision": RGB_REVISION, "license": "CC-BY-NC-SA-4.0", "url": "https://github.com/chen700564/RGB", "restriction": "non-commercial use only"},
            {"name": "Kubernetes Chinese documentation", "revision": KUBERNETES_REVISION, "license": "CC-BY-4.0", "url": "https://github.com/kubernetes/website"},
            {"name": "Apache Dubbo Chinese documentation", "revision": DUBBO_REVISION, "license": "Apache-2.0", "url": "https://github.com/apache/dubbo-website"},
        ],
    }


def build(args: argparse.Namespace) -> None:
    output_dir = args.output_dir.resolve()
    corpus_dir = output_dir / "corpus"
    if corpus_dir.exists():
        shutil.rmtree(corpus_dir)
    for generated_name in ("dataset.json", "gold.jsonl", "manifest.json", "sources.json"):
        generated_path = output_dir / generated_name
        if generated_path.exists():
            generated_path.unlink()
    output_dir.mkdir(parents=True, exist_ok=True)
    writer = DatasetWriter(output_dir)
    client = SourceClient(args.cache_dir.resolve(), offline=args.offline)
    build_t2(client, writer)
    build_cmrc(client, writer)
    build_rgb(client, writer)
    build_technical_docs(client, writer, args.case_spec.resolve())
    expected_total = sum(COMPONENT_COUNTS.values())
    if len(writer.cases) != expected_total:
        raise SourceError(f"expected {expected_total} cases, produced {len(writer.cases)}")
    writer.finish(source_manifest())
    metadata = dataset_metadata(writer)
    (output_dir / "dataset.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    log(f"Built {len(writer.cases)} cases and {len(writer.documents)} documents at {output_dir}")


def parse_args() -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[2]
    evaluation_dir = repo_root / "evaluation"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir", type=Path,
        default=evaluation_dir / "datasets" / DATASET_ID,
    )
    parser.add_argument(
        "--cache-dir", type=Path,
        default=evaluation_dir / ".source-cache" / "composite-downloads",
    )
    parser.add_argument(
        "--case-spec", type=Path,
        default=evaluation_dir / "datasets" / f"{DATASET_ID}.technical-doc-cases.jsonl",
    )
    parser.add_argument("--offline", action="store_true", help="reuse cached upstream files only")
    return parser.parse_args()


if __name__ == "__main__":
    build(parse_args())
