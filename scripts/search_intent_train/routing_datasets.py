"""
Adaptive RAG / search-gating datasets with explicit or derived labels.

Sources aligned with the user's research brief:
- Meta CRAG (static_or_dynamic, web vs KG)
- SealQA / FreshQA lineage (freshness: fast/slow/never-changing)
- QReCC (conversational search rewrites)
- ParallelPrompt (organic human prompts, non-parallel subset)
- WildChat first-turn prompts (organic, unlabeled → heuristic filter)
- Tool-calling corpora (in-app actions, not open web)
"""

from __future__ import annotations

import bz2
import json
import random
import re
import urllib.request
from collections.abc import Iterable
from pathlib import Path

from tqdm import tqdm

from search_intent_train.data_sources import _clean, _looks_like_web_lookup, _sample

REPO_ROOT = Path(__file__).resolve().parents[2]
CACHE_DIR = REPO_ROOT / "scripts" / ".cache"
CRAG_JSONL_BZ2 = CACHE_DIR / "crag_task_1_and_2_dev_v4.jsonl.bz2"
CRAG_JSONL_URL = (
    "https://github.com/facebookresearch/CRAG/raw/refs/heads/main/data/"
    "crag_task_1_and_2_dev_v4.jsonl.bz2"
)

# ---------------------------------------------------------------------------
# CRAG (Meta) — web-search gating from static_or_dynamic + popularity
# ---------------------------------------------------------------------------


def _ensure_crag_downloaded() -> Path:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    if not CRAG_JSONL_BZ2.exists():
        print(f"Downloading CRAG QA jsonl (~compressed) to {CRAG_JSONL_BZ2} ...")
        urllib.request.urlretrieve(CRAG_JSONL_URL, CRAG_JSONL_BZ2)
    return CRAG_JSONL_BZ2


def _crag_needs_web_search(row: dict) -> bool | None:
    """Return True/False for web pre-search, or None to skip ambiguous rows."""
    if row.get("answer") in ("invalid question", "i don't know"):
        return None
    if row.get("question_type") == "false_premise":
        # Still often needs retrieval to refute; treat as search.
        return True
    popularity = row.get("popularity")
    if popularity not in (None, ""):
        # KG/mock-API questions — not Tavily-style open web for our gate.
        return False
    dynamism = (row.get("static_or_dynamic") or "").lower()
    if dynamism in ("fast-changing", "real-time", "slow-changing"):
        return True
    if dynamism == "static":
        # Factual web QA but may not need *live* crawl; still RAG-positive for training.
        return True
    return None


_crag_cache: dict[int, tuple[list[str], list[str]]] = {}


def load_crag_labeled(rng: random.Random, cap: int) -> tuple[list[str], list[str]]:
    if cap in _crag_cache:
        return _crag_cache[cap]
    path = _ensure_crag_downloaded()
    search: list[str] = []
    no_search: list[str] = []
    print("Parsing CRAG jsonl (query + labels only)...")
    with bz2.open(path, "rt", encoding="utf-8") as handle:
        for line in tqdm(handle, desc="CRAG"):
            if len(search) >= cap and len(no_search) >= cap:
                break
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            query = _clean(row.get("query", ""), min_len=15)
            if not query:
                continue
            label = _crag_needs_web_search(row)
            if label is True and len(search) < cap:
                search.append(query)
            elif label is False and len(no_search) < cap:
                no_search.append(query)
    _crag_cache[cap] = (search, no_search)
    return search, no_search


# ---------------------------------------------------------------------------
# SealQA (FreshQA lineage) — freshness labels
# ---------------------------------------------------------------------------


def load_sealqa_labeled(rng: random.Random) -> tuple[list[str], list[str]]:
    from datasets import load_dataset

    search: list[str] = []
    no_search: list[str] = []
    for config in ("seal_0", "seal_hard", "longseal"):
        try:
            ds = load_dataset("vtllms/sealqa", config, split="test")
        except Exception as exc:
            print(f"  Warning: SealQA {config} unavailable: {exc}")
            continue
        for row in ds:
            q = _clean(row.get("question", ""), min_len=15)
            if not q:
                continue
            freshness = (row.get("freshness") or "").lower()
            if freshness in ("fast-changing", "slow-changing"):
                search.append(q)
            elif freshness == "never-changing":
                no_search.append(q)
    search = list(dict.fromkeys(search))
    no_search = list(dict.fromkeys(no_search))
    print(f"  SealQA/FreshQA lineage: {len(search)} search, {len(no_search)} non-search")
    return search, no_search


# ---------------------------------------------------------------------------
# QReCC — Rewrite = context-independent search query (positive)
# Question with context = follow-up (often negative for *verbatim* web query)
# ---------------------------------------------------------------------------


_qrecc_cache: dict[int, tuple[list[str], list[str]]] = {}


def load_qrecc_labeled(rng: random.Random, cap: int) -> tuple[list[str], list[str]]:
    if cap in _qrecc_cache:
        return _qrecc_cache[cap]
    from datasets import load_dataset

    search: list[str] = []
    no_search: list[str] = []
    try:
        ds = load_dataset("svakulenk0/qrecc", split="train", streaming=True)
        for row in tqdm(ds, desc="QReCC"):
            rewrite = _clean(row.get("Rewrite", ""), min_len=10)
            question = _clean(row.get("Question", ""), min_len=8)
            context = row.get("Context") or []
            if rewrite and len(search) < cap:
                search.append(rewrite)
            if question and len(context) > 0 and len(no_search) < cap:
                # Conversational follow-up — usually not a standalone web query.
                if question.lower() != (rewrite or "").lower():
                    no_search.append(question)
            if len(search) >= cap and len(no_search) >= cap:
                break
    except Exception as exc:
        print(f"  Warning: QReCC unavailable: {exc}")
    result = (
        _sample(list(dict.fromkeys(search)), cap, rng),
        _sample(list(dict.fromkeys(no_search)), cap, rng),
    )
    _qrecc_cache[cap] = result
    return result


# ---------------------------------------------------------------------------
# ParallelPrompt — real LMSYS/WildChat prompts (non-parallel → organic negatives)
# ---------------------------------------------------------------------------


def load_parallelprompt_organic(rng: random.Random, cap: int) -> list[str]:
    from datasets import load_dataset

    out: list[str] = []
    try:
        ds = load_dataset("forgelab/ParallelPrompt", split="train", streaming=True)
        for row in tqdm(ds, desc="ParallelPrompt"):
            if row.get("parallelizable") is True:
                continue
            prompt = _clean(row.get("prompt", ""), min_len=12)
            if prompt and not _looks_like_web_lookup(prompt):
                out.append(prompt)
            if len(out) >= cap:
                break
    except Exception as exc:
        print(f"  Warning: ParallelPrompt unavailable: {exc}")
    return _sample(list(dict.fromkeys(out)), cap, rng)


# ---------------------------------------------------------------------------
# WildChat — organic first-turn user prompts (heuristic negatives)
# ---------------------------------------------------------------------------


def load_wildchat_organic_negatives(rng: random.Random, cap: int) -> list[str]:
    """Real user prompts that are not obvious web lookups or tool routing."""
    from datasets import load_dataset

    out: list[str] = []
    try:
        ds = load_dataset(
            "PJMixers-Dev/allenai_WildChat-1M-prompts",
            split="train",
            streaming=True,
        )
        for row in tqdm(ds, desc="WildChat organic"):
            text = row.get("prompt") or row.get("text") or ""
            if isinstance(text, list):
                text = text[0] if text else ""
            prompt = _clean(str(text), min_len=12)
            if not prompt:
                continue
            if _looks_like_web_lookup(prompt) or _looks_like_tool_or_artifact(prompt):
                continue
            out.append(prompt)
            if len(out) >= cap * 3:
                break
    except Exception as exc:
        print(f"  Warning: WildChat prompts unavailable: {exc}")
    return _sample(list(dict.fromkeys(out)), cap, rng)


def load_wildchat_search_heuristic(rng: random.Random, cap: int) -> list[str]:
    from datasets import load_dataset

    out: list[str] = []
    try:
        ds = load_dataset(
            "PJMixers-Dev/allenai_WildChat-1M-prompts",
            split="train",
            streaming=True,
        )
        for row in tqdm(ds, desc="WildChat search-heuristic"):
            text = row.get("prompt") or row.get("text") or ""
            if isinstance(text, list):
                text = text[0] if text else ""
            prompt = _clean(str(text), min_len=20)
            if prompt and _looks_like_web_lookup(prompt) and not _looks_like_tool_or_artifact(prompt):
                out.append(prompt)
            if len(out) >= cap:
                break
    except Exception as exc:
        print(f"  Warning: WildChat search pool unavailable: {exc}")
    return _sample(list(dict.fromkeys(out)), cap, rng)


# ---------------------------------------------------------------------------
# Tool-calling (negatives for open-web pre-search)
# ---------------------------------------------------------------------------


def load_tool_calling_negatives(rng: random.Random, cap: int) -> list[str]:
    from datasets import load_dataset

    from search_intent_train.data_sources import _extract_glaive_user_turns

    out: list[str] = []
    try:
        ds = load_dataset("glaiveai/glaive-function-calling-v2", split="train", streaming=True)
        added = 0
        for row in tqdm(ds, desc="Glaive tool-calling"):
            chat = row.get("chat") or ""
            for user_line in _extract_glaive_user_turns(chat):
                u = _clean(user_line, min_len=10)
                if u:
                    out.append(u)
                    added += 1
            if added >= cap:
                break
    except Exception as exc:
        print(f"  Warning: Glaive unavailable: {exc}")

    try:
        ds = load_dataset("NousResearch/hermes-function-calling-v1", split="train", streaming=True)
        added = 0
        for row in tqdm(ds, desc="Hermes tool-calling"):
            raw = row.get("conversations") or row.get("conversation") or ""
            if isinstance(raw, str):
                for user_line in _extract_glaive_user_turns(raw):
                    u = _clean(user_line, min_len=10)
                    if u:
                        out.append(u)
                        added += 1
            if added >= cap // 2:
                break
    except Exception as exc:
        print(f"  Warning: Hermes unavailable: {exc}")

    return _sample(list(dict.fromkeys(out)), cap, rng)


_TOOL_ARTIFACT_RE = re.compile(
    r"\b(artifact|docgen|document creation tool|use the .+ tool|export (this|it)|"
    r"create (me )?(a )?(document|pdf|report)|append .+ artifact)\b",
    re.IGNORECASE,
)


def _looks_like_tool_or_artifact(text: str) -> bool:
    return bool(_TOOL_ARTIFACT_RE.search(text))


# ---------------------------------------------------------------------------
# Public API: assemble routing-aware training corpora
# ---------------------------------------------------------------------------


def load_routing_search_samples(rng: random.Random, cap_per_source: int) -> list[str]:
    parts: list[str] = []

    crag_search, _ = load_crag_labeled(rng, cap_per_source)
    parts.extend(crag_search)

    seal_search, _ = load_sealqa_labeled(rng)
    parts.extend(seal_search)

    qrecc_search, _ = load_qrecc_labeled(rng, cap_per_source)
    parts.extend(qrecc_search)

    parts.extend(load_wildchat_search_heuristic(rng, cap_per_source // 2))

    # Legacy IR-style queries (NQ, MS MARCO) — still useful positives
    from search_intent_train.data_sources import load_search_samples

    parts.extend(load_search_samples(rng, cap=max(cap_per_source // 4, 400)))

    return list(dict.fromkeys(parts))


def load_routing_no_search_samples(rng: random.Random, cap_per_source: int) -> list[str]:
    parts: list[str] = []

    _, crag_no = load_crag_labeled(rng, cap_per_source)
    parts.extend(crag_no)

    _, seal_no = load_sealqa_labeled(rng)
    parts.extend(seal_no)

    _, qrecc_no = load_qrecc_labeled(rng, cap_per_source)
    parts.extend(qrecc_no)

    parts.extend(load_parallelprompt_organic(rng, cap_per_source))
    parts.extend(load_tool_calling_negatives(rng, cap_per_source))
    parts.extend(load_wildchat_organic_negatives(rng, cap_per_source))

    from search_intent_train.data_sources import load_no_search_samples

    parts.extend(load_no_search_samples(rng, cap=max(cap_per_source // 4, 400)))

    return list(dict.fromkeys(parts))
