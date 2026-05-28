# Search intent classifier training

Pre-retrieval web search uses a **binary classifier** on **384-dim L2-normalized MiniLM embeddings**
(same family as `MiniLMEmbeddingEngine`). The head runs in Rust (`classifier_core`) via UniFFI.

## Quick start

```bash
./scripts/train_search_classifier.py          # routing datasets + small synthetic supplement
./scripts/build-classifier-bindings.sh        # needs Android NDK
```

Golden prompts are in `scripts/search_intent_train/golden_prompts.py`. Training prints per-prompt
probabilities and a **recommended threshold**; align `SearchIntentPolicy.PRE_RETRIEVAL_THRESHOLD`
in Kotlin with `RECOMMENDED_THRESHOLD` in generated `third_party/classifier_core/src/lib.rs`.

## Datasets (v4 — adaptive RAG / search gating)

Implemented in `scripts/search_intent_train/routing_datasets.py`.

| Label | Sources | Label meaning |
|-------|---------|---------------|
| **Search (1)** | **CRAG** (Meta), **SealQA** (FreshQA lineage), **QReCC** rewrites, WildChat (web-heuristic), NQ / MS MARCO | Needs external / time-sensitive facts |
| **Non-search (0)** | CRAG (KG/mock-API), SealQA `never-changing`, QReCC contextual follow-ups, **ParallelPrompt** (non-parallel organic), Glaive/Hermes tool-calling, WildChat organic, CLINC / CoQA / Dolly | In-app tools, static parametric answers, conversational follow-ups |

### Cache

CRAG is downloaded once to `scripts/.cache/crag_task_1_and_2_dev_v4.jsonl.bz2` from Meta’s GitHub
(Hugging Face `Quivr/CRAG` often fails JSON parsing). This path is gitignored.

### Not used (wrong task or gated)

- **HF `zy113/ChronoQA`** — fiction/passage QA, not temporal news RAG from the paper.
- **FreshQA CSV** — use SealQA (`vtllms/sealqa`) as proxy when CSV URL is unstable.
- **Salesforce/xlam-function-calling-60k** — gated; Glaive + Hermes cover tool negatives.

### Synthetic supplement (optional)

`--synthetic-per-class 500` (default) adds procedural templates for Enclave-specific phrases
(docgen, artifacts). Use `--no-synthetic` for routing-only training.

```bash
python3 scripts/train_search_classifier.py --cap 2000 --synthetic-per-class 500
python3 scripts/train_search_classifier.py --no-synthetic
```

Classes are balanced to the smaller side. `--cap` limits each routing bucket (CRAG, QReCC, WildChat, etc.).

### Synthetic edge-case hard negatives (auto-mixed)

`scripts/search_intent_train/synthetic_edge_cases.jsonl` (2000 rows, 1000 per class) is
auto-loaded by `routing_datasets.load_synthetic_edge_cases()` and folded into both the
search and no-search pools. It targets the classifier's known failure mode: messages that
*look* search-like but cannot be resolved without prior context (anaphora, refinement,
status updates, meta-questions about the assistant). Difficulty split per class:
30% easy / 40% medium / 30% hard.

Regenerate any time with:

```bash
python3 scripts/search_intent_train/generate_synthetic_edge_cases.py
```

Seed is fixed (`--seed 20260527`) so the file is reproducible. The generator emits the
rich JSONL schema (`conversation_context`, `target_message`, `label`, `reasoning`,
`difficulty`, `category`) — the trainer only consumes `target_message` + `label`; the
remaining fields are kept for audit and future contextual fine-tuning.

## Head options

### 1. Linear head on frozen embeddings (default export)

- Encode all text with `sentence-transformers/all-MiniLM-L6-v2`.
- Train `LogisticRegression(class_weight='balanced')`.
- Export `WEIGHTS[384]` + `BIAS` to `third_party/classifier_core/src/lib.rs`.

### 2. MLP head (`--head mlp` or `--head auto`)

- `384 → 128 → 1` ReLU MLP in PyTorch; export to Rust.
- `--head auto` picks whichever head wins on the golden set.

### 3. Full MiniLM fine-tune + ONNX (optional, not wired in app)

See `scripts/search_intent_train/finetune_minilm_onnx.py`.

## Runtime stack

```
User prompt
  → MiniLMEmbeddingEngine (ONNX utility model, 384-d)
  → classifier_core (linear or MLP head)
  → SearchIntentPolicy heuristics (safety net for docgen/artifact/tool phrases)
  → optional pre-retrieval WEB_SEARCH tool
```

After a strong routing retrain, heuristics can stay as a narrow safety net only.

## Adding production hard negatives

Add eval rows to `golden_prompts.py`; add training templates in `synthetic_data.py` if routing
corpora lack coverage; retrain and rebuild bindings.
