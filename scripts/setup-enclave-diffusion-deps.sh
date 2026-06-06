#!/usr/bin/env bash
# Fetches third-party sources for the Enclave diffusion engine native build.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPS="$ROOT/local-image-gen-android/src/main/cpp/engine/thirdparty"

clone_if_missing() {
  local url="$1"
  local dir="$2"
  local ref="${3:-}"
  if [[ -d "$dir/.git" ]]; then
    return 0
  fi
  mkdir -p "$(dirname "$dir")"
  if [[ -n "$ref" ]]; then
    git clone --depth 1 --branch "$ref" "$url" "$dir"
  else
    git clone --depth 1 "$url" "$dir"
  fi
}

mkdir -p "$DEPS"
clone_if_missing https://github.com/alibaba/MNN.git "$DEPS/MNN" "3.0.0"
clone_if_missing https://github.com/mlc-ai/tokenizers-cpp.git "$DEPS/tokenizers-cpp"
if [[ -d "$DEPS/tokenizers-cpp/.git" ]]; then
  git -C "$DEPS/tokenizers-cpp" submodule update --init --depth 1 msgpack sentencepiece 2>/dev/null || true
fi
clone_if_missing https://github.com/xtensor-stack/xtl.git "$DEPS/xtl" "0.7.7"
clone_if_missing https://github.com/xtensor-stack/xtensor.git "$DEPS/xtensor" "0.25.0"

if [[ ! -f "$DEPS/stb/stb_image.h" ]]; then
  mkdir -p "$DEPS/stb"
  curl -fsSL "https://raw.githubusercontent.com/nothings/stb/master/stb_image.h" \
    -o "$DEPS/stb/stb_image.h"
fi

echo "Enclave diffusion third-party deps ready under $DEPS"
