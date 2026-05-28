#!/usr/bin/env python3
"""Evaluate search-intent classifier on golden + extended prompt sets."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from sentence_transformers import SentenceTransformer

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from search_intent_train.extended_eval_prompts import EXTENDED_EVAL
from search_intent_train.golden_prompts import GOLDEN_LABELED
from search_intent_train.rust_mlp import load_mlp, predict_proba


def evaluate(name: str, labeled: list[tuple[str, int]], probs: np.ndarray, threshold: float) -> float:
    labels = np.array([lab for _, lab in labeled], dtype=int)
    pred = (probs >= threshold).astype(int)
    correct = int((pred == labels).sum())
    total = len(labeled)
    print(f"\n=== {name} ({correct}/{total} = {correct/total:.1%}) threshold={threshold:.3f} ===")
    for (prompt, label), prob, p in zip(labeled, probs, pred):
        tag = "SEARCH" if p == 1 else "NO SEARCH"
        ok = "ok" if p == label else "MISS"
        print(f"  [{ok}] [{tag}] p={prob:.4f} (want {label}) {prompt[:90]}")
    return correct / total


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--lib-rs",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "third_party" / "classifier_core" / "src" / "lib.rs",
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=None,
        help="Override RECOMMENDED_THRESHOLD from lib.rs",
    )
    args = parser.parse_args()

    threshold, w1, b1, w2, b2 = load_mlp(args.lib_rs)
    if args.threshold is not None:
        threshold = args.threshold

    print(f"Loaded MLP from {args.lib_rs}")
    print(f"Using threshold: {threshold:.4f}")

    model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")

    for name, labeled in (
        ("Golden (16)", GOLDEN_LABELED),
        ("Extended eval (28)", EXTENDED_EVAL),
        ("Combined (44)", GOLDEN_LABELED + EXTENDED_EVAL),
    ):
        texts = [p for p, _ in labeled]
        emb = model.encode(texts, show_progress_bar=False, normalize_embeddings=True)
        probs = predict_proba(np.asarray(emb, dtype=np.float32), w1, b1, w2, b2)
        evaluate(name, labeled, probs, threshold)


if __name__ == "__main__":
    main()
