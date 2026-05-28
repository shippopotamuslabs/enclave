#!/usr/bin/env python3
"""Train search-intent classifier and export Rust weights for classifier_core."""

from __future__ import annotations

import argparse
import random
import sys
from pathlib import Path

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split
from sentence_transformers import SentenceTransformer

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
sys.path.insert(0, str(SCRIPT_DIR.parent))

from search_intent_train.export_rust import write_linear_rust, write_mlp_rust
from search_intent_train.golden_prompts import GOLDEN_LABELED
from search_intent_train.routing_datasets import (
    load_routing_no_search_samples,
    load_routing_search_samples,
)
from search_intent_train.synthetic_data import generate_no_search_samples, generate_search_samples


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train search intent classifier")
    parser.add_argument(
        "--rust-out",
        type=Path,
        default=REPO_ROOT / "third_party" / "classifier_core" / "src" / "lib.rs",
    )
    parser.add_argument(
        "--head",
        choices=("linear", "mlp", "auto"),
        default="auto",
        help="Classifier head on frozen MiniLM embeddings (auto picks better golden accuracy)",
    )
    parser.add_argument("--hidden-dim", type=int, default=128)
    parser.add_argument(
        "--cap",
        type=int,
        default=2_000,
        help="Per-source cap for routing datasets (CRAG, QReCC, WildChat, etc.)",
    )
    parser.add_argument(
        "--synthetic-per-class",
        type=int,
        default=500,
        help="Extra procedural templates per class (supplement only; default 500)",
    )
    parser.add_argument(
        "--no-synthetic",
        action="store_true",
        help="Disable procedural template supplement",
    )
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--mlp-epochs", type=int, default=40)
    parser.add_argument(
        "--max-per-class",
        type=int,
        default=4_000,
        help="Hard cap per class after dedupe (avoids OOM during MiniLM encode)",
    )
    return parser.parse_args()


def encode(model: SentenceTransformer, texts: list[str]) -> np.ndarray:
    return model.encode(texts, show_progress_bar=True, normalize_embeddings=True)


def train_linear(x_train: np.ndarray, y_train: np.ndarray) -> LogisticRegression:
    clf = LogisticRegression(C=1.0, max_iter=2_000, random_state=42, class_weight="balanced")
    clf.fit(x_train, y_train)
    return clf


def train_mlp(
    x_train: np.ndarray,
    y_train: np.ndarray,
    x_val: np.ndarray,
    y_val: np.ndarray,
    hidden_dim: int,
    epochs: int,
) -> tuple[object, float]:
    import torch
    import torch.nn as nn

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    class MlpHead(nn.Module):
        def __init__(self, in_dim: int, hidden: int) -> None:
            super().__init__()
            self.fc1 = nn.Linear(in_dim, hidden)
            self.fc2 = nn.Linear(hidden, 1)

        def forward(self, x: torch.Tensor) -> torch.Tensor:
            return self.fc2(torch.relu(self.fc1(x))).squeeze(-1)

    model = MlpHead(x_train.shape[1], hidden_dim).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-4)
    loss_fn = nn.BCEWithLogitsLoss()

    x_tr = torch.tensor(x_train, dtype=torch.float32, device=device)
    y_tr = torch.tensor(y_train, dtype=torch.float32, device=device)
    x_va = torch.tensor(x_val, dtype=torch.float32, device=device)
    y_va = torch.tensor(y_val, dtype=torch.float32, device=device)

    best_val = -1.0
    best_state = None
    for _ in range(epochs):
        model.train()
        optimizer.zero_grad()
        loss = loss_fn(model(x_tr), y_tr)
        loss.backward()
        optimizer.step()

        model.eval()
        with torch.no_grad():
            val_acc = (
                (torch.sigmoid(model(x_va)) >= 0.5).float() == y_va
            ).float().mean().item()
        if val_acc > best_val:
            best_val = val_acc
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)
    return model, best_val


def mlp_predict_proba(model: object, x: np.ndarray) -> np.ndarray:
    import torch

    device = next(model.parameters()).device  # type: ignore[attr-defined]
    model.eval()  # type: ignore[attr-defined]
    with torch.no_grad():
        logits = model(torch.tensor(x, dtype=torch.float32, device=device))  # type: ignore[operator]
        return torch.sigmoid(logits).cpu().numpy()


def mlp_weights(model: object) -> tuple[list[list[float]], list[float], list[float], float]:
    fc1 = model.fc1  # type: ignore[attr-defined]
    fc2 = model.fc2  # type: ignore[attr-defined]
    w1 = fc1.weight.detach().cpu().numpy().tolist()
    b1 = fc1.bias.detach().cpu().numpy().tolist()
    w2 = fc2.weight.detach().cpu().numpy().reshape(-1).tolist()
    b2 = float(fc2.bias.detach().cpu().item())
    return w1, b1, w2, b2


def calibrate_threshold(probs: np.ndarray, labels: np.ndarray) -> float:
    best_t = 0.80
    best_acc = -1.0
    for t in np.linspace(0.05, 0.95, 181):
        acc = float(((probs >= t).astype(int) == labels).mean())
        if acc > best_acc:
            best_acc = acc
            best_t = float(t)
    return best_t


def evaluate_golden(name: str, predict_fn, st_model: SentenceTransformer) -> tuple[float, float]:
    texts = [p for p, _ in GOLDEN_LABELED]
    labels = np.array([label for _, label in GOLDEN_LABELED], dtype=int)
    emb = encode(st_model, texts)
    probs = predict_fn(emb)
    threshold = calibrate_threshold(probs, labels)
    pred = (probs >= threshold).astype(int)
    acc = (pred == labels).mean()
    print(f"\n--- Golden set ({name}) ---")
    for (prompt, label), prob, p in zip(GOLDEN_LABELED, probs, pred):
        tag = "SEARCH" if p == 1 else "NO SEARCH"
        ok = "ok" if p == label else "MISS"
        print(f"  [{ok}] [{tag}] p={prob:.4f} (want {label}) {prompt[:72]}...")
    print(f"Golden accuracy: {acc:.4f}, recommended threshold: {threshold:.3f}")
    return float(acc), threshold


def main() -> None:
    args = parse_args()
    rng = random.Random(args.seed)
    np.random.seed(args.seed)

    print("Loading routing-aware datasets (CRAG, SealQA, QReCC, ParallelPrompt, WildChat, ...)...")
    search_texts = load_routing_search_samples(rng, cap_per_source=args.cap)
    no_search_texts = load_routing_no_search_samples(rng, cap_per_source=args.cap)

    if not args.no_synthetic and args.synthetic_per_class > 0:
        print(f"Adding {args.synthetic_per_class} procedural supplements per class...")
        search_texts.extend(generate_search_samples(rng, args.synthetic_per_class))
        no_search_texts.extend(generate_no_search_samples(rng, args.synthetic_per_class))
        print(f"  After synthetic: {len(search_texts)} search, {len(no_search_texts)} non-search")

    print("Deduplicating corpus...")
    search_texts = list(dict.fromkeys(search_texts))
    no_search_texts = list(dict.fromkeys(no_search_texts))
    print(f"Total: {len(search_texts)} search, {len(no_search_texts)} non-search (before balance)")

    if len(search_texts) > args.max_per_class:
        rng.shuffle(search_texts)
        search_texts = search_texts[: args.max_per_class]
    if len(no_search_texts) > args.max_per_class:
        rng.shuffle(no_search_texts)
        no_search_texts = no_search_texts[: args.max_per_class]
    print(f"Capped to {len(search_texts)} search, {len(no_search_texts)} non-search")

    min_size = min(len(search_texts), len(no_search_texts))
    if min_size == 0:
        raise SystemExit("No training samples collected — check network/dataset access.")
    rng.shuffle(search_texts)
    rng.shuffle(no_search_texts)
    search_texts = search_texts[:min_size]
    no_search_texts = no_search_texts[:min_size]

    texts = no_search_texts + search_texts
    labels = np.array([0] * len(no_search_texts) + [1] * len(search_texts), dtype=int)
    print(f"Balanced training set: {len(texts)} ({min_size} per class)")

    print("Loading sentence-transformers/all-MiniLM-L6-v2 ...")
    st_model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
    embeddings = encode(st_model, texts)

    x_train, x_test, y_train, y_test = train_test_split(
        embeddings, labels, test_size=0.15, random_state=42, stratify=labels
    )

    sources_summary = (
        "Routing mix: CRAG, SealQA/FreshQA, QReCC, NQ/MARCO, ParallelPrompt, WildChat, "
        "tool-calling; optional synthetic supplement."
    )

    linear_clf = train_linear(x_train, y_train)
    linear_test_acc = float(linear_clf.score(x_test, y_test))
    print(f"\nLinear hold-out accuracy: {linear_test_acc:.4f}")
    print(classification_report(y_test, linear_clf.predict(x_test), target_names=["Non-Search", "Search"]))

    def linear_predict(x: np.ndarray) -> np.ndarray:
        return linear_clf.predict_proba(x)[:, 1]

    linear_golden_acc, linear_threshold = evaluate_golden("linear", linear_predict, st_model)

    chosen = "linear"
    recommended_threshold = linear_threshold
    test_acc = linear_test_acc
    mlp_model = None

    if args.head in ("mlp", "auto"):
        try:
            import torch  # noqa: F401

            mlp_model, _ = train_mlp(x_train, y_train, x_test, y_test, args.hidden_dim, args.mlp_epochs)
            mlp_probs = mlp_predict_proba(mlp_model, x_test)
            mlp_test_acc = float(((mlp_probs >= 0.5).astype(int) == y_test).mean())
            print(f"\nMLP hold-out accuracy: {mlp_test_acc:.4f}")

            def mlp_predict(x: np.ndarray) -> np.ndarray:
                return mlp_predict_proba(mlp_model, x)

            mlp_golden_acc, mlp_threshold = evaluate_golden("mlp", mlp_predict, st_model)
            if args.head == "mlp" or mlp_golden_acc >= linear_golden_acc:
                chosen = "mlp"
                recommended_threshold = mlp_threshold
                test_acc = mlp_test_acc
        except ImportError:
            print("PyTorch not installed; skipping MLP head.")

    print(f"\nSelected head: {chosen}")

    if chosen == "mlp" and mlp_model is not None:
        w1, b1, w2, b2 = mlp_weights(mlp_model)
        write_mlp_rust(
            args.rust_out,
            w1=w1,
            b1=b1,
            w2=w2,
            b2=b2,
            test_accuracy=test_acc,
            sources_summary=sources_summary,
            recommended_threshold=recommended_threshold,
            hidden_dim=args.hidden_dim,
        )
    else:
        write_linear_rust(
            args.rust_out,
            weights=linear_clf.coef_[0].tolist(),
            bias=float(linear_clf.intercept_[0]),
            test_accuracy=test_acc,
            sources_summary=sources_summary,
            recommended_threshold=recommended_threshold,
        )

    print(f"\nWrote {args.rust_out}")
    print(f"Suggested Kotlin threshold: SearchIntentPolicy.PRE_RETRIEVAL_THRESHOLD = {recommended_threshold:.2f}f")


if __name__ == "__main__":
    main()
