"""Load MLP weights from generated classifier_core lib.rs for offline eval."""

from __future__ import annotations

import re
from pathlib import Path

import numpy as np

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LIB_RS = REPO_ROOT / "third_party" / "classifier_core" / "src" / "lib.rs"


def _floats(blob: str) -> np.ndarray:
    return np.array([float(x) for x in re.findall(r"(-?\d+(?:\.\d+)?(?:e[+-]?\d+)?)f32", blob)], dtype=np.float32)


def load_mlp(path: Path = DEFAULT_LIB_RS) -> tuple[float, np.ndarray, np.ndarray, np.ndarray, float]:
    text = path.read_text(encoding="utf-8")
    if "pub const W1:" not in text:
        raise ValueError(f"No MLP weights in {path} (linear head?)")

    threshold_m = re.search(r"RECOMMENDED_THRESHOLD:\s*f32\s*=\s*([\d.]+)f32", text)
    hidden_m = re.search(r"HIDDEN_DIM:\s*usize\s*=\s*(\d+)", text)
    if not threshold_m or not hidden_m:
        raise ValueError("Missing RECOMMENDED_THRESHOLD or HIDDEN_DIM")

    threshold = float(threshold_m.group(1))
    hidden_dim = int(hidden_m.group(1))

    def section(name: str) -> str:
        start = text.index(f"pub const {name}:")
        rest = text[start + 1 :]
        for marker in ("\n\npub const ", "\n\n#[", "\n\nfn ", "\n\npub fn "):
            try:
                end = rest.index(marker)
                return text[start : start + 1 + end]
            except ValueError:
                continue
        return text[start:]

    w1_blob = section("W1")
    b1_blob = section("B1")
    w2_blob = section("W2")
    b2_blob = section("B2")

    w1_flat = _floats(w1_blob)
    w1 = w1_flat.reshape(hidden_dim, 384)
    b1 = _floats(b1_blob)
    w2 = _floats(w2_blob)
    b2_m = re.search(r"pub const B2:\s*f32\s*=\s*([\d.e+-]+)f32", b2_blob)
    b2 = float(b2_m.group(1)) if b2_m else float(_floats(b2_blob)[0])
    return threshold, w1, b1, w2, b2


def predict_proba(embeddings: np.ndarray, w1: np.ndarray, b1: np.ndarray, w2: np.ndarray, b2: float) -> np.ndarray:
    hidden = np.maximum(embeddings @ w1.T + b1, 0.0)
    logits = hidden @ w2 + b2
    return 1.0 / (1.0 + np.exp(-logits))
