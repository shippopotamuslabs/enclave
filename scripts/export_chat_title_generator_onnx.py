#!/usr/bin/env python3
"""Export Dafilab/chat-title-generator (flan-t5-small) to FP16 ONNX for download-on-demand.

Encoder-decoder model used on-device, via ONNX Runtime, to generate short chat titles
in parallel with the main chat LLM. Exported in the **no-past** form (plain
``text2text-generation`` task) so the Android greedy-decode loop can recompute per step;
titles are only a handful of tokens, so KV-cache is unnecessary.

Precision: **FP16**, not INT8. INT8 dynamic quantization (per-tensor and per-channel)
destroys this small model's output ("UNK UNK ...", garbage). FP16 preserves quality at
~half the FP32 size (~187MB total). NOTE: the FP16 graph must be run with ONNX Runtime
graph optimizations DISABLED (ORT_DISABLE_ALL) — ORT's layer-norm fusion conflicts with the
inserted fp16 casts. The Android loader sets the session opt level accordingly.

Packaging: download-on-demand. These files are NOT bundled in the APK. Upload the three
dist files to the model host and register them in the deploy `model_config.json` (root) with
the title-generator utility types. Until then the app falls back to truncated titles.

Dependencies:
    pip install "transformers>=4.46" "optimum[onnxruntime]>=1.23" "onnxruntime>=1.20" sentencepiece protobuf
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path

import onnx
from onnxruntime.transformers.onnx_model import OnnxModel
from optimum.exporters.onnx import main_export
from transformers import AutoConfig, AutoTokenizer

MODEL_ID = "Dafilab/chat-title-generator"
DEFAULT_DIST_DIR = Path("build/chat_title_generator_dist")
# Tiny gen-params config bundled in the APK (committed). Big model files are downloaded.
ASSET_DIR = Path("app/src/main/assets/models/chat_title_generator")

ENCODER_OUT = "encoder_model_fp16.onnx"
DECODER_OUT = "decoder_model_fp16.onnx"
TOKENIZER_OUT = "tokenizer.json"

MAX_INPUT_LENGTH = 384
MAX_OUTPUT_TOKENS = 24
TASK_PREFIX = "short title: "  # per the model card usage example


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-id", default=MODEL_ID)
    parser.add_argument("--dist-dir", type=Path, default=DEFAULT_DIST_DIR)
    parser.add_argument("--work-dir", type=Path, default=Path("build/chat_title_generator_export"))
    return parser.parse_args()


def clean_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def export_fp32(model_id: str, work_dir: Path) -> None:
    clean_dir(work_dir)
    main_export(model_name_or_path=model_id, output=work_dir, task="text2text-generation", opset=17)


def convert_fp16(src: Path, dst: Path) -> None:
    model = OnnxModel(onnx.load(str(src)))
    model.convert_float_to_float16(keep_io_types=True)
    model.save_model_to_file(str(dst))


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_bundled_gen_params(config: AutoConfig) -> None:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    gen_params = {
        "max_input_length": MAX_INPUT_LENGTH,
        "max_output_tokens": MAX_OUTPUT_TOKENS,
        "decoder_start_token_id": int(getattr(config, "decoder_start_token_id", 0) or 0),
        "eos_token_id": int(getattr(config, "eos_token_id", 1) or 1),
        "pad_token_id": int(getattr(config, "pad_token_id", 0) or 0),
        "task_prefix": TASK_PREFIX,
    }
    (ASSET_DIR / "model_config.json").write_text(
        json.dumps(gen_params, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )


def main() -> None:
    args = parse_args()
    dist = args.dist_dir
    clean_dir(dist)

    export_fp32(args.model_id, args.work_dir)
    convert_fp16(args.work_dir / "encoder_model.onnx", dist / ENCODER_OUT)
    convert_fp16(args.work_dir / "decoder_model.onnx", dist / DECODER_OUT)

    tokenizer = AutoTokenizer.from_pretrained(args.model_id, use_fast=True)
    tokenizer.save_pretrained(args.work_dir)
    shutil.copyfile(args.work_dir / "tokenizer.json", dist / TOKENIZER_OUT)

    write_bundled_gen_params(AutoConfig.from_pretrained(args.model_id))

    print(f"\nDist files in {dist} (upload these to the model host):")
    for name in (ENCODER_OUT, DECODER_OUT, TOKENIZER_OUT):
        f = dist / name
        print(f"  {name}: size={f.stat().st_size} sha256={sha256_of(f)}")
    print("\nAdd matching entries to the deploy model_config.json (root) with utilityType")
    print("title_generator_encoder / title_generator_decoder / title_generator_tokenizer.")


if __name__ == "__main__":
    main()
