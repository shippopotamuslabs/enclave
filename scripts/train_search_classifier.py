#!/usr/bin/env python3
"""
Train the on-device search-intent classifier and regenerate classifier_core Rust weights.

See scripts/SEARCH_INTENT_TRAINING.md for dataset details and MiniLM fine-tuning options.

Usage:
  ./scripts/train_search_classifier.py
  ./scripts/train_search_classifier.py --head mlp
  ./scripts/build-classifier-bindings.sh   # after training, on a machine with Android NDK
"""

from __future__ import annotations

import os
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
VENV_DIR = os.path.join(SCRIPT_DIR, ".venv_classifier")
TRAIN_MAIN = os.path.join(SCRIPT_DIR, "search_intent_train", "train.py")


def run_command(args: list[str], cwd: str | None = None) -> None:
    print(f"Running: {' '.join(args)}")
    result = subprocess.run(args, cwd=cwd)
    if result.returncode != 0:
        sys.exit(result.returncode)


def setup_venv() -> str:
    if not os.path.exists(VENV_DIR):
        print("Creating Python virtual environment...")
        run_command([sys.executable, "-m", "venv", VENV_DIR])

    pip = os.path.join(VENV_DIR, "bin", "pip")
    run_command([pip, "install", "--upgrade", "pip"])
    run_command(
        [
            pip,
            "install",
            "sentence-transformers",
            "scikit-learn",
            "datasets",
            "tqdm",
            "numpy",
            "torch",
        ],
    )
    return os.path.join(VENV_DIR, "bin", "python")


def main() -> None:
    if not os.path.exists(VENV_DIR):
        python_exec = setup_venv()
    else:
        python_exec = os.path.join(VENV_DIR, "bin", "python")
        pip = os.path.join(VENV_DIR, "bin", "pip")
        run_command(
            [
                pip,
                "install",
                "-q",
                "sentence-transformers",
                "scikit-learn",
                "datasets",
                "tqdm",
                "numpy",
                "torch",
            ],
        )
    train_args = [python_exec, TRAIN_MAIN] + sys.argv[1:]
    run_command(train_args, cwd=PROJECT_ROOT)
    print(
        "\nNext: ./scripts/build-classifier-bindings.sh "
        "(regenerates UniFFI Kotlin + libclassifier_core.so for arm64)",
    )


if __name__ == "__main__":
    main()
