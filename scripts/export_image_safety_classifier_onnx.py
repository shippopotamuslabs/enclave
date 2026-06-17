#!/usr/bin/env python3
"""Export Falconsai/nsfw_image_detection to a validated ONNX asset for on-device image moderation.

viddexa/nsfw-detection-2-mini (EfficientNet) does NOT survive torch.onnx.export — logits collapse
to ~0 regardless of quantization. Falconsai/nsfw_image_detection (ViT) exports faithfully and
scores the probe image at nsfw=0.9998+ after INT8 weight quantization.

Packaging is download-on-demand:
  1. Run this script to emit build/image_safety_classifier_dist/model_quantized.onnx
  2. Upload to R2 under image_safety_classifier/
  3. Register the file in root model_config.json (utilityType image_safety_classifier)

Only app/src/main/assets/models/image_safety_classifier/model_config.json is bundled in the APK.

Dependencies:
    pip install "torch" "transformers>=4.46" "onnx>=1.16" "onnxruntime>=1.20" "pillow"
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
import torch.nn as nn
from onnxruntime.quantization import QuantType, quantize_dynamic
from PIL import Image
from transformers import AutoConfig, AutoImageProcessor, AutoModelForImageClassification


MODEL_ID = "Falconsai/nsfw_image_detection"
DEFAULT_ASSET_DIR = Path("app/src/main/assets/models/image_safety_classifier")
DEFAULT_DIST_DIR = Path("build/image_safety_classifier_dist")
DEFAULT_WORK_DIR = Path("build/image_safety_classifier_export")
MODEL_FILE_NAME = "model_quantized.onnx"
FP32_FILE_NAME = "model.onnx"
PROBE_IMAGE = Path("build/image_safety_probe/test_image.png")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-id", default=MODEL_ID, help="Hugging Face model id to export.")
    parser.add_argument(
        "--asset-config-dir",
        type=Path,
        default=DEFAULT_ASSET_DIR,
        help="Directory for the bundled Android model_config.json.",
    )
    parser.add_argument(
        "--dist-dir",
        type=Path,
        default=DEFAULT_DIST_DIR,
        help="Flat output directory for upload to R2.",
    )
    parser.add_argument(
        "--work-dir",
        type=Path,
        default=DEFAULT_WORK_DIR,
        help="Temporary export directory for the fp32 ONNX model.",
    )
    parser.add_argument(
        "--fp32",
        action="store_true",
        help="Skip quantization and emit FP32 model.onnx (~327 MB) instead.",
    )
    parser.add_argument(
        "--probe-image",
        type=Path,
        default=PROBE_IMAGE,
        help="Optional probe image used to validate ONNX fidelity.",
    )
    return parser.parse_args()


def sorted_labels(config: AutoConfig) -> list[str]:
    return [config.id2label[index] for index in sorted(config.id2label)]


class LogitsWrapper(nn.Module):
    def __init__(self, model: nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        return self.model(pixel_values=pixel_values).logits


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def export_fp32_model(model_id: str, work_dir: Path, pixel_values: torch.Tensor) -> Path:
    work_dir.mkdir(parents=True, exist_ok=True)
    model = AutoModelForImageClassification.from_pretrained(model_id).eval()
    wrapped = LogitsWrapper(model)
    fp32_path = work_dir / FP32_FILE_NAME
    torch.onnx.export(
        wrapped,
        pixel_values,
        str(fp32_path),
        input_names=["pixel_values"],
        output_names=["logits"],
        dynamic_axes={"pixel_values": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=17,
        dynamo=False,
    )
    return fp32_path


def quantize_model(fp32_path: Path, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    quantized_path = output_dir / MODEL_FILE_NAME
    quantize_dynamic(str(fp32_path), str(quantized_path), weight_type=QuantType.QInt8)
    return quantized_path


def validate_onnx(
    model_path: Path,
    processor: AutoImageProcessor,
    pytorch_model: nn.Module,
    probe_image: Path,
    labels: list[str],
) -> None:
    image = Image.open(probe_image).convert("RGB")
    inputs = processor(images=image, return_tensors="pt")
    pixel_values = inputs["pixel_values"]
    with torch.no_grad():
        torch_logits = pytorch_model(pixel_values=pixel_values).logits[0].numpy()

    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    onnx_logits = session.run(
        ["logits"],
        {"pixel_values": pixel_values.numpy().astype(np.float32)},
    )[0][0]
    max_diff = float(np.max(np.abs(torch_logits - onnx_logits)))
    torch_probs = np.exp(torch_logits - torch_logits.max())
    torch_probs /= torch_probs.sum()
    onnx_probs = np.exp(onnx_logits - onnx_logits.max())
    onnx_probs /= onnx_probs.sum()
    prob_diff = float(np.max(np.abs(torch_probs - onnx_probs)))
    top_idx = int(np.argmax(onnx_probs))
    if prob_diff > 0.01:
        raise SystemExit(
            f"ONNX validation failed: max probability diff {prob_diff:.6f} exceeds 0.01 "
            f"(logit diff {max_diff:.6f})",
        )
    print(
        f"  validation ok: max_logit_diff={max_diff:.6f}, max_prob_diff={prob_diff:.6f}, "
        f"top={labels[top_idx]}={onnx_probs[top_idx]:.4f}",
    )


def write_android_model_config(
    output_dir: Path,
    labels: list[str],
    model_file_name: str,
    input_size: int,
    channel_mean: list[float],
    channel_std: list[float],
) -> None:
    blocked_labels = [label for label in labels if label != "normal"]
    thresholds = {label: 0.5 for label in blocked_labels}
    model_config = {
        "model_path": model_file_name,
        "input_size": input_size,
        "labels": labels,
        "thresholds": thresholds,
        "blocked_labels": blocked_labels,
        "channel_mean": channel_mean,
        "channel_std": channel_std,
        "input_name": "pixel_values",
        "output_name": "logits",
        "resize_mode": "stretch",
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "model_config.json").write_text(
        json.dumps(model_config, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    args = parse_args()
    args.dist_dir.mkdir(parents=True, exist_ok=True)

    config = AutoConfig.from_pretrained(args.model_id)
    processor = AutoImageProcessor.from_pretrained(args.model_id)
    pytorch_model = AutoModelForImageClassification.from_pretrained(args.model_id).eval()
    labels = sorted_labels(config)
    input_size = int(processor.size["height"])

    if args.probe_image.exists():
        probe = Image.open(args.probe_image).convert("RGB")
        pixel_values = processor(images=probe, return_tensors="pt")["pixel_values"]
    else:
        pixel_values = torch.randn(1, 3, input_size, input_size)

    fp32_path = export_fp32_model(
        model_id=args.model_id,
        work_dir=args.work_dir,
        pixel_values=pixel_values,
    )

    if args.fp32:
        model_path = args.dist_dir / FP32_FILE_NAME
        model_path.write_bytes(fp32_path.read_bytes())
        model_file_name = FP32_FILE_NAME
    else:
        model_path = quantize_model(fp32_path=fp32_path, output_dir=args.dist_dir)
        model_file_name = MODEL_FILE_NAME

    if args.probe_image.exists():
        print(f"Validating {model_path.name} against {args.probe_image} ...")
        validate_onnx(
            model_path=model_path,
            processor=processor,
            pytorch_model=pytorch_model,
            probe_image=args.probe_image,
            labels=labels,
        )

    write_android_model_config(
        output_dir=args.asset_config_dir,
        labels=labels,
        model_file_name=model_file_name,
        input_size=input_size,
        channel_mean=[float(value) for value in processor.image_mean],
        channel_std=[float(value) for value in processor.image_std],
    )

    size = model_path.stat().st_size
    digest = sha256_of(model_path)
    print(f"Exported {args.model_id} -> {model_path}")
    print(f"  size={size} sha256={digest}")
    print("Next steps:")
    print(f"  1. Upload {model_path} to R2 under image_safety_classifier/{model_file_name}")
    print("  2. Add/update root model_config.json entry:")
    print(
        json.dumps(
            {
                "displayName": "Image Safety Classifier",
                "huggingFaceModelName": None,
                "huggingFacePath": "image_safety_classifier",
                "fileName": model_file_name,
                "sha256": digest,
                "sizeInBytes": size,
                "source": "R2",
                "utilityType": "image_safety_classifier",
                "isMultimodal": False,
                "configurations": [],
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()