#!/usr/bin/env python3
"""
Optional: fine-tune MiniLM with a classification head and export ONNX for Android.

This replaces BOTH embedding + linear/MLP head in one ONNX session. Use when you want
the backbone to adapt (slower, larger artifact) instead of only training a head on
frozen embeddings (scripts/search_intent_train/train.py).

Usage (after pip install torch transformers optimum onnx onnxruntime):
  python scripts/search_intent_train/finetune_minilm_onnx.py \\
    --out-dir core/data/src/main/assets/utility_models/search_intent

The app's MiniLMEmbeddingEngine would need a separate code path to load this model
for search-intent only — not wired by default.
"""

from __future__ import annotations

import argparse
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=32)
    args = parser.parse_args()

    import numpy as np
    import torch
    from datasets import Dataset
    from transformers import (
        AutoModelForSequenceClassification,
        AutoTokenizer,
        Trainer,
        TrainingArguments,
    )

    import sys

    sys.path.insert(0, str(REPO_ROOT / "scripts"))
    from search_intent_train.data_sources import load_no_search_samples, load_search_samples
    import random

    rng = random.Random(42)
    pos = load_search_samples(rng, cap=4_000)
    neg_texts = load_no_search_samples(rng, cap=4_000)
    min_n = min(len(pos), len(neg_texts))
    texts = neg_texts[:min_n] + pos[:min_n]
    labels = [0] * min_n + [1] * min_n

    model_id = "sentence-transformers/all-MiniLM-L6-v2"
    tokenizer = AutoTokenizer.from_pretrained(model_id)
    model = AutoModelForSequenceClassification.from_pretrained(model_id, num_labels=2)

    def tokenize(batch):
        return tokenizer(batch["text"], truncation=True, max_length=256, padding="max_length")

    ds = Dataset.from_dict({"text": texts, "label": labels})
    ds = ds.train_test_split(test_size=0.15, seed=42)
    ds = ds.map(tokenize, batched=True)

    training_args = TrainingArguments(
        output_dir=str(args.out_dir / "checkpoints"),
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        evaluation_strategy="epoch",
        save_strategy="epoch",
        load_best_model_at_end=True,
        metric_for_best_model="eval_loss",
    )

    def compute_metrics(eval_pred):
        logits, labels_np = eval_pred
        preds = np.argmax(logits, axis=1)
        return {"accuracy": (preds == labels_np).mean()}

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=ds["train"],
        eval_dataset=ds["test"],
        compute_metrics=compute_metrics,
    )
    trainer.train()

    args.out_dir.mkdir(parents=True, exist_ok=True)
    onnx_path = args.out_dir / "search_intent_minilm.onnx"
    dummy = tokenizer("test", return_tensors="pt", padding="max_length", max_length=256)
    torch.onnx.export(
        model,
        (dummy["input_ids"], dummy["attention_mask"]),
        str(onnx_path),
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "logits": {0: "batch"},
        },
        opset_version=17,
    )
    tokenizer.save_pretrained(args.out_dir)
    print(f"Exported ONNX to {onnx_path}")
    print("Wire a dedicated SearchIntentOnnxEngine in :core:data to load this file.")


if __name__ == "__main__":
    main()
