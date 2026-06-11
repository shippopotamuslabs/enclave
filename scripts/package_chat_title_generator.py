#!/usr/bin/env python3
"""Download, verify, and stage LiquidAI/LFM2.5-350M-ONNX (q4 variant) for on-device chat titling.

Model: LiquidAI/LFM2.5-350M-ONNX
Variant: q4 = INT4 weights (MatMulNBits operator) with FP32 activations.

IMPORTANT — do NOT use fp16 or q4f16 variants:
    Full-FP16 compute produces all-NaN logits on ARM64. ORT's NEON fp16 kernels accumulate in
    fp16, which overflows large activations. This killed the previous T5 (flan-t5-small) export
    too; the lesson carried forward here. Always use INT4-weights + FP32-activations (the plain
    q4 variant) or INT8 quantisation. Never resurface a full-FP16 compute graph on ARM64.

Architecture: decoder-only (LFM2.5 liquid foundation model), unlike the previous T5 encoder-
decoder pair. Single ONNX session with external data file; prompt uses ChatML format.

Packaging is download-on-demand: upload the three dist files to the R2 model host and register
them in the root model_config.json with utilityType title_generator_encoder (model graph),
title_generator_decoder (external weights), and title_generator_tokenizer. Files are NOT bundled
in the APK; until downloaded the app falls back to truncated titles.
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import urllib.request
from pathlib import Path

HF_BASE = "https://huggingface.co/LiquidAI/LFM2.5-350M-ONNX/resolve/main/"

REMOTE_FILES = [
    ("onnx/model_q4.onnx",      "model_q4.onnx"),
    ("onnx/model_q4.onnx_data", "model_q4.onnx_data"),
    ("tokenizer.json",           "tokenizer.json"),
]

EXPECTED = {
    "model_q4.onnx":      ("d1a705712e93aafaba1346b32245fa59e7857a46e2272003c0e8c524977e0de8", 183442),
    "model_q4.onnx_data": ("71ec6ad38a4c463dcb3dba671d06a1d9861be3a23e51290d818b95c0b7d2a5db", 293629952),
    "tokenizer.json":     ("29d43b4be8e8a896fefd7cd836ca6d6b4eedd249f823866ce0453b368e646f49", 3297793),
}

DEFAULT_WORK_DIR = Path("build/chat_title_generator_export")
DEFAULT_DIST_DIR = Path("build/chat_title_generator_dist")

SMOKE_PROMPT = (
    "<|startoftext|><|im_start|>user\n"
    "Write a 3-6 word title summarizing the topic of this message. "
    "Respond with the title and nothing else.\n\n"
    "Message: How do I bake sourdough bread at home?<|im_end|>\n"
    "<|im_start|>assistant\n"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dist-dir", type=Path, default=DEFAULT_DIST_DIR)
    parser.add_argument("--work-dir", type=Path, default=DEFAULT_WORK_DIR)
    return parser.parse_args()


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_file(url: str, dest: Path, expected_size: int) -> None:
    """Download url to dest, skipping if the file already has the expected size."""
    if dest.exists() and dest.stat().st_size == expected_size:
        print(f"  {dest.name}: already present ({expected_size} bytes), skipping download")
        return
    print(f"  Downloading {url} -> {dest.name} ...")
    with urllib.request.urlopen(url) as resp, dest.open("wb") as out:
        shutil.copyfileobj(resp, out)
    actual = dest.stat().st_size
    print(f"  {dest.name}: downloaded {actual} bytes")


def download_all(work_dir: Path) -> None:
    work_dir.mkdir(parents=True, exist_ok=True)
    for remote_path, local_name in REMOTE_FILES:
        url = HF_BASE + remote_path
        dest = work_dir / local_name
        _, expected_size = EXPECTED[local_name]
        download_file(url, dest, expected_size)


def stage(work_dir: Path, dist_dir: Path) -> None:
    """Clean dist dir and copy files flat."""
    if dist_dir.exists():
        shutil.rmtree(dist_dir)
    dist_dir.mkdir(parents=True, exist_ok=True)
    for _, local_name in REMOTE_FILES:
        src = work_dir / local_name
        dst = dist_dir / local_name
        shutil.copyfile(src, dst)
        print(f"  staged: {dst}")


def verify_smoke(dist_dir: Path) -> None:
    """Run a greedy-decode smoke test against the staged model."""
    import numpy as np
    import onnxruntime as ort
    from tokenizers import Tokenizer

    tokenizer = Tokenizer.from_file(str(dist_dir / "tokenizer.json"))
    enc = tokenizer.encode(SMOKE_PROMPT, add_special_tokens=False)
    input_ids_list = enc.ids

    sess_opts = ort.SessionOptions()
    sess = ort.InferenceSession(
        str(dist_dir / "model_q4.onnx"),
        sess_options=sess_opts,
        providers=["CPUExecutionProvider"],
    )

    # Discover cache input names from the session
    input_names = {inp.name for inp in sess.get_inputs()}
    past_conv_names = sorted(n for n in input_names if n.startswith("past_conv."))
    kv_key_names   = sorted(n for n in input_names if n.endswith(".key") and "past_key_values" in n)
    kv_value_names = sorted(n for n in input_names if n.endswith(".value") and "past_key_values" in n)

    seq_len = len(input_ids_list)
    total_len = seq_len

    input_ids_np = np.array([input_ids_list], dtype=np.int64)
    attn_mask_np = np.ones((1, total_len), dtype=np.int64)

    # Initial cache state
    conv_caches: dict[str, "np.ndarray"] = {
        name: np.zeros((1, 1024, 3), dtype=np.float32) for name in past_conv_names
    }
    kv_caches: dict[str, "np.ndarray"] = {}
    for name in kv_key_names:
        kv_caches[name] = np.zeros((1, 8, 0, 64), dtype=np.float32)
    for name in kv_value_names:
        kv_caches[name] = np.zeros((1, 8, 0, 64), dtype=np.float32)

    EOS_TOKEN_ID = 7
    MAX_NEW_TOKENS = 24
    generated_ids: list[int] = []

    for step in range(MAX_NEW_TOKENS):
        feeds: dict[str, "np.ndarray"] = {
            "input_ids":          input_ids_np,
            "attention_mask":     attn_mask_np,
            "num_logits_to_keep": np.array(1, dtype=np.int64),
        }
        feeds.update(conv_caches)
        feeds.update(kv_caches)

        outputs = sess.run(None, feeds)
        output_names = [o.name for o in sess.get_outputs()]
        output_map = dict(zip(output_names, outputs))

        logits = output_map["logits"]
        next_id = int(np.argmax(logits[0, -1]))

        if next_id == EOS_TOKEN_ID:
            break
        generated_ids.append(next_id)

        # Update caches: present_conv.X -> past_conv.X
        for out_name, arr in output_map.items():
            if out_name.startswith("present_conv."):
                suffix = out_name[len("present_conv."):]
                past_name = f"past_conv.{suffix}"
                if past_name in conv_caches:
                    conv_caches[past_name] = arr
            elif out_name.startswith("present.") and out_name.endswith(".key"):
                # present.X.key -> past_key_values.X.key
                inner = out_name[len("present."):-len(".key")]
                past_name = f"past_key_values.{inner}.key"
                if past_name in kv_caches:
                    kv_caches[past_name] = arr
            elif out_name.startswith("present.") and out_name.endswith(".value"):
                inner = out_name[len("present."):-len(".value")]
                past_name = f"past_key_values.{inner}.value"
                if past_name in kv_caches:
                    kv_caches[past_name] = arr

        # Next step: single token input
        input_ids_np = np.array([[next_id]], dtype=np.int64)
        total_len += 1
        attn_mask_np = np.ones((1, total_len), dtype=np.int64)

    if not generated_ids:
        raise AssertionError("Smoke test produced no tokens — model or session setup is broken")

    result = tokenizer.decode(generated_ids)
    print(f"\nSmoke test title: {result!r}")


def print_dist_summary(dist_dir: Path) -> None:
    print(f"\nDist files in {dist_dir}:")
    for _, name in REMOTE_FILES:
        f = dist_dir / name
        size = f.stat().st_size
        digest = sha256_of(f)
        print(f"  {name}: size={size} sha256={digest}")
    print()
    print("Next steps:")
    print("  1. Upload the three dist files to the R2 model host under chat_title_generator/")
    print("  2. Ensure root model_config.json entries (title_generator_encoder,")
    print("     title_generator_decoder, title_generator_tokenizer) match the printed hashes.")


def main() -> None:
    args = parse_args()

    print(f"Work dir: {args.work_dir}")
    print(f"Dist dir: {args.dist_dir}")

    print("\nDownloading model files...")
    download_all(args.work_dir)

    print("\nStaging dist files...")
    stage(args.work_dir, args.dist_dir)

    print("\nRunning smoke test...")
    verify_smoke(args.dist_dir)

    print_dist_summary(args.dist_dir)


if __name__ == "__main__":
    main()
