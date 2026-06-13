#!/usr/bin/env python3
"""Classify a local image with the same ONNX model/config as OnnxImageSafetyClassifier.

Usage:
    python3 scripts/classify_image_safety.py path/to/image.jpg
    python3 scripts/classify_image_safety.py path/to/image.png --config app/src/main/assets/models/image_safety_classifier/model_config.json

Requires: pip install onnxruntime pillow numpy
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path, help="Image file to classify.")
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("app/src/main/assets/models/image_safety_classifier/model_config.json"),
        help="model_config.json path.",
    )
    parser.add_argument(
        "--model",
        type=Path,
        default=None,
        help="Optional ONNX path (defaults to config directory + model_path).",
    )
    return parser.parse_args()


def softmax(logits: np.ndarray) -> np.ndarray:
    shifted = logits - logits.max()
    exp_values = np.exp(shifted)
    return exp_values / exp_values.sum()


def center_crop_to_size(image: Image.Image, size: int) -> Image.Image:
    width, height = image.size
    shortest = min(width, height)
    scale = shortest / size
    crop_size = max(1, int(size * scale))
    left = max(0, (width - crop_size) // 2)
    top = max(0, (height - crop_size) // 2)
    cropped = image.crop((left, top, left + crop_size, top + crop_size))
    return cropped.resize((size, size), Image.Resampling.BILINEAR)


def resize_to_size(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.Resampling.BILINEAR)


def preprocess(
    image_path: Path,
    input_size: int,
    mean: list[float],
    std: list[float],
    resize_mode: str,
) -> np.ndarray:
    image = Image.open(image_path).convert("RGB")
    resized = resize_to_size(image, input_size) if resize_mode == "stretch" else center_crop_to_size(image, input_size)
    pixels = np.asarray(resized, dtype=np.float32) / 255.0
    normalized = (pixels - np.array(mean, dtype=np.float32)) / np.array(std, dtype=np.float32)
    chw = np.transpose(normalized, (2, 0, 1))
    return np.expand_dims(chw, axis=0)


def main() -> None:
    args = parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    model_path = args.model if args.model else args.config.parent / config["model_path"]
    if not model_path.exists():
        raise SystemExit(f"Model not found: {model_path}")

    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    tensor = preprocess(
        args.image,
        input_size=config["input_size"],
        mean=config["channel_mean"],
        std=config["channel_std"],
        resize_mode=config.get("resize_mode", "center_crop"),
    )
    logits = session.run([config["output_name"]], {config["input_name"]: tensor})[0][0]
    probabilities = softmax(np.asarray(logits, dtype=np.float32))

    labels = config["labels"]
    scores = {label: float(prob) for label, prob in zip(labels, probabilities)}
    blocked_labels = config["blocked_labels"]
    thresholds = config.get("thresholds", {})

    print(f"image: {args.image}")
    print(f"model: {model_path}")
    print("scores (descending):")
    for label, score in sorted(scores.items(), key=lambda item: item[1], reverse=True):
        print(f"  {label}: {score:.4f}")

    print("block check:")
    blocked = False
    blocking_label = None
    blocking_score = 0.0
    for label in blocked_labels:
        threshold = float(thresholds.get(label, 0.5))
        score = scores.get(label, 0.0)
        would_block = score >= threshold
        print(f"  {label}: score={score:.4f}, threshold={threshold:.4f}, blocked={would_block}")
        if would_block and score >= blocking_score:
            blocked = True
            blocking_label = label
            blocking_score = score

    top_label = max(scores, key=scores.get)
    print(f"top_label: {top_label} ({scores[top_label]:.4f})")
    print(f"blocked: {blocked}" + (f" ({blocking_label})" if blocking_label else ""))


if __name__ == "__main__":
    main()