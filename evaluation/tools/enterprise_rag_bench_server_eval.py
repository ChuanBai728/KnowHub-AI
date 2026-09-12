#!/usr/bin/env python3
"""
Run a small EnterpriseRAG-Bench slice against a live KnowHub AI server.

The script intentionally uses KnowHub's public HTTP APIs instead of writing
directly to MySQL/PostgreSQL/Elasticsearch, so import and evaluation follow the
same path as a real user upload.

Run caliber
-----------
每条观测记录都会带上 `run_config`（见 `load_rag_run_config`），记录本次实际生效的
RAG 配置，使报告能自证测量口径。用 `--rag-config-yaml` 指定要记录的文件；当后端以
`evaluation/application-eval.yaml` 启动时务必指向它，否则记录的是默认的
`knowhub-app/src/main/resources/application.yaml`，与后端实际运行配置不符。

要点：`force-retrieval-mode: true` 会强制所有 AUTO_DOCUMENT 请求走 RETRIEVAL 并关闭
澄清，此时「路由准确率」结构性失效（期望 GRAPH_ONLY/GRAPH_THEN_EVIDENCE/CLARIFICATION
的题目必然判错），该指标在技术文档子集分里占 35%。报告头部会对该口径显式告警。

Retrieval scope
---------------
实际参与检索的文档数只在路由阶段快照的 `retrievalScopeDocumentCount` 里，本脚本把它
提取到观测记录顶层。不要用 `route_document_ids` 判断检索范围：它取自路由服务的中间候选
列表，受 ROUTE_DOCUMENT_CANDIDATE_LIMIT = 100 约束，不反映最终范围。
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import time
import uuid
import zipfile
from collections import OrderedDict
from pathlib import Path
from typing import Any, Iterable

import requests

try:
    import yaml
except ImportError:  # 缺失时降级为「不记录运行口径」，绝不中断评测
    yaml = None


DATASET_ID = "onyx-dot-app/EnterpriseRAG-Bench"
RAW_QUESTIONS_URL = "https://raw.githubusercontent.com/onyx-dot-app/EnterpriseRAG-Bench/main/questions.jsonl"
ALL_DOCUMENTS_ZIP_URL = "https://github.com/onyx-dot-app/EnterpriseRAG-Bench/releases/download/v1.0.0/all_documents.zip"
PARSE_SUCCESS = 3
INDEX_SUCCESS = 3

# 运行口径白名单：(yaml 键路径, 记录键名)。只采集标量，把 answer-system-prompt 等
# 巨型文本挡在每条观测记录之外。口径随观测记录落盘，使报告能自证它是按什么配置跑出来的。
RAG_RUN_CONFIG_KEYS = (
    ("force-retrieval-mode", "force_retrieval_mode"),
    ("force-retrieval-full-corpus", "force_retrieval_full_corpus"),
    ("clarification-enabled", "clarification_enabled"),
    ("keyword-channel-enabled", "keyword_channel_enabled"),
    ("rerank-enabled", "rerank_enabled"),
    ("min-vector-similarity", "min_vector_similarity"),
    ("route-document-top-k", "route_document_top_k"),
    ("low-confidence-route-document-top-k", "low_confidence_route_document_top_k"),
    ("route-history-max-chars", "route_history_max_chars"),
    ("rrf.k", "rrf_k"),
    ("rrf.vector-weight", "rrf_vector_weight"),
    ("rrf.keyword-weight", "rrf_keyword_weight"),
    ("rrf.adaptive-keyword-weight-enabled", "rrf_adaptive_keyword_weight_enabled"),
    ("rrf.exact-term-keyword-weight", "rrf_exact_term_keyword_weight"),
)

# 路由阶段快照的判别键。ROUTE 阶段在 stageTraces 里会出现两次（知识范围路由 + 执行路径
# 路由），只有前者带这些键，所以必须按键集匹配，不能按 stageCode 或位置取。
ROUTE_SCOPE_SNAPSHOT_KEYS = (
    "routeStatus",
    "candidateDocumentCount",
    "retrievalScopeDocumentCount",
    "knowledgeRouteBypassed",
)


def log(message: str) -> None:
    print(time.strftime("[%Y-%m-%d %H:%M:%S]"), message, flush=True)


def require_ok(response: requests.Response, action: str) -> dict[str, Any]:
    if not response.ok:
        raise RuntimeError(f"{action} HTTP {response.status_code}: {response.text[:1000]}")
    try:
        payload = response.json()
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(f"{action} returned non-JSON response: {response.text[:1000]}") from exc
    code = payload.get("code")
    if code not in (0, 200, "0", "200", None):
        raise RuntimeError(f"{action} failed: {json.dumps(payload, ensure_ascii=False)[:1000]}")
    return payload


def load_hf_dataset(config: str):
    try:
        from datasets import load_dataset
    except ImportError as exc:
        raise RuntimeError("datasets is not installed") from exc
    return load_dataset(DATASET_ID, config, split="test", streaming=True)


def normalize_question(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "question_id": str(item["question_id"]),
        "question_type": item.get("question_type"),
        "source_types": list(item.get("source_types") or []),
        "question": item["question"],
        "expected_doc_ids": list(item.get("expected_doc_ids") or []),
        "gold_answer": item.get("gold_answer", ""),
        "answer_facts": list(item.get("answer_facts") or []),
    }


def normalize_document(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "doc_id": str(item["doc_id"]),
        "source_type": item.get("source_type", ""),
        "title": item.get("title", ""),
        "content": item.get("content", ""),
    }


def prepare_subset(output_dir: Path, question_count: int, document_count: int) -> None:
    try:
        prepare_subset_from_huggingface(output_dir, question_count, document_count)
    except Exception as exc:  # noqa: BLE001
        log(f"HuggingFace datasets path unavailable, falling back to GitHub release zip: {exc}")
        prepare_subset_from_github_release(output_dir, question_count, document_count)


def prepare_subset_from_huggingface(output_dir: Path, question_count: int, document_count: int) -> None:
    corpus_dir = output_dir / "corpus"
    corpus_dir.mkdir(parents=True, exist_ok=True)

    questions: list[dict[str, Any]] = []
    expected_doc_ids: OrderedDict[str, None] = OrderedDict()
    for item in load_hf_dataset("questions"):
        question = normalize_question(dict(item))
        questions.append(question)
        for doc_id in question["expected_doc_ids"]:
            expected_doc_ids[str(doc_id)] = None
        if len(questions) >= question_count:
            break

    if not questions:
        raise RuntimeError("No questions loaded from EnterpriseRAG-Bench.")

    selected_docs: OrderedDict[str, dict[str, Any]] = OrderedDict()
    distractors: OrderedDict[str, dict[str, Any]] = OrderedDict()
    expected = set(expected_doc_ids.keys())

    for item in load_hf_dataset("documents"):
        doc = normalize_document(dict(item))
        doc_id = doc["doc_id"]
        if doc_id in expected and doc_id not in selected_docs:
            selected_docs[doc_id] = doc
        elif doc_id not in distractors and doc_id not in expected:
            distractors[doc_id] = doc
        if len(selected_docs) >= len(expected) and len(selected_docs) + len(distractors) >= document_count:
            break

    missing = [doc_id for doc_id in expected if doc_id not in selected_docs]
    if missing:
        raise RuntimeError(f"Missing expected docs from stream: {missing[:10]}")

    for doc_id, doc in distractors.items():
        if len(selected_docs) >= document_count:
            break
        selected_docs[doc_id] = doc

    if len(selected_docs) < document_count:
        raise RuntimeError(f"Only selected {len(selected_docs)} documents; requested {document_count}.")

    manifest = []
    for doc in selected_docs.values():
        safe_name = re.sub(r"[^A-Za-z0-9_.-]+", "_", doc["doc_id"]) + ".md"
        path = corpus_dir / safe_name
        text = f"# {doc['title']}\n\nSource type: {doc['source_type']}\nOfficial doc id: {doc['doc_id']}\n\n{doc['content']}\n"
        path.write_text(text, encoding="utf-8")
        manifest.append({
            "doc_id": doc["doc_id"],
            "source_type": doc["source_type"],
            "title": doc["title"],
            "path": str(path.relative_to(output_dir)).replace("\\", "/"),
        })

    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    with (output_dir / "gold.jsonl").open("w", encoding="utf-8") as handle:
        for item in questions:
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")

    log(f"Prepared {len(manifest)} documents and {len(questions)} questions at {output_dir}")


def download_file(url: str, target: Path) -> None:
    if target.exists() and target.stat().st_size > 0:
        log(f"Using cached {target}")
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_suffix(target.suffix + ".part")
    last_error = None
    for attempt in range(1, 6):
        done = tmp.stat().st_size if tmp.exists() else 0
        headers = {"Range": f"bytes={done}-"} if done else {}
        mode = "ab" if done else "wb"
        log(f"Downloading attempt {attempt}/5 {url} -> {target} offset={done}")
        try:
            with requests.get(url, stream=True, timeout=(20, 300), headers=headers) as response:
                if response.status_code == 416:
                    break
                response.raise_for_status()
                total_header = response.headers.get("content-length")
                total = (done + int(total_header)) if total_header and headers else int(total_header or 0)
                last_log = time.time()
                with tmp.open(mode) as handle:
                    for chunk in response.iter_content(chunk_size=1024 * 1024):
                        if not chunk:
                            continue
                        handle.write(chunk)
                        done += len(chunk)
                        if time.time() - last_log >= 30:
                            if total:
                                log(f"Downloaded {done / 1024 / 1024:.1f}/{total / 1024 / 1024:.1f} MiB")
                            else:
                                log(f"Downloaded {done / 1024 / 1024:.1f} MiB")
                            last_log = time.time()
            tmp.replace(target)
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            log(f"Download attempt {attempt} failed: {exc}")
            time.sleep(min(60, attempt * 10))
    if tmp.exists() and tmp.stat().st_size > 0:
        tmp.replace(target)
        return
    raise RuntimeError(f"Download failed for {url}: {last_error}")


def load_questions_from_raw(output_dir: Path, question_count: int) -> list[dict[str, Any]]:
    cache = output_dir / "cache" / "questions.jsonl"
    download_file(RAW_QUESTIONS_URL, cache)
    questions = []
    with cache.open("r", encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            questions.append(normalize_question(json.loads(line)))
            if len(questions) >= question_count:
                break
    return questions


def zip_doc_id(name: str) -> str:
    stem = Path(name).stem
    match = re.search(r"(dsid_[A-Za-z0-9]+)", stem)
    return match.group(1) if match else stem


def read_zip_text(archive: zipfile.ZipFile, member: str) -> str:
    data = archive.read(member)
    for encoding in ("utf-8", "utf-8-sig", "latin-1"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", errors="replace")


def prepare_subset_from_github_release(output_dir: Path, question_count: int, document_count: int) -> None:
    corpus_dir = output_dir / "corpus"
    corpus_dir.mkdir(parents=True, exist_ok=True)
    questions = load_questions_from_raw(output_dir, question_count)
    if not questions:
        raise RuntimeError("No questions loaded from raw questions.jsonl.")

    expected_doc_ids: OrderedDict[str, None] = OrderedDict()
    for question in questions:
        for doc_id in question["expected_doc_ids"]:
            expected_doc_ids[str(doc_id)] = None

    zip_path = output_dir / "cache" / "all_documents.zip"
    download_file(ALL_DOCUMENTS_ZIP_URL, zip_path)

    selected_members: OrderedDict[str, str] = OrderedDict()
    distractor_members: OrderedDict[str, str] = OrderedDict()
    with zipfile.ZipFile(zip_path) as archive:
        names = [name for name in archive.namelist() if not name.endswith("/")]
        for name in names:
            doc_id = zip_doc_id(name)
            if doc_id in expected_doc_ids and doc_id not in selected_members:
                selected_members[doc_id] = name
            elif doc_id not in expected_doc_ids and doc_id not in distractor_members:
                distractor_members[doc_id] = name
            if len(selected_members) >= len(expected_doc_ids) and len(selected_members) + len(distractor_members) >= document_count:
                break

        missing = [doc_id for doc_id in expected_doc_ids if doc_id not in selected_members]
        if missing:
            raise RuntimeError(f"Missing expected docs in all_documents.zip: {missing[:10]}")

        for doc_id, name in distractor_members.items():
            if len(selected_members) >= document_count:
                break
            selected_members[doc_id] = name

        manifest = []
        for doc_id, member in selected_members.items():
            source_text = read_zip_text(archive, member)
            title = Path(member).name
            safe_name = re.sub(r"[^A-Za-z0-9_.-]+", "_", doc_id) + ".md"
            path = corpus_dir / safe_name
            text = f"# {title}\n\nOfficial doc id: {doc_id}\nOriginal file: {member}\n\n{source_text}\n"
            path.write_text(text, encoding="utf-8")
            manifest.append({
                "doc_id": doc_id,
                "source_type": "",
                "title": title,
                "path": str(path.relative_to(output_dir)).replace("\\", "/"),
            })

    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    with (output_dir / "gold.jsonl").open("w", encoding="utf-8") as handle:
        for item in questions:
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")
    log(f"Prepared {len(manifest)} release documents and {len(questions)} questions at {output_dir}")


class KnowHubClient:
    def __init__(self, base_url: str, username: str, password: str, timeout: int):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.login(username, password)

    def login(self, username: str, password: str) -> None:
        payload = {"username": username, "password": password}
        response = self.session.post(
            f"{self.base_url}/api/v1/admin/auth/login",
            json=payload,
            timeout=self.timeout,
        )
        data = require_ok(response, "login").get("data") or {}
        token = data.get("token")
        if not token:
            raise RuntimeError("Login response did not include token.")
        self.session.headers.update({"Authorization": f"Bearer {token}"})

    def post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        response = self.session.post(f"{self.base_url}{path}", json=payload, timeout=self.timeout)
        return require_ok(response, path).get("data") or {}

    def upload_document(self, file_path: Path, meta: dict[str, Any]) -> dict[str, Any]:
        with file_path.open("rb") as handle:
            files = {
                "file": (file_path.name, handle, "text/markdown"),
                "meta": (None, json.dumps(meta, ensure_ascii=False), "application/json"),
            }
            response = self.session.post(
                f"{self.base_url}/api/v1/manage/document/upload",
                files=files,
                timeout=max(self.timeout, 120),
            )
        return require_ok(response, "upload").get("data") or {}

    def stream_chat(self, payload: dict[str, Any], timeout: int) -> tuple[str, list[dict[str, Any]]]:
        events: list[dict[str, Any]] = []
        answer_parts: list[str] = []
        response = self.session.post(
            f"{self.base_url}/api/v1/chat/stream",
            json=payload,
            headers={**self.session.headers, "Accept": "text/event-stream"},
            stream=True,
            timeout=timeout,
        )
        if not response.ok:
            raise RuntimeError(f"stream HTTP {response.status_code}: {response.text[:1000]}")

        for raw_line in response.iter_lines(decode_unicode=True):
            if raw_line is None:
                continue
            line = raw_line.strip()
            if not line.startswith("data:"):
                continue
            text = line[5:].strip()
            if not text or text == "[DONE]":
                continue
            try:
                event = json.loads(text)
                events.append(event)
                event_text = event.get("content") or event.get("text") or event.get("delta") or ""
                if isinstance(event_text, str):
                    answer_parts.append(event_text)
            except json.JSONDecodeError:
                answer_parts.append(text)
        return "".join(answer_parts), events


def read_manifest(output_dir: Path) -> list[dict[str, Any]]:
    return json.loads((output_dir / "manifest.json").read_text(encoding="utf-8"))


def read_gold(output_dir: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in (output_dir / "gold.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]


def wait_for_condition(label: str, timeout_seconds: int, interval: int, fn):
    deadline = time.time() + timeout_seconds
    last = None
    while time.time() < deadline:
        last = fn()
        if last:
            return last
        time.sleep(interval)
    raise TimeoutError(f"Timed out waiting for {label}; last={last}")


def wait_for_chat_terminal_status(client: KnowHubClient, conversation_id: str,
                                  timeout_seconds: int = 30) -> dict[str, Any]:
    """Wait for asynchronous turn finalization after the SSE stream closes."""
    deadline = time.time() + timeout_seconds
    session: dict[str, Any] = {}
    while time.time() < deadline:
        session = client.post("/api/v1/chat/session/detail", {"conversationId": conversation_id})
        status = str(session.get("latestTurnStatus") or "UNKNOWN").upper()
        if status not in {"RUNNING", "UNKNOWN", ""}:
            return session
        time.sleep(1)
    return session


def extract_steps(pipeline: dict[str, Any] | None) -> list[dict[str, Any]]:
    steps = ((pipeline or {}).get("steps") or [])
    result = []
    for index, step in enumerate(steps, start=1):
        result.append({
            "stepNo": get_any(step, "stepNo", "step_no") or index,
            "strategyType": get_any(step, "strategyType", "strategy_type"),
        })
    return [item for item in result if item.get("strategyType") is not None]


def get_any(data: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in data:
            return data[key]
    return None


def status_is(data: dict[str, Any], expected: int, *keys: str) -> bool:
    value = get_any(data, *keys)
    return str(value) == str(expected)


def import_documents(client: KnowHubClient, output_dir: Path, poll_seconds: int) -> None:
    manifest = read_manifest(output_dir)
    state_path = output_dir / "import-state.json"
    state = {"items": []}
    if state_path.exists():
        state = json.loads(state_path.read_text(encoding="utf-8"))
    by_doc_id = {item["doc_id"]: item for item in state.get("items", [])}

    for index, item in enumerate(manifest, start=1):
        state_item = by_doc_id.get(item["doc_id"])
        if state_item and str(state_item.get("indexStatus")) == str(INDEX_SUCCESS):
            continue

        path = output_dir / item["path"]
        log(f"Importing {index}/{len(manifest)} {item['doc_id']} {item['title'][:80]}")
        upload = client.upload_document(path, {
            "documentName": item["title"] or item["doc_id"],
            "operatorId": "enterprise-rag-bench",
            "knowledgeScopeCode": "enterprise-rag-bench",
            "knowledgeScopeName": "EnterpriseRAG-Bench",
            "businessCategory": item.get("source_type") or "enterprise",
            "documentTags": f"enterprise-rag-bench,{item.get('source_type') or 'unknown'},{item['doc_id']}",
        })
        document_id = upload["documentId"]

        def parse_ready():
            detail = client.post("/api/v1/manage/document/detail/query", {"documentId": document_id})
            if status_is(detail, PARSE_SUCCESS, "parseStatus", "parse_status"):
                return detail
            if status_is(detail, 4, "parseStatus", "parse_status"):
                raise RuntimeError(f"Parse failed for {document_id}: {get_any(detail, 'parseErrorMsg', 'parse_error_msg')}")
            return None

        detail = wait_for_condition(f"parse {document_id}", 900, poll_seconds, parse_ready)
        plan_query = client.post("/api/v1/manage/document/strategy/plan/query", {"documentId": document_id})
        plan = get_any(plan_query, "plan") or {}
        plan_id = get_any(plan, "planId", "plan_id") or get_any(detail, "currentPlanId", "current_plan_id")
        parent_steps = extract_steps(get_any(plan, "parentPipeline", "parent_pipeline"))
        child_steps = extract_steps(get_any(plan, "childPipeline", "child_pipeline"))
        if not plan_id or not parent_steps or not child_steps:
            raise RuntimeError(f"Strategy plan incomplete for {document_id}: {json.dumps(plan_query, ensure_ascii=False)[:1000]}")

        client.post("/api/v1/manage/document/strategy/confirm", {
            "documentId": document_id,
            "basePlanId": plan_id,
            "adjustNote": "EnterpriseRAG-Bench server evaluation",
            "operatorId": 1,
            "parentSteps": parent_steps,
            "childSteps": child_steps,
        })
        client.post("/api/v1/manage/document/index/build", {
            "documentId": document_id,
            "planId": plan_id,
            "operatorId": 1,
        })

        def index_ready():
            detail = client.post("/api/v1/manage/document/detail/query", {"documentId": document_id})
            if status_is(detail, INDEX_SUCCESS, "indexStatus", "index_status"):
                return detail
            if status_is(detail, 4, "indexStatus", "index_status"):
                raise RuntimeError(f"Index failed for {document_id}: {detail}")
            return None

        detail = wait_for_condition(f"index {document_id}", 1200, poll_seconds, index_ready)
        by_doc_id[item["doc_id"]] = {
            **item,
            "runtime_document_id": str(document_id),
            "parseStatus": get_any(detail, "parseStatus", "parse_status"),
            "strategyStatus": get_any(detail, "strategyStatus", "strategy_status"),
            "indexStatus": get_any(detail, "indexStatus", "index_status"),
        }
        state["items"] = list(by_doc_id.values())
        state_path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")

    log(f"Import finished: {len(by_doc_id)} documents recorded at {state_path}")


def as_list(data: Any) -> list[Any]:
    return data if isinstance(data, list) else []


def ranked_document_ids(results: list[dict[str, Any]], rank_names: tuple[str, ...],
                        *, selected_only: bool = False,
                        channel: str | None = None) -> list[str]:
    """Build a document-level ranking from chunk/channel observation rows."""
    ranked: list[tuple[float, int, str]] = []
    for index, result in enumerate(results):
        if selected_only and not (
            result.get("selected") is True or str(result.get("selected")).lower() == "true"
        ):
            continue
        result_channel = str(result.get("channelType") or result.get("channel_type") or "")
        if channel is not None and result_channel != channel:
            continue
        doc_id = result.get("documentId") or result.get("document_id")
        if doc_id is None:
            continue
        rank = None
        for name in rank_names:
            rank = as_float(result.get(name))
            if rank is not None:
                break
        if rank is not None:
            ranked.append((rank, index, str(doc_id)))
    ranked.sort(key=lambda item: (item[0], item[1]))
    return unique_in_order([doc_id for _, _, doc_id in ranked])


def observation_completed(observation: dict[str, Any]) -> bool:
    status = observation.get("answer_status")
    if status is not None:
        return str(status).upper() == "COMPLETED"
    answer = str(observation.get("answer") or "")
    transport_failure_markers = (
        "connection prematurely closed",
        "connection reset",
        "read timed out",
        "remote host terminated",
    )
    normalized = answer.lower()
    return bool(answer.strip()) and not any(marker in normalized for marker in transport_failure_markers)


def extract_exchange_id(events: Iterable[dict[str, Any]]) -> str | None:
    for event in events:
        for container in (event, event.get("metadata") if isinstance(event.get("metadata"), dict) else {}):
            value = container.get("exchangeId") or container.get("exchange_id")
            if value is not None:
                return str(value)
    return None


def build_chat_payload(question: dict[str, Any], conversation_id: str,
                       official_to_runtime: dict[str, str], text: str | None = None) -> dict[str, Any]:
    chat_mode = str(question.get("chat_mode") or "AUTO_DOCUMENT").upper()
    payload: dict[str, Any] = {
        "question": text if text is not None else question["question"],
        "conversationId": conversation_id,
        "chatMode": chat_mode,
    }
    selected_key = question.get("selected_document_key")
    if chat_mode == "DOCUMENT":
        if not selected_key:
            raise ValueError(f"{question['question_id']} uses DOCUMENT mode without selected_document_key")
        runtime_document_id = official_to_runtime.get(str(selected_key))
        if not runtime_document_id:
            raise ValueError(
                f"{question['question_id']} selected document {selected_key!r} was not imported"
            )
        payload["selectedDocumentId"] = runtime_document_id
    return payload


def replay_conversation(client: KnowHubClient, question: dict[str, Any], conversation_id: str,
                        official_to_runtime: dict[str, str], chat_timeout: int) -> int:
    """Replay preceding user turns and let KnowHub generate the corresponding assistant turns."""
    conversation = question.get("conversation") or []
    if not isinstance(conversation, list) or len(conversation) < 2:
        return 0
    replayed = 0
    for message in conversation[:-1]:
        if not isinstance(message, dict) or str(message.get("role", "")).lower() != "user":
            continue
        content = str(message.get("content") or "").strip()
        if not content:
            continue
        client.stream_chat(
            build_chat_payload(question, conversation_id, official_to_runtime, content),
            timeout=chat_timeout,
        )
        session = wait_for_chat_terminal_status(
            client,
            conversation_id,
            timeout_seconds=chat_timeout,
        )
        status = str(session.get("latestTurnStatus") or "UNKNOWN").upper()
        if status != "COMPLETED":
            error = str(session.get("latestTurnError") or status)
            raise RuntimeError(
                f"Conversation replay turn did not complete for {question['question_id']}: {error}"
            )
        replayed += 1
    return replayed


def evaluate(client: KnowHubClient, output_dir: Path, chat_timeout: int,
             components: set[str] | None = None,
             question_ids: set[str] | None = None,
             observations_file: str = "observations.jsonl",
             max_cases: int | None = None,
             rerun_completed: bool = False,
             run_config: dict[str, Any] | None = None) -> None:
    state = json.loads((output_dir / "import-state.json").read_text(encoding="utf-8"))
    runtime_to_official = {
        str(item["runtime_document_id"]): item["doc_id"]
        for item in state.get("items", [])
        if item.get("runtime_document_id")
    }
    official_to_runtime = {official: runtime for runtime, official in runtime_to_official.items()}
    gold = read_gold(output_dir)
    if components:
        gold = [item for item in gold if str(item.get("component") or "") in components]
    if question_ids:
        gold = [item for item in gold if str(item.get("question_id") or "") in question_ids]
    if max_cases is not None and max_cases > 0:
        gold = gold[:max_cases]

    observations_path = output_dir / Path(observations_file).name
    completed = set()
    if observations_path.exists():
        for line in observations_path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                existing = json.loads(line)
                if observation_completed(existing):
                    completed.add(existing["question_id"])

    with observations_path.open("a", encoding="utf-8") as handle:
        for index, question in enumerate(gold, start=1):
            if question["question_id"] in completed and not rerun_completed:
                continue
            log(f"Evaluating {index}/{len(gold)} {question['question_id']}")
            conversation_id = ""
            exchange_id = ""
            replayed_turns = 0
            answer = ""
            answer_status = "FAILED"
            answer_error = ""
            for attempt in range(1, 4):
                conversation_id = f"erb-{uuid.uuid4()}"
                try:
                    replayed_turns = replay_conversation(
                        client, question, conversation_id, official_to_runtime, chat_timeout
                    )
                    answer, events = client.stream_chat(
                        build_chat_payload(question, conversation_id, official_to_runtime),
                        timeout=chat_timeout,
                    )
                    exchange_id = extract_exchange_id(events) or ""
                    session = wait_for_chat_terminal_status(client, conversation_id)
                    exchange_id = exchange_id or str(session.get("latestExchangeId") or "")
                    answer_status = str(session.get("latestTurnStatus") or "UNKNOWN").upper()
                    answer_error = str(session.get("latestTurnErrorMessage") or "")
                except Exception as exc:  # noqa: BLE001
                    answer_status = "FAILED"
                    answer_error = str(exc)
                if answer_status == "COMPLETED":
                    break
                log(
                    f"{question['question_id']} attempt {attempt}/3 failed: "
                    f"{answer_error or answer_status}"
                )
                if attempt < 3:
                    time.sleep(attempt * 2)
            retrieval_results = []
            channel_executions = []
            route_trace = {}
            exchange_detail = {}
            if exchange_id:
                exchange_detail = client.post("/api/v1/chat/exchange/detail", {
                    "conversationId": conversation_id,
                    "exchangeId": str(exchange_id),
                })
                retrieval_results = as_list(client.post("/api/v1/chat/exchange/retrieval/results", {
                    "conversationId": conversation_id,
                    "exchangeId": str(exchange_id),
                }))
                channel_executions = as_list(client.post("/api/v1/chat/exchange/channel/executions", {
                    "conversationId": conversation_id,
                    "exchangeId": str(exchange_id),
                }))
                route_trace = fetch_route_trace(client, conversation_id, exchange_id)

            raw_retrieved_runtime_ids = []
            channel_names: set[str] = set()
            for result in retrieval_results:
                doc_id = result.get("documentId") or result.get("document_id")
                if doc_id is not None:
                    raw_retrieved_runtime_ids.append(str(doc_id))
                    channel_name = str(result.get("channelType") or result.get("channel_type") or "")
                    if channel_name:
                        channel_names.add(channel_name)
            retrieved_runtime_ids = ranked_document_ids(
                retrieval_results, ("rrfRank", "rrf_rank")
            )
            if not retrieved_runtime_ids:
                retrieved_runtime_ids = unique_in_order(raw_retrieved_runtime_ids)
            selected_runtime_ids = ranked_document_ids(
                retrieval_results, ("finalRank", "final_rank"), selected_only=True
            )
            channel_runtime_ids = {
                channel_name: ranked_document_ids(
                    retrieval_results,
                    ("channelRank", "channel_rank"),
                    channel=channel_name,
                )
                for channel_name in channel_names
            }
            raw_retrieved_official_ids = [
                runtime_to_official.get(doc_id, doc_id) for doc_id in raw_retrieved_runtime_ids
            ]
            retrieved_official_ids = [runtime_to_official.get(doc_id, doc_id) for doc_id in retrieved_runtime_ids]
            selected_official_ids = [runtime_to_official.get(doc_id, doc_id) for doc_id in selected_runtime_ids]
            channel_official_ids = {
                channel: [runtime_to_official.get(doc_id, doc_id) for doc_id in ids]
                for channel, ids in channel_runtime_ids.items()
                if channel
            }
            route_runtime_ids = extract_route_document_ids(route_trace)
            route_official_ids = [runtime_to_official.get(doc_id, doc_id) for doc_id in route_runtime_ids]
            # 实际检索范围只存在于路由阶段快照里，必须单独取出来；route_document_ids 取自
            # 路由服务的中间候选（硬上限 100），无法反映最终范围。
            route_scope = extract_route_scope(
                route_scope_snapshot(exchange_detail.get("stageTraces") or [])
            )
            observation = {
                "question_id": question["question_id"],
                "question": question["question"],
                "chat_mode": str(question.get("chat_mode") or "AUTO_DOCUMENT").upper(),
                "selected_document_key": question.get("selected_document_key"),
                "allowed_execution_modes": question.get("allowed_execution_modes") or [],
                "retrieval_expected": any(
                    mode in {"RETRIEVAL", "GRAPH_THEN_EVIDENCE"}
                    for mode in question.get("allowed_execution_modes") or []
                ),
                "replayed_user_turns": replayed_turns,
                "expected_doc_ids": question["expected_doc_ids"],
                "conversation_id": conversation_id,
                "exchange_id": exchange_id,
                "answer": answer,
                "answer_status": answer_status,
                "answer_error": answer_error,
                "raw_retrieved_runtime_document_ids": raw_retrieved_runtime_ids,
                "raw_retrieved_document_ids": raw_retrieved_official_ids,
                "retrieved_runtime_document_ids": retrieved_runtime_ids,
                "retrieved_document_ids": retrieved_official_ids,
                "selected_runtime_document_ids": selected_runtime_ids,
                "selected_document_ids": selected_official_ids,
                "channel_document_ids": channel_official_ids,
                "route_runtime_document_ids": route_runtime_ids,
                "route_document_ids": route_official_ids,
                "route_trace": route_trace,
                "channel_executions": channel_executions,
                "exchange_detail": exchange_detail,
            }
            observation.update(route_scope)
            # 按条件写入：用「键不存在」表达该记录早于本机制或配置不可读，
            # 而不是写一个空字典（那与「写了一半失败」无法区分）。
            if run_config:
                observation["run_config"] = run_config
            handle.write(json.dumps(observation, ensure_ascii=False) + "\n")
            handle.flush()

    try:
        stage_benchmarks = as_list(client.post("/api/v1/chat/stage/benchmarks", {}))
        (output_dir / "stage-benchmarks.json").write_text(
            json.dumps(stage_benchmarks, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
    except Exception as exc:  # noqa: BLE001
        log(f"Stage benchmark fetch failed: {exc}")

    report_prefix = "report" if Path(observations_file).name == "observations.jsonl" else f"report-{Path(observations_file).stem}"
    write_report(output_dir, observations_file, report_prefix)


def metric_recall(expected: list[str], retrieved: list[str], k: int) -> float:
    if not expected:
        return 0.0
    return len(set(expected) & set(retrieved[:k])) / len(set(expected))


def metric_mrr(expected: list[str], retrieved: list[str], k: int) -> float:
    expected_set = set(expected)
    for rank, doc_id in enumerate(retrieved[:k], start=1):
        if doc_id in expected_set:
            return 1.0 / rank
    return 0.0


def metric_hit(expected: list[str], retrieved: list[str], k: int) -> float:
    return 1.0 if set(expected) & set(retrieved[:k]) else 0.0


def unique_in_order(values: list[str]) -> list[str]:
    seen = OrderedDict()
    for value in values:
        if value:
            seen.setdefault(str(value), None)
    return list(seen.keys())


def duplicate_ratio(values: list[str]) -> float:
    if not values:
        return 0.0
    return 1.0 - (len(set(values)) / len(values))


def as_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def as_int(value: Any) -> int | None:
    """路由快照里的计数是字符串（"candidateDocumentCount": "100"），需要归一成 int。"""
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def fetch_route_trace(client: KnowHubClient, conversation_id: str, exchange_id: str | None) -> dict[str, Any]:
    page = client.post("/api/v1/manage/knowledge/route/trace/page/query", {
        "conversationId": conversation_id,
        "pageNo": "1",
        "pageSize": "5",
    })
    records = as_list(page.get("records"))
    if exchange_id:
        for record in records:
            if str(record.get("exchangeId") or record.get("exchange_id") or "") == str(exchange_id):
                return record
    return records[0] if records else {}


def parse_json_list(value: Any) -> list[dict[str, Any]]:
    if not value:
        return []
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            return [item for item in parsed if isinstance(item, dict)] if isinstance(parsed, list) else []
        except json.JSONDecodeError:
            return []
    return []


def extract_route_document_ids(route_trace: dict[str, Any]) -> list[str]:
    documents = parse_json_list(route_trace.get("topDocumentsJson") or route_trace.get("top_documents_json"))
    result = []
    for item in documents:
        doc_id = item.get("documentId") or item.get("document_id")
        if doc_id is not None:
            result.append(str(doc_id))
    selected = route_trace.get("selectedDocumentId") or route_trace.get("selected_document_id")
    if selected is not None:
        result.insert(0, str(selected))
    return unique_in_order(result)


def route_scope_snapshot(stage_traces: list[dict[str, Any]] | None) -> dict[str, Any]:
    """取出知识范围路由阶段的快照。

    stageTraces 里有两条 stageCode == "ROUTE" 的阶段：第一条是知识范围路由（带候选数量等
    信息），第二条是执行路径路由（只有 executionMode/navigationSummary）。因此按快照键集
    判别，而不是按 stageCode 或位置。
    """
    for stage in stage_traces or []:
        snapshot = stage.get("snapshot") if isinstance(stage, dict) else None
        if isinstance(snapshot, dict) and any(key in snapshot for key in ROUTE_SCOPE_SNAPSHOT_KEYS):
            return snapshot
    return {}


def extract_route_scope(snapshot: dict[str, Any]) -> dict[str, Any]:
    """把路由快照归一成扁平字段。

    retrieval_scope_document_count 才是实际参与检索的文档数；route_document_ids 取自路由
    服务的中间候选列表（受 ROUTE_DOCUMENT_CANDIDATE_LIMIT = 100 约束），不反映最终范围。
    """
    if not snapshot:
        return {}
    return {
        "route_candidate_document_count": as_int(snapshot.get("candidateDocumentCount")),
        "retrieval_scope_document_count": as_int(snapshot.get("retrievalScopeDocumentCount")),
        "route_status": str(snapshot.get("routeStatus") or ""),
        "route_confidence": as_float(snapshot.get("confidence")),
        "full_corpus_retrieval": bool(snapshot.get("fullCorpusRetrieval")),
        "knowledge_route_bypassed": bool(snapshot.get("knowledgeRouteBypassed")),
    }


def observation_route_scope(observation: dict[str, Any]) -> dict[str, Any]:
    """观测记录里的检索范围：扁平字段优先，缺失时回退到路由阶段快照。

    回退让 2026-09-12 之前那批只有快照、没有扁平字段的观测文件也能算出真实范围。
    """
    if (
        observation.get("retrieval_scope_document_count") is not None
        or observation.get("route_status")
    ):
        return extract_route_scope({
            "candidateDocumentCount": observation.get("route_candidate_document_count"),
            "retrievalScopeDocumentCount": observation.get("retrieval_scope_document_count"),
            "routeStatus": observation.get("route_status"),
            "confidence": observation.get("route_confidence"),
        })
    detail = observation.get("exchange_detail") or {}
    return extract_route_scope(route_scope_snapshot(detail.get("stageTraces")))


def mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def resolve_spring_placeholder(value: str) -> str:
    value = value.strip().strip("\"'")
    match = re.fullmatch(r"\$\{([^}:]+):([^}]*)}", value)
    if match:
        return os.getenv(match.group(1), match.group(2))
    match = re.fullmatch(r"\$\{([^}]+)}", value)
    if match:
        return os.getenv(match.group(1), "")
    return value


def load_admin_auth_from_yaml(path: Path) -> tuple[str | None, str | None]:
    if not path.exists():
        return None, None
    username = None
    password = None
    in_app = False
    in_admin_auth = False
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw_line.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        stripped = line.strip()
        if indent == 0:
            in_app = stripped == "app:"
            in_admin_auth = False
            continue
        if in_app and indent == 2:
            in_admin_auth = stripped == "admin-auth:"
            continue
        if in_app and in_admin_auth and indent == 4 and ":" in stripped:
            key, value = stripped.split(":", 1)
            if key.strip() == "username":
                username = resolve_spring_placeholder(value)
            elif key.strip() == "password":
                password = resolve_spring_placeholder(value)
    return username, password


def load_rag_run_config(config_path: Path | None) -> dict[str, Any]:
    """采集本次运行生效的 RAG 口径，随观测记录落盘，使报告能自证配置。

    只取白名单里的标量键（见 RAG_RUN_CONFIG_KEYS），避免把 answer-system-prompt 之类
    的巨型文本写进每条记录。任何失败（缺文件、YAML 不可用、解析报错、结构不符）都返回
    空字典而不是抛异常——评测不应因为读不到配置而中断。
    """
    if yaml is None or config_path is None:
        return {}
    path = Path(config_path)
    if not path.exists():
        return {}
    try:
        document = yaml.safe_load(path.read_text(encoding="utf-8", errors="replace"))
    except Exception:
        return {}
    if not isinstance(document, dict):
        return {}

    rag = ((document.get("app") or {}).get("chat") or {}).get("rag")
    rag = rag if isinstance(rag, dict) else {}
    config: dict[str, Any] = {}
    for yaml_key, stamp_key in RAG_RUN_CONFIG_KEYS:
        node: Any = rag
        for part in yaml_key.split("."):
            node = node.get(part) if isinstance(node, dict) else None
        if node is None or isinstance(node, (dict, list)):
            continue
        config[stamp_key] = node

    # 日志口径决定延迟读数是否可比（质量指标与日志无关）。
    mybatis = document.get("mybatis-plus")
    if isinstance(mybatis, dict):
        configuration = mybatis.get("configuration")
        if isinstance(configuration, dict) and configuration.get("log-impl"):
            config["sql_log_impl"] = str(configuration["log-impl"])

    config["source"] = str(path)
    config["captured_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
    return config


def describe_run_caliber(run_config: dict[str, Any]) -> str:
    """把口径渲染成一行人类可读文本，附在报告头部。"""
    if not run_config:
        return "unknown（未记录配置；该报告无法自证运行口径）"
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


def write_report(output_dir: Path,
                 observations_file: str = "observations.jsonl",
                 report_prefix: str = "report",
                 run_config: dict[str, Any] | None = None) -> None:
    observations_path = output_dir / Path(observations_file).name
    observations_by_id: OrderedDict[str, dict[str, Any]] = OrderedDict()
    for line in observations_path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            observation = json.loads(line)
            observations_by_id[str(observation["question_id"])] = observation
    observations = list(observations_by_id.values())
    # 观测记录里自带的口径优先——它才是这批请求实际按什么配置跑的；参数仅作回退。
    stamped_config = next(
        (obs["run_config"] for obs in observations if isinstance(obs.get("run_config"), dict)),
        None,
    )
    effective_config = stamped_config or run_config or {}
    total = len(read_gold(output_dir))
    rows = []
    for obs in observations:
        expected = [str(item) for item in obs.get("expected_doc_ids") or []]
        retrieved = unique_in_order([str(item) for item in obs.get("retrieved_document_ids") or []])
        raw_retrieved = [
            str(item)
            for item in obs.get("raw_retrieved_document_ids") or obs.get("retrieved_document_ids") or []
        ]
        unique_retrieved = unique_in_order(retrieved)
        selected = unique_in_order([str(item) for item in obs.get("selected_document_ids") or []])
        route_docs = unique_in_order([str(item) for item in obs.get("route_document_ids") or []])
        channel_docs = obs.get("channel_document_ids") or {}
        channel_executions = obs.get("channel_executions") or []
        channel_duration_sum = sum(
            duration for duration in (
                as_float(item.get("durationMs") or item.get("duration_ms"))
                for item in channel_executions
            )
            if duration is not None
        )
        vector_durations = [
            duration for duration in (
                as_float(item.get("durationMs") or item.get("duration_ms"))
                for item in channel_executions
                if str(item.get("channelType") or item.get("channel_type") or "") == "vector"
            )
            if duration is not None
        ]
        keyword_durations = [
            duration for duration in (
                as_float(item.get("durationMs") or item.get("duration_ms"))
                for item in channel_executions
                if str(item.get("channelType") or item.get("channel_type") or "") == "keyword"
            )
            if duration is not None
        ]
        scope = observation_route_scope(obs)
        rows.append({
            "question_id": obs["question_id"],
            "chat_mode": obs.get("chat_mode") or "AUTO_DOCUMENT",
            "retrieval_expected": bool(obs.get("retrieval_expected", bool(expected))),
            "recall@5": metric_recall(expected, retrieved, 5),
            "recall@10": metric_recall(expected, retrieved, 10),
            "recall@20": metric_recall(expected, retrieved, 20),
            "unique_recall@5": metric_recall(expected, unique_retrieved, 5),
            "unique_recall@10": metric_recall(expected, unique_retrieved, 10),
            "unique_recall@20": metric_recall(expected, unique_retrieved, 20),
            "final_recall@5": metric_recall(expected, selected, 5),
            "mrr@10": metric_mrr(expected, retrieved, 10),
            "route_hit@1": metric_hit(expected, route_docs, 1),
            "route_hit@3": metric_hit(expected, route_docs, 3),
            "route_hit@20": metric_hit(expected, route_docs, 20),
            "vector_recall@20": metric_recall(expected, [str(item) for item in channel_docs.get("vector", [])], 20),
            "keyword_recall@20": metric_recall(expected, [str(item) for item in channel_docs.get("keyword", [])], 20),
            "retrieved_count": len(retrieved),
            "unique_document_count": len(unique_retrieved),
            "duplicate_document_ratio": duplicate_ratio(raw_retrieved),
            "route_candidate_count": len(route_docs),
            "retrieval_scope_document_count": scope.get("retrieval_scope_document_count"),
            "route_status": scope.get("route_status") or "",
            "channel_count": len(obs.get("channel_executions") or []),
            "channel_duration_sum_ms": channel_duration_sum,
            "vector_duration_avg_ms": mean(vector_durations),
            "keyword_duration_avg_ms": mean(keyword_durations),
        })
    retrieval_rows = [row for row in rows if row["retrieval_expected"]]
    stage_benchmarks_path = output_dir / "stage-benchmarks.json"
    stage_benchmarks = []
    if stage_benchmarks_path.exists():
        stage_benchmarks = json.loads(stage_benchmarks_path.read_text(encoding="utf-8"))
    metrics = {
        "completed": len(observations),
        "total": total,
        "failed_answers": sum(
            1 for observation in observations if not observation_completed(observation)
        ),
        "retrieval_questions": len(retrieval_rows),
        "recall@5": mean([row["recall@5"] for row in retrieval_rows]),
        "recall@10": mean([row["recall@10"] for row in retrieval_rows]),
        "recall@20": mean([row["recall@20"] for row in retrieval_rows]),
        "unique_recall@5": mean([row["unique_recall@5"] for row in retrieval_rows]),
        "unique_recall@10": mean([row["unique_recall@10"] for row in retrieval_rows]),
        "unique_recall@20": mean([row["unique_recall@20"] for row in retrieval_rows]),
        "final_recall@5": mean([row["final_recall@5"] for row in retrieval_rows]),
        "mrr@10": mean([row["mrr@10"] for row in retrieval_rows]),
        "route_hit@1": mean([row["route_hit@1"] for row in retrieval_rows]),
        "route_hit@3": mean([row["route_hit@3"] for row in retrieval_rows]),
        "route_hit@20": mean([row["route_hit@20"] for row in retrieval_rows]),
        "vector_recall@20": mean([row["vector_recall@20"] for row in retrieval_rows]),
        "keyword_recall@20": mean([row["keyword_recall@20"] for row in retrieval_rows]),
        "avg_unique_document_count": mean([row["unique_document_count"] for row in retrieval_rows]),
        "avg_duplicate_document_ratio": mean([row["duplicate_document_ratio"] for row in retrieval_rows]),
        "avg_route_candidate_count": mean([row["route_candidate_count"] for row in retrieval_rows]),
        "avg_retrieval_scope_document_count": mean([
            row["retrieval_scope_document_count"]
            for row in retrieval_rows
            if row["retrieval_scope_document_count"] is not None
        ]),
        "high_confidence_route_cases": sum(1 for row in rows if row["route_status"] == "SUCCESS"),
        "avg_high_confidence_scope_document_count": mean([
            row["retrieval_scope_document_count"]
            for row in rows
            if row["route_status"] == "SUCCESS" and row["retrieval_scope_document_count"] is not None
        ]),
        "low_confidence_route_cases": sum(1 for row in rows if row["route_status"] == "LOW_CONFIDENCE"),
        "avg_low_confidence_scope_document_count": mean([
            row["retrieval_scope_document_count"]
            for row in rows
            if row["route_status"] == "LOW_CONFIDENCE" and row["retrieval_scope_document_count"] is not None
        ]),
        "avg_channel_duration_sum_ms": mean([row["channel_duration_sum_ms"] for row in retrieval_rows]),
        "avg_vector_duration_ms": mean([row["vector_duration_avg_ms"] for row in retrieval_rows]),
        "avg_keyword_duration_ms": mean([row["keyword_duration_avg_ms"] for row in retrieval_rows]),
        "empty_retrieval": sum(1 for row in retrieval_rows if row["retrieved_count"] == 0),
        "empty_channels": sum(1 for row in retrieval_rows if row["channel_count"] == 0),
        "suspected_clarification": sum(1 for row in retrieval_rows if row["retrieved_count"] == 0 and row["channel_count"] == 0),
        "stage_benchmarks": stage_benchmarks,
        "run_config": effective_config,
        "rows": rows,
    }
    safe_report_prefix = Path(report_prefix).name
    (output_dir / f"{safe_report_prefix}.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")
    md = [
        "# EnterpriseRAG-Bench KnowHub Report",
        "",
        f"- Run caliber: {describe_run_caliber(effective_config)}",
    ]
    if effective_config.get("force_retrieval_mode") is True:
        md.append(
            "- WARNING: force-retrieval-mode=true forces every query into RETRIEVAL and disables"
            " clarification, so any route-accuracy figure from this run is not comparable to"
            " production and must not be quoted as a product metric."
        )
    md.extend([
        f"- Completed: {metrics['completed']}/{metrics['total']}",
        f"- Failed answers: {metrics['failed_answers']}",
        f"- Retrieval-scored questions: {metrics['retrieval_questions']}",
        f"- Recall@5: {metrics['recall@5']:.4f}",
        f"- Recall@10: {metrics['recall@10']:.4f}",
        f"- Recall@20: {metrics['recall@20']:.4f}",
        f"- Unique Recall@10: {metrics['unique_recall@10']:.4f}",
        f"- Final Recall@5: {metrics['final_recall@5']:.4f}",
        f"- MRR@10: {metrics['mrr@10']:.4f}",
        f"- Route Hit@1: {metrics['route_hit@1']:.4f}",
        f"- Route Hit@3: {metrics['route_hit@3']:.4f}",
        f"- Route Hit@20: {metrics['route_hit@20']:.4f}",
        f"- Vector Recall@20: {metrics['vector_recall@20']:.4f}",
        f"- Keyword Recall@20: {metrics['keyword_recall@20']:.4f}",
        f"- Avg unique document count: {metrics['avg_unique_document_count']:.2f}",
        f"- Avg duplicate document ratio: {metrics['avg_duplicate_document_ratio']:.4f}",
        f"- Avg route candidate count: {metrics['avg_route_candidate_count']:.2f}",
        f"- Avg retrieval scope document count: {metrics['avg_retrieval_scope_document_count']:.2f}",
        f"- High-confidence routes (SUCCESS): {metrics['high_confidence_route_cases']}"
        f" (avg scope {metrics['avg_high_confidence_scope_document_count']:.2f})",
        f"- Low-confidence routes: {metrics['low_confidence_route_cases']}"
        f" (avg scope {metrics['avg_low_confidence_scope_document_count']:.2f})",
        f"- Avg channel duration sum ms: {metrics['avg_channel_duration_sum_ms']:.2f}",
        f"- Avg vector duration ms: {metrics['avg_vector_duration_ms']:.2f}",
        f"- Avg keyword duration ms: {metrics['avg_keyword_duration_ms']:.2f}",
        f"- Empty retrieval: {metrics['empty_retrieval']}",
        f"- Empty channels: {metrics['empty_channels']}",
        f"- Suspected clarification: {metrics['suspected_clarification']}",
        "",
    ])
    if stage_benchmarks:
        md.extend([
            "## Stage Benchmarks",
            "",
            "| Stage | Mode | P50 | P90 | P99 | Avg | Samples |",
            "|---|---:|---:|---:|---:|---:|---:|",
        ])
        for item in stage_benchmarks:
            md.append(
                f"| {item.get('stageCode') or item.get('stage_code') or ''} "
                f"| {item.get('executionMode') or item.get('execution_mode') or ''} "
                f"| {item.get('p50DurationMs') or item.get('p50_duration_ms') or ''} "
                f"| {item.get('p90DurationMs') or item.get('p90_duration_ms') or ''} "
                f"| {item.get('p99DurationMs') or item.get('p99_duration_ms') or ''} "
                f"| {item.get('avgDurationMs') or item.get('avg_duration_ms') or ''} "
                f"| {item.get('sampleCount') or item.get('sample_count') or ''} |"
            )
        md.append("")
    report_path = output_dir / f"{safe_report_prefix}.md"
    report_path.write_text("\n".join(md), encoding="utf-8")
    log(f"Report written to {report_path}")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.getenv("KNOWHUB_BASE_URL", "http://127.0.0.1:9082"))
    parser.add_argument("--username", default=os.getenv("KNOWHUB_ADMIN_USERNAME", "admin"))
    parser.add_argument("--password", default=os.getenv("KNOWHUB_ADMIN_PASSWORD"))
    parser.add_argument("--config-yaml", default="knowhub-app/src/main/resources/application.yaml")
    parser.add_argument(
        "--rag-config-yaml",
        default="",
        help="Config file to record as this run's RAG caliber; defaults to --config-yaml. "
             "Point it at evaluation/application-eval.yaml when the backend was launched with it.",
    )
    parser.add_argument("--output-dir", default="evaluation/reports/enterprise-rag-bench-250-original")
    parser.add_argument("--question-count", type=int, default=50)
    parser.add_argument("--document-count", type=int, default=250)
    parser.add_argument("--poll-seconds", type=int, default=10)
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument("--chat-timeout", type=int, default=360)
    parser.add_argument(
        "--observations-file",
        default="observations.jsonl",
        help="Observation JSONL filename inside output-dir; use a new name to preserve earlier runs.",
    )
    parser.add_argument(
        "--max-cases",
        type=int,
        default=0,
        help="Maximum cases after component filtering; zero evaluates all selected cases.",
    )
    parser.add_argument(
        "--components",
        default="",
        help="Comma-separated component names to evaluate; empty evaluates every component.",
    )
    parser.add_argument(
        "--question-ids",
        default="",
        help="Comma-separated question IDs to evaluate; empty evaluates every selected case.",
    )
    parser.add_argument(
        "--rerun-completed",
        action="store_true",
        help="Append fresh observations even when selected question IDs already completed.",
    )
    parser.add_argument("--phase", choices=["prepare", "import", "evaluate", "report", "all"], default="all")
    args = parser.parse_args(argv)

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.phase in ("prepare", "all"):
        prepare_subset(output_dir, args.question_count, args.document_count)

    # 只记录，不影响评测本身：读不到配置时返回空字典。
    run_config = load_rag_run_config(Path(args.rag_config_yaml or args.config_yaml))

    if args.phase in ("import", "evaluate", "all"):
        if not args.password:
            yaml_username, yaml_password = load_admin_auth_from_yaml(Path(args.config_yaml))
            args.username = os.getenv("KNOWHUB_ADMIN_USERNAME", yaml_username or args.username)
            args.password = yaml_password
        if not args.password:
            raise RuntimeError("Set KNOWHUB_ADMIN_PASSWORD or pass --password.")
        client = KnowHubClient(args.base_url, args.username, args.password, args.timeout)
        if args.phase in ("import", "all"):
            import_documents(client, output_dir, args.poll_seconds)
        if args.phase in ("evaluate", "all"):
            components = {item.strip() for item in args.components.split(",") if item.strip()}
            question_ids = {item.strip() for item in args.question_ids.split(",") if item.strip()}
            evaluate(
                client,
                output_dir,
                args.chat_timeout,
                components or None,
                question_ids=question_ids or None,
                observations_file=args.observations_file,
                max_cases=args.max_cases or None,
                rerun_completed=args.rerun_completed,
                run_config=run_config,
            )

    if args.phase == "report":
        report_prefix = "report" if Path(args.observations_file).name == "observations.jsonl" else f"report-{Path(args.observations_file).stem}"
        write_report(output_dir, args.observations_file, report_prefix, run_config=run_config)

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
