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
SYNTHETIC_EDGE_CASES_JSONL = (
    REPO_ROOT / "scripts" / "search_intent_train" / "synthetic_edge_cases.jsonl"
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
# New Training Data Sources (Class 0 - Non-Search)
# ---------------------------------------------------------------------------


def load_oasst1_refinement(rng: random.Random, cap: int) -> list[str]:
    from datasets import load_dataset
    from search_intent_train.synthetic_data import is_iterative_refinement
    
    out: list[str] = []
    try:
        ds = load_dataset("OpenAssistant/oasst1", split="train")
        msg_map = {}
        tree_messages = {}
        for row in ds:
            m_id = row["message_id"]
            t_id = row["message_tree_id"]
            p_id = row["parent_id"]
            role = row["role"]
            text = row["text"]
            rank = row["rank"]
            lang = row.get("lang", "en")
            
            if lang != "en":
                continue
                
            msg = {
                "message_id": m_id,
                "message_tree_id": t_id,
                "parent_id": p_id,
                "role": role,
                "text": text,
                "rank": rank,
            }
            msg_map[m_id] = msg
            if t_id not in tree_messages:
                tree_messages[t_id] = []
            tree_messages[t_id].append(msg)
            
        depth_map = {}
        def get_depth(m_id):
            if m_id not in msg_map:
                return -1
            if m_id in depth_map:
                return depth_map[m_id]
            p_id = msg_map[m_id]["parent_id"]
            if p_id is None:
                depth_map[m_id] = 0
                return 0
            d = get_depth(p_id)
            if d == -1:
                depth_map[m_id] = 0
            else:
                depth_map[m_id] = d + 1
            return depth_map[m_id]

        for m_id in msg_map:
            get_depth(m_id)

        tree_max_depth = {}
        for t_id, msgs in tree_messages.items():
            max_d = 0
            for msg in msgs:
                d = depth_map.get(msg["message_id"], 0)
                if d > max_d:
                    max_d = d
            tree_max_depth[t_id] = max_d

        for m_id, msg in msg_map.items():
            if msg["role"] != "prompter":
                continue
            t_id = msg["message_tree_id"]
            if tree_max_depth.get(t_id, 0) <= 3:
                continue
            depth = depth_map.get(m_id, 0)
            if depth < 2:
                continue
            text = msg["text"]
            if not is_iterative_refinement(text):
                continue
            
            p_id = msg["parent_id"]
            if p_id in msg_map:
                parent_msg = msg_map[p_id]
                parent_rank = parent_msg.get("rank")
                if parent_rank is not None and parent_rank >= 0:
                    cleaned = _clean(text, min_len=10)
                    if cleaned:
                        out.append(cleaned)
                        
    except Exception as exc:
        print(f"  Warning: OASST1 loading failed, using synthetic fallback: {exc}")
        
    fallback_oasst = [
        "Good suggestion, I'll make those changes to the config flow.",
        "Ok, I just updated things with the ellipses idea.",
        "How does this look now? Is it clean enough?",
        "That worked. What about the other controller?",
        "I see what you mean. Here's what I changed: removed the unused import",
        "Much cleaner now. The adapter is updated.",
        "This is exactly right. You've iterated from v1 to v2",
        "Good catch — that one is genuinely confusing because it looks like it should be meaningful",
        "Are these variables noise or actually serving a purpose?",
        "What would make this better?",
        "Is this clean enough?",
        "How do you even know what tool-schema-0 is?"
    ]
    while len(out) < cap:
        out.extend(fallback_oasst)
        
    return _sample(list(dict.fromkeys(out)), cap, rng)


def load_lmsys_refinement(rng: random.Random, cap: int) -> list[str]:
    from datasets import load_dataset
    from search_intent_train.synthetic_data import is_iterative_refinement

    out: list[str] = []
    try:
        ds = load_dataset("HuggingFaceH4/ultrachat_200k", split="test_sft", streaming=True)
        
        tech_keywords = {
            "code", "programming", "developer", "compile", "refactor", "class", "function",
            "python", "javascript", "typescript", "kotlin", "rust", "java", "c++", "html", "css",
            "sql", "git", "bash", "linux", "compiler", "debugging", "regex", "api", "database"
        }
        
        added = 0
        for row in tqdm(ds, desc="LMSYS (UltraChat)"):
            messages = row.get("messages", [])
            if len(messages) <= 4:
                continue
                
            is_technical = False
            for msg in messages:
                content = str(msg.get("content", "")).lower()
                if "```" in content or any(kw in content for kw in tech_keywords):
                    is_technical = True
                    break
                    
            if not is_technical:
                continue
                
            for i in range(2, len(messages)):
                msg = messages[i]
                if msg.get("role") == "user":
                    content = msg.get("content", "")
                    if content and is_iterative_refinement(content):
                        cleaned = _clean(content, min_len=10)
                        if cleaned:
                            out.append(cleaned)
                            added += 1
            if len(out) >= cap * 2:
                break
    except Exception as exc:
        print(f"  Warning: LMSYS loading failed, using synthetic fallback: {exc}")
        
    fallback_lmsys = [
        "Good suggestion, I'll make those changes to the adapter class.",
        "Ok, I just updated things with the ellipses idea and deduplication.",
        "How does this look now? Is the schema mapping correct?",
        "That worked. What about the controller state machine?",
        "I see what you mean. Here's what I changed in the logic.",
        "Much cleaner now. The classifier threshold is updated to 0.78.",
        "This is exactly right. You've iterated from the initial layout to the optimized version.",
        "Good catch — that one is genuinely confusing because it looks like it should be meaningful.",
        "Are these imports noise or actually serving a purpose?",
        "What would make this better? Should I use a flow or live coroutine?",
        "Is this clean enough, or should we decouple the DB persistence?",
        "How do you even know what tool-schema-0 and tool-schema-1 are?",
        "I added some extra logging to help me troubleshoot the thread issue.",
        "The tools are working perfectly now after resolving the missing package.",
        "I actually have 0 user data and this app uses zero knowledge encryption anyway.",
        "Yeah honestly this is the next thing I want to work on."
    ]
    while len(out) < cap:
        out.extend(fallback_lmsys)
        
    return _sample(list(dict.fromkeys(out)), cap, rng)


def load_sharegpt_refinement(rng: random.Random, cap: int) -> list[str]:
    from datasets import load_dataset
    from search_intent_train.synthetic_data import is_iterative_refinement

    out: list[str] = []
    try:
        ds = load_dataset("Aeala/ShareGPT_Vicuna_unfiltered", split="train", streaming=True)
        
        tech_keywords = {
            "code", "programming", "developer", "compile", "refactor", "class", "function",
            "python", "javascript", "typescript", "kotlin", "rust", "java", "c++", "html", "css",
            "git", "debugging"
        }
        
        added = 0
        for row in tqdm(ds, desc="ShareGPT"):
            conversations = row.get("conversations", [])
            if len(conversations) <= 4:
                continue
                
            is_technical = False
            for msg in conversations:
                val = str(msg.get("value", "")).lower()
                if "```" in val or any(kw in val for kw in tech_keywords):
                    is_technical = True
                    break
                    
            if not is_technical:
                continue
                
            for i in range(2, len(conversations)):
                msg = conversations[i]
                if msg.get("from") in ("human", "user"):
                    value = msg.get("value", "")
                    if value and is_iterative_refinement(value):
                        cleaned = _clean(value, min_len=10)
                        if cleaned:
                            out.append(cleaned)
                            added += 1
            if len(out) >= cap * 2:
                break
    except Exception as exc:
        print(f"  Warning: ShareGPT loading failed, using synthetic fallback: {exc}")
        
    fallback_sharegpt = [
        "Can you write a unit test for the function I just pasted?",
        "Debug this script — it throws a KeyError on line 42.",
        "That's not what I meant — let me rephrase.",
        "No, I mean use the tool instead.",
        "Use the memory repository to handle this, not a web search.",
        "Create a document that outlines the plan we just discussed.",
        "Generate a PDF report from this chat using the artifact flow.",
        "Export our conversation as a markdown file.",
        "Append this section to the artifact we are building.",
        "Thanks, that makes sense now. I've updated the script.",
        "Good call, that worked perfectly."
    ]
    while len(out) < cap:
        out.extend(fallback_sharegpt)
        
    return _sample(list(dict.fromkeys(out)), cap, rng)


def load_ubuntu_refinement(rng: random.Random, cap: int) -> list[str]:
    from datasets import load_dataset

    out: list[str] = []
    try:
        ds = load_dataset("sedthh/ubuntu_dialogue_qa", split="train", streaming=True)
        
        cmd_keywords = ["sudo", "apt", "git", "cd", "ls", "grep", "chmod", "chown", "mkdir", "rm", "cp", "mv", "install", "config"]
        result_keywords = ["worked", "fixed", "thanks", "done", "okay", "ok", "yes", "yeah", "now it says", "error", "no luck", "compiled", "running"]
        
        added = 0
        for row in tqdm(ds, desc="Ubuntu Dialogs"):
            question = row.get("user_question", "")
            response = row.get("responder_response", "")
            
            has_cmd = any(kw in question.lower() for kw in cmd_keywords) or any(kw in response.lower() for kw in cmd_keywords)
            has_result = any(kw in question.lower() for kw in result_keywords) or any(kw in response.lower() for kw in result_keywords)
            
            if has_cmd and has_result:
                cleaned = _clean(question, min_len=8)
                if cleaned:
                    out.append(cleaned)
                    added += 1
            if len(out) >= cap:
                break
    except Exception as exc:
        print(f"  Warning: Ubuntu Dialogue Corpus loading failed, using synthetic fallback: {exc}")
        
    fallback_ubuntu = [
        "I ran `sudo apt update` and that worked, thanks!",
        "Ok I just did that. now it says command not found",
        "I got error: permission denied when running git clone",
        "Fixed it by updating python and running pip install -r requirements.txt",
        "It finished with exit code 0. Looks good, dependencies installed.",
        "Still getting the same compile error after running cargo build",
        "Done, the server is running now on port 8000",
        "Ok, I just executed chmod +x scripts/train_search_classifier.py",
        "Confirmed, the build succeeded on gradle assembleDebug",
        "Yep, that resolved the library mismatch.",
        "I checked cat /var/log/syslog and it was empty."
    ]
    while len(out) < cap:
        out.extend(fallback_ubuntu)
        
    return _sample(list(dict.fromkeys(out)), cap, rng)


def load_github_pr_comments(rng: random.Random, cap: int) -> list[str]:
    import urllib.request
    import json
    from search_intent_train.synthetic_data import is_iterative_refinement
    
    out: list[str] = []
    
    repos = [
        "tensorflow/tensorflow",
        "pytorch/pytorch",
        "rust-lang/rust",
        "flutter/flutter",
        "google/jax",
        "kotlin/kotlin"
    ]
    
    for repo in repos:
        if len(out) >= cap:
            break
        try:
            url = f"https://api.github.com/repos/{repo}/pulls/comments?per_page=100"
            req = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Accept": "application/vnd.github.v3+json"
                }
            )
            with urllib.request.urlopen(req, timeout=5) as resp:
                comments = json.loads(resp.read().decode("utf-8"))
                for comment in comments:
                    body = comment.get("body", "")
                    in_reply = comment.get("in_reply_to_id") is not None
                    
                    if body and (in_reply or is_iterative_refinement(body)):
                        cleaned = _clean(body, min_len=10)
                        if cleaned:
                            out.append(cleaned)
        except Exception as exc:
            print(f"  Warning: Failed to fetch GitHub comments for {repo}: {exc}")
            
    fallback_comments = [
        "Good catch, I've updated the test case to cover this boundary condition.",
        "Thanks for the feedback! I added the null checks and simplified the logic.",
        "I'll make that change now. Should be updated in the next commit.",
        "Much cleaner. I removed the redundant check and squashed the commits.",
        "Done. I've updated the README as well to reflect the new API parameters.",
        "That worked, now all unit tests are passing locally.",
        "Fixed in the latest revision. Thanks for pointing it out!",
        "Yes, that is correct. I updated it to use the new adapter interface.",
        "Good call, I changed the threshold value to 0.78 as suggested.",
        "Updated the schema definitions. Please let me know how it looks now.",
        "I see what you mean. Here is what I changed in the config handler.",
        "Ah, good catch. The reference here was indeed serving no purpose. Removed it.",
        "Is this clean enough, or should I abstract the database mapping further?",
        "Okay, I just updated things with the deduplication and ellipses idea.",
        "I added some extra logging to help me troubleshoot that thread issue.",
        "The tools are working perfectly now after resolving the missing package.",
        "Can you verify the benchmark run results? I've updated the PR branch.",
        "Thanks! Applied the suggestions and formatted the code using ktlint.",
        "Done. It now uses the memory repository instead of direct DB access.",
        "Perfect suggestion, that simplifies the controller state machine a lot.",
        "I'll make those changes. Let me know if we need to restructure the test suite too.",
        "Updated. Now it properly raises a ValueError on invalid shapes.",
        "I just pushed the fix for the CI build error. It was a mismatch in target API.",
        "Looks good, I will merge once the checks complete.",
        "That's exactly right. I updated the serialization helper to handle nested maps.",
        "Ok, fixed the warnings from the static analyzer."
    ]
    
    if len(out) < cap:
        out.extend(fallback_comments)
        
    return _sample(list(dict.fromkeys(out)), cap, rng)


# ---------------------------------------------------------------------------
# Synthetic edge-case dataset (anaphoric / refinement / meta hard negatives)
#
# JSONL produced by scripts/search_intent_train/generate_synthetic_edge_cases.py
# Each row has at least: target_message (str), label (0|1). Optional richer
# fields (conversation_context, reasoning, difficulty, category) are kept for
# audit and future contextual fine-tuning, but not consumed here — the trainer
# only embeds the standalone target string.
# ---------------------------------------------------------------------------


def load_synthetic_edge_cases() -> tuple[list[str], list[str]]:
    """Return (search_strings, no_search_strings) from the synthetic edge JSONL.

    Returns empty lists if the file is missing — train.py will continue with the
    rest of the routing corpora.
    """
    if not SYNTHETIC_EDGE_CASES_JSONL.exists():
        print(
            f"  Note: {SYNTHETIC_EDGE_CASES_JSONL.name} not found. "
            "Run scripts/search_intent_train/generate_synthetic_edge_cases.py to create it.",
        )
        return [], []

    search: list[str] = []
    no_search: list[str] = []
    with SYNTHETIC_EDGE_CASES_JSONL.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            target = row.get("target_message", "")
            label = row.get("label")
            # Allow short fragments like "Mhm." or "Yes." — these are intentional
            # hard negatives for the confirmation_querylike bucket.
            cleaned = _clean(target, min_len=3)
            if not cleaned:
                continue
            if label == 1:
                search.append(cleaned)
            elif label == 0:
                no_search.append(cleaned)
    print(
        f"  Synthetic edge cases: {len(search)} search, {len(no_search)} non-search "
        f"({SYNTHETIC_EDGE_CASES_JSONL.name})"
    )
    return search, no_search


# ---------------------------------------------------------------------------
# Public API: assemble routing-aware training corpora
# ---------------------------------------------------------------------------


_synthetic_edge_cache: tuple[list[str], list[str]] | None = None


def _get_synthetic_edge_cases() -> tuple[list[str], list[str]]:
    global _synthetic_edge_cache
    if _synthetic_edge_cache is None:
        _synthetic_edge_cache = load_synthetic_edge_cases()
    return _synthetic_edge_cache


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

    # Synthetic edge-case positives (concrete entities, time-sensitive, etc.)
    synth_search, _ = _get_synthetic_edge_cases()
    parts.extend(synth_search)

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

    # New training data sources (Class 0 - Non-Search)
    parts.extend(load_oasst1_refinement(rng, cap_per_source))
    parts.extend(load_lmsys_refinement(rng, cap_per_source))
    parts.extend(load_sharegpt_refinement(rng, cap_per_source))
    parts.extend(load_ubuntu_refinement(rng, cap_per_source))
    parts.extend(load_github_pr_comments(rng, cap_per_source))

    from search_intent_train.data_sources import load_no_search_samples

    parts.extend(load_no_search_samples(rng, cap=max(cap_per_source // 4, 400)))

    # Synthetic edge-case hard negatives (anaphoric, refinement, meta, ...)
    _, synth_no = _get_synthetic_edge_cases()
    parts.extend(synth_no)

    return list(dict.fromkeys(parts))
