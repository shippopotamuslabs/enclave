#!/usr/bin/env python3
"""Export KoalaAI/Text-Moderation to a dynamically quantized INT8 ONNX asset.

Dependencies:
    pip install "transformers>=4.46" "optimum[onnxruntime]>=1.23" "onnxruntime>=1.20"

The script writes the ONNX model, tokenizer files, config.json, and an Android
model_config.json into app/src/main/assets/models/koala_text_moderation/.
"""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

from optimum.onnxruntime import ORTModelForSequenceClassification, ORTQuantizer
from optimum.onnxruntime.configuration import AutoQuantizationConfig
from transformers import AutoConfig, AutoTokenizer


MODEL_ID = "KoalaAI/Text-Moderation"
DEFAULT_ASSET_DIR = Path("app/src/main/assets/models/koala_text_moderation")
MODEL_FILE_NAME = "model_quantized.onnx"
TOKENIZER_FILE_NAME = "tokenizer.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-id", default=MODEL_ID, help="Hugging Face model id to export.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_ASSET_DIR,
        help="Android assets directory for the exported model.",
    )
    parser.add_argument(
        "--work-dir",
        type=Path,
        default=Path("build/koala_text_moderation_export"),
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


def quantize_model(work_dir: Path, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    quantizer = ORTQuantizer.from_pretrained(work_dir)
    quantization_config = AutoQuantizationConfig.arm64(is_static=False, per_channel=False)
    quantizer.quantize(save_dir=output_dir, quantization_config=quantization_config)

    exported_models = sorted(output_dir.glob("*.onnx"))
    if not exported_models:
        raise FileNotFoundError(f"No ONNX model was written under {output_dir}")

    model_path = output_dir / MODEL_FILE_NAME
    if exported_models[0] != model_path:
        exported_models[0].replace(model_path)
    return model_path


def export_tokenizer_and_config(model_id: str, output_dir: Path) -> AutoConfig:
    tokenizer = AutoTokenizer.from_pretrained(model_id, use_fast=True)
    tokenizer.save_pretrained(output_dir)

    config = AutoConfig.from_pretrained(model_id)
    config.save_pretrained(output_dir)
    return config


def write_android_model_config(output_dir: Path, labels: list[str]) -> None:
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

    (output_dir / "model_config.json").write_text(
        json.dumps(model_config, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    args = parse_args()
    output_dir = args.output_dir

    export_fp32_model(model_id=args.model_id, work_dir=args.work_dir)
    quantize_model(work_dir=args.work_dir, output_dir=output_dir)
    config = export_tokenizer_and_config(model_id=args.model_id, output_dir=output_dir)
    write_android_model_config(output_dir=output_dir, labels=sorted_labels(config))

    print(f"Exported {args.model_id} to {output_dir}")


if __name__ == "__main__":
    main()
