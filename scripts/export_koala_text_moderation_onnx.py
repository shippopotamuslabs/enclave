#!/usr/bin/env python3
"""Export KoalaAI/Text-Moderation to a dynamically quantized INT8 ONNX asset.

The ONNX weights are NOT bundled in the APK — they are uploaded to Cloudflare R2
and downloaded on demand via UtilityModelFilePort (see model_config.json
``prompt_safety_classifier`` entry). Only the small tokenizer/config files ship
in the playStore source set.

Dependencies:
    pip install "transformers>=4.46" "optimum[onnxruntime]>=1.23" "onnxruntime>=1.20"

After export:
    1. Upload build/koala_text_moderation_dist/model_quantized.onnx to R2
       under the nsfw_text_classifier/ path.
    2. Sync the sha256/sizeInBytes in root model_config.json if they changed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path

from optimum.onnxruntime import ORTModelForSequenceClassification, ORTQuantizer
from optimum.onnxruntime.configuration import AutoQuantizationConfig
from transformers import AutoConfig, AutoTokenizer


MODEL_ID = "KoalaAI/Text-Moderation"
ASSET_DIR = Path("app/src/playStore/assets/models/nsfw_text_classifier")
DIST_DIR = Path("build/koala_text_moderation_dist")
WORK_DIR = Path("build/koala_text_moderation_export")
MODEL_FILE_NAME = "model_quantized.onnx"
TOKENIZER_FILE_NAME = "tokenizer.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-id", default=MODEL_ID, help="Hugging Face model id to export.")
    parser.add_argument(
        "--asset-dir",
        type=Path,
        default=ASSET_DIR,
        help="playStore assets directory for tokenizer/config files (ONNX weights excluded).",
    )
    parser.add_argument(
        "--dist-dir",
        type=Path,
        default=DIST_DIR,
        help="Output directory for the R2-upload ONNX artifact.",
    )
    parser.add_argument(
        "--work-dir",
        type=Path,
        default=WORK_DIR,
        help="Temporary export directory for the fp32 ONNX model.",
    )
    return parser.parse_args()


def sorted_labels(config: AutoConfig) -> list[str]:
    return [config.id2label[index] for index in sorted(config.id2label)]


def clean_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def export_fp32_model(model_id: str, work_dir: Path) -> None:
    clean_dir(work_dir)
    model = ORTModelForSequenceClassification.from_pretrained(model_id, export=True)
    model.save_pretrained(work_dir)


def quantize_model(work_dir: Path, dist_dir: Path) -> Path:
    dist_dir.mkdir(parents=True, exist_ok=True)
    quantizer = ORTQuantizer.from_pretrained(work_dir)
    quantization_config = AutoQuantizationConfig.arm64(is_static=False, per_channel=False)
    quantizer.quantize(save_dir=dist_dir, quantization_config=quantization_config)

    exported_models = sorted(dist_dir.glob("*.onnx"))
    if not exported_models:
        raise FileNotFoundError(f"No ONNX model was written under {dist_dir}")

    model_path = dist_dir / MODEL_FILE_NAME
    if exported_models[0] != model_path:
        exported_models[0].replace(model_path)
    return model_path


def export_tokenizer_and_config(model_id: str, asset_dir: Path) -> AutoConfig:
    asset_dir.mkdir(parents=True, exist_ok=True)
    tokenizer = AutoTokenizer.from_pretrained(model_id, use_fast=True)
    tokenizer.save_pretrained(asset_dir)

    config = AutoConfig.from_pretrained(model_id)
    config.save_pretrained(asset_dir)
    return config


def write_android_model_config(asset_dir: Path, labels: list[str]) -> None:
    model_config = {
        "model_path": MODEL_FILE_NAME,
        "tokenizer_path": TOKENIZER_FILE_NAME,
        "max_sequence_length": 384,
        "labels": labels,
        "label_aliases": {
            "sexual/minors": ["S3"],
            "illicit": [],
            "illicit/violent": [],
        },
        "thresholds": {
            "sexual/minors": 0.65,
            "illicit": 0.60,
            "illicit/violent": 0.60,
            "illegal_combined": 0.80,
        },
        "combined_illegal_labels": ["S3"],
    }

    (asset_dir / "model_config.json").write_text(
        json.dumps(model_config, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def sha256_and_size(path: Path) -> tuple[str, int]:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest(), path.stat().st_size


def main() -> None:
    args = parse_args()

    export_fp32_model(model_id=args.model_id, work_dir=args.work_dir)
    model_path = quantize_model(work_dir=args.work_dir, dist_dir=args.dist_dir)
    config = export_tokenizer_and_config(model_id=args.model_id, asset_dir=args.asset_dir)
    write_android_model_config(output_dir=args.asset_dir, labels=sorted_labels(config))

    # Guard: ONNX weights must NOT end up in APK assets.
    bundled_onnx = args.asset_dir / MODEL_FILE_NAME
    if bundled_onnx.exists():
        raise RuntimeError(
            f"ONNX weights must not be bundled in APK assets. Remove {bundled_onnx}."
        )

    sha, size = sha256_and_size(model_path)
    print(f"Exported {args.model_id}")
    print(f"  Tokenizer/config (bundled): {args.asset_dir}")
    print(f"  R2 upload file: {model_path} ({size:,} bytes)")
    print(f"  sha256: {sha}")
    print(f"  Next: upload {model_path.name} to R2 nsfw_text_classifier/ and sync")
    print(f"        root model_config.json sha256/sizeInBytes if they changed.")


if __name__ == "__main__":
    main()
