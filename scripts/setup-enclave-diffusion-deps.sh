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

# Pin a repo to an exact commit (for refs that are not tags/branches).
pin_commit() {
  local dir="$1"
  local commit="$2"
  if [[ "$(git -C "$dir" rev-parse HEAD)" != "$commit" ]]; then
    git -C "$dir" fetch --depth 1 origin "$commit"
    git -C "$dir" checkout "$commit"
  fi
}

mkdir -p "$DEPS"
# MNN 3.2.1 — matches the Local Dream pin; 3.0.0 lacks int8 unary table support
# needed by current xororz CPU (MNN) model packs.
MNN_COMMIT="3db3cc904dfea55286972b472b040ad5525aa083"
clone_if_missing https://github.com/alibaba/MNN.git "$DEPS/MNN"
pin_commit "$DEPS/MNN" "$MNN_COMMIT"
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
