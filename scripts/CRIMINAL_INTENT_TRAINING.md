# Criminal / illegal intent classifier training

On-device prompt moderation uses a **multi-label** classifier on
`sentence-transformers/all-MiniLM-L6-v2`, exported to **INT8 ONNX** and run by
`OnnxCriminalIntentClassifier` (module `feature/inference`). It flags procedural
requests for illegal activity across seven labels (see `LABEL_NAMES`).

## Quick start

```bash
python3.12 -m venv scripts/.venv_classifier
scripts/.venv_classifier/bin/pip install --upgrade pip
scripts/.venv_classifier/bin/pip install -r scripts/requirements-criminal-intent.txt

# Train + export (downloads datasets + base model from Hugging Face / GitHub):
scripts/.venv_classifier/bin/python scripts/train_criminal_intent_classifier.py

# Evaluate the exported INT8 model against the OOD suite:
scripts/.venv_classifier/bin/python scripts/eval_criminal_intent_classifier.py
```

Requires network access for first run (HF Hub + `raw.githubusercontent.com`).
Hugging Face datasets cache under `~/.cache/huggingface`; subsequent runs reuse it.

## Datasets

| Source | License | Role |
|--------|---------|------|
| JBB-Behaviors, ClearHarm, HarmBench, AdvBench, Gretel Safety Alignment, OpenAI Moderation | MIT / Apache-2.0 | Harmful + benign prompts |
| NQ-Open, CLINC150, OpenAssistant OASST1 | Apache-2.0 / MIT | Benign hard negatives |
| Synthetic (project-generated, BSD-0) | — | Templated adversarial/hard-negative pairs **and** curated realistic colloquial prompts |

All external sources are downloaded at train time; nothing is vendored. The only
committed training inputs are this script + the regenerated OOD eval cases.

### Synthetic data design (low false-positive rate)

- `seeds` + `adversarial_prompts` / `hard_negative_prompts` — templated
  positive/negative pairs per crime-noun seed. Each harmful seed ships matched
  benign framings so trigger words also appear in safe context.
- `REALISTIC_HARMFUL_PROMPTS` / `REALISTIC_HARD_NEGATIVE_PROMPTS` — short,
  colloquial, real-world phrasings (labeled directly, not keyword-gated) so the
  model cannot miss obvious requests, each inoculated by a benign counterpart
  sharing the same surface terms.
- `labels_from_text` uses **word-boundary** keyword matching (`has_keyword`) so
  tokens like `ied`/`hack` do not fire inside `applied`/`hackathon`.
  `BENIGN_CONTEXT_PATTERNS` suppress labels for recovery/defensive/legal framing.
- Curated probes use a `force_train:` group-id prefix; `grouped_split` keeps
  those entirely in the train split so single-example obvious-harmful and
  known-benign concepts are reliably learned (generalization is still measured
  on the remaining sources). Without this, a unique-group probe can be held out
  and either missed (harmful) or blocked (benign).
- **Dual-use prompts stay allowed.** Requests with a common legitimate framing
  (e.g. hotwiring or unlocking *your own* car, picking *your own* lock) are not
  in the harmful set and appear as benign hard negatives. Only unambiguous
  framings (carjacking, defeating someone else's alarm) are labeled harmful.

## Outputs

`train_*` writes to two asset dirs (the second is a copy for tests):

- `app/src/main/assets/models/criminal_intent_classifier/` — runtime assets.
  **Only `model_config.json` is committed**; `model.onnx`, tokenizer, vocab are
  gitignored (`.gitignore` `/app/src/main/assets/models/**`) and regenerated.
- `feature/inference/src/test/assets/models/criminal_intent_classifier/` — the
  **committed** artifact set (`model.onnx` + tokenizer + config) used by
  `OnnxCriminalIntentClassifierTest`.
- `scripts/criminal_intent_ood_eval_cases.jsonl` — committed OOD eval cases.

`model_config.json` records `thresholds`, `eval_metrics` (block recall / false
positive rate), and dataset provenance. After retraining, review
`eval_metrics.false_positive_rate` and re-run the Kotlin OOD suite
(`OnnxCriminalIntentClassifierTest`), which asserts recall ≥ 0.90 and **zero**
false positives on its hard-negative list.
