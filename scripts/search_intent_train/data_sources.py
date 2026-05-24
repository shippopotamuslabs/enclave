"""Load and label text samples for search-intent training."""

from __future__ import annotations

import random
import re
from collections.abc import Iterable

from tqdm import tqdm

# Per-source caps keep download/encode time bounded while diversifying signal.
DEFAULT_CAP = 2_500

def _clean(text: str, min_len: int = 12, max_len: int = 512) -> str | None:
    t = " ".join(text.split())
    if len(t) < min_len or len(t) > max_len:
        return None
    return t


def _sample(items: list[str], cap: int, rng: random.Random) -> list[str]:
    if len(items) <= cap:
        return items
    return rng.sample(items, cap)


def load_search_samples(rng: random.Random, cap: int = DEFAULT_CAP) -> list[str]:
    from datasets import load_dataset

    out: list[str] = []

    # Google Natural Questions (real search questions)
    try:
        nq = load_dataset("google-research-datasets/nq_open", split="train", streaming=True)
        for item in tqdm(nq, desc="NQ Open"):
            q = _clean(item.get("question", ""), min_len=20)
            if q:
                out.append(q)
            if len(out) >= cap:
                break
    except Exception as e:
        print(f"  Warning: NQ Open unavailable: {e}")

    # ELI5-style questions (try multiple dataset ids; HF scripts API changes often)
    for eli5_id in ("eli5", "eli5_category"):
        if len(out) >= cap:
            break
        try:
            eli5 = load_dataset(eli5_id, split="train", streaming=True, trust_remote_code=True)
            added = 0
            for item in tqdm(eli5, desc=f"ELI5 ({eli5_id})"):
                title = _clean(
                    item.get("title", "") or item.get("question", "") or "",
                    min_len=25,
                )
                if title:
                    out.append(title)
                    added += 1
                if added >= cap // 2:
                    break
        except Exception as e:
            print(f"  Warning: ELI5 ({eli5_id}) unavailable: {e}")

    # MS MARCO v1.1 queries (Bing-style search)
    try:
        marco = load_dataset("microsoft/ms_marco", "v1.1", split="train", streaming=True)
        added = 0
        for item in tqdm(marco, desc="MS MARCO"):
            q = _clean(item.get("query", ""), min_len=15)
            if q:
                out.append(q)
                added += 1
            if added >= cap:
                break
    except Exception as e:
        print(f"  Warning: MS MARCO unavailable: {e}")

    # BeIR MS MARCO query split (fallback)
    if len(out) < cap // 2:
        try:
            beir_q = load_dataset("BeIR/msmarco", "queries", split="train", streaming=True)
            added = 0
            for item in tqdm(beir_q, desc="BeIR MS MARCO queries"):
                text = _clean(item.get("text", "") or item.get("title", ""), min_len=15)
                if text:
                    out.append(text)
                    added += 1
                if added >= cap:
                    break
        except Exception as e:
            print(f"  Warning: BeIR MS MARCO unavailable: {e}")

    return _sample(list(dict.fromkeys(out)), cap, rng)


def load_no_search_samples(rng: random.Random, cap: int = DEFAULT_CAP) -> list[str]:
    from datasets import load_dataset

    out: list[str] = []

    # CLINC150 — general assistant intents (not web lookup)
    try:
        clinc = load_dataset("DeepPavlov/clinc150", split="train")
        for row in clinc:
            u = _clean(row.get("utterance", ""))
            if u:
                out.append(u)
            if len(out) >= cap:
                break
    except Exception as e:
        print(f"  Warning: CLINC150 unavailable: {e}")

    # Glaive function-calling — user wants a tool, not the open web
    try:
        glaive = load_dataset("glaiveai/glaive-function-calling-v2", split="train", streaming=True)
        added = 0
        for row in tqdm(glaive, desc="Glaive tool-calling"):
            chat = row.get("chat") or row.get("question") or ""
            for user_line in _extract_glaive_user_turns(chat):
                u = _clean(user_line, min_len=10)
                if u:
                    out.append(u)
                    added += 1
            if added >= cap // 3:
                break
    except Exception as e:
        print(f"  Warning: Glaive unavailable: {e}")

    # Hermes / Nous function-calling (open)
    for ds_name, field in (
        ("NousResearch/hermes-function-calling-v1", "conversation"),
        ("intertwine/self-instruct-function-calling-v1", "instruction"),
    ):
        try:
            ds = load_dataset(ds_name, split="train", streaming=True)
            added = 0
            for row in tqdm(ds, desc=ds_name.split("/")[-1]):
                raw = row.get(field) or row.get("query") or row.get("instruction") or ""
                if isinstance(raw, list):
                    for part in raw:
                        u = _clean(str(part), min_len=10)
                        if u:
                            out.append(u)
                            added += 1
                else:
                    for user_line in _extract_glaive_user_turns(str(raw)):
                        u = _clean(user_line, min_len=10)
                        if u:
                            out.append(u)
                            added += 1
                    u = _clean(str(raw), min_len=10)
                    if u and added == 0:
                        out.append(u)
                        added += 1
                if added >= cap // 4:
                    break
        except Exception as e:
            print(f"  Warning: {ds_name} unavailable: {e}")

    # CoQA — question answerable from provided passage (no live web)
    try:
        coqa = load_dataset("stanfordnlp/coqa", split="train")
        added = 0
        for row in tqdm(coqa, desc="CoQA"):
            questions = row.get("questions", {})
            q_texts = questions.get("input_text", []) if isinstance(questions, dict) else []
            for q in q_texts:
                u = _clean(q, min_len=8)
                if u:
                    out.append(u)
                    added += 1
            if added >= cap // 3:
                break
    except Exception as e:
        print(f"  Warning: CoQA unavailable: {e}")

    # QuAC — dialogue QA in context (cap iterations; HF script can stall)
    try:
        quac = load_dataset("quac", split="train", streaming=True)
        added = 0
        max_scan = max(cap * 20, 5_000)
        for i, row in enumerate(tqdm(quac, desc="QuAC", total=min(max_scan, cap // 3))):
            if i >= max_scan:
                break
            q = _clean(row.get("question", ""), min_len=8)
            if q:
                out.append(q)
                added += 1
            if added >= cap // 3:
                break
    except Exception as e:
        print(f"  Warning: QuAC unavailable: {e}")

    # Dolly — general instructions
    try:
        dolly = load_dataset("databricks/databricks-dolly-15k", split="train")
        added = 0
        for row in dolly:
            ins = _clean(row.get("instruction", ""), min_len=15)
            if ins and not _looks_like_web_lookup(ins):
                out.append(ins)
                added += 1
            if added >= cap // 4:
                break
    except Exception as e:
        print(f"  Warning: Dolly unavailable: {e}")

    return _sample(list(dict.fromkeys(out)), cap, rng)


def _looks_like_web_lookup(text: str) -> bool:
    lowered = text.lower()
    markers = (
        "current",
        "latest",
        "today",
        "right now",
        "2024",
        "2025",
        "look up",
        "search for",
        "find me",
        "what happened",
        "how much does",
    )
    return any(m in lowered for m in markers)


def _extract_glaive_user_turns(chat: str) -> Iterable[str]:
    if not chat:
        return []
    # Format: USER: ... ASSISTANT: ...
    parts = re.split(r"\bUSER:\s*", chat, flags=re.IGNORECASE)
    for part in parts[1:]:
        user = re.split(r"\bASSISTANT:\s*", part, maxsplit=1, flags=re.IGNORECASE)[0]
        yield user.strip()
