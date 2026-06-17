#!/usr/bin/env bash
# Build the Enclave diffusion C++ harness (host and/or Android device CLI).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HARNESS_DIR="$ROOT/local-image-gen-android/harness"
BUILD_ROOT="$ROOT/build/enclave-diffusion-harness"
TARGET="${1:-host}"

if [[ ! -f "$HARNESS_DIR/CMakeLists.txt" ]]; then
  echo "Missing harness CMake project at $HARNESS_DIR" >&2
  exit 1
fi

if [[ ! -f "$ROOT/local-image-gen-android/src/main/cpp/engine/thirdparty/MNN/CMakeLists.txt" ]]; then
  echo "Fetching diffusion native dependencies..."
  "$ROOT/scripts/setup-enclave-diffusion-deps.sh"
fi

QNN_SDK_ROOT="${QNN_SDK_ROOT:-/opt/qcom/aistack/qairt/2.47.0.260601}"
export QNN_SDK_ROOT

build_host() {
  local build_dir="$BUILD_ROOT/host"
  cmake -S "$HARNESS_DIR" -B "$build_dir" \
    -DCMAKE_BUILD_TYPE=Release \
    -DQNN_SDK_ROOT="$QNN_SDK_ROOT"
  cmake --build "$build_dir" -j"$(nproc)"
  echo "Host harness: $build_dir/enclave-diffusion-harness"
}

build_android() {
  local ndk_root="${ANDROID_NDK:-}"
  if [[ -z "$ndk_root" ]]; then
    local sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
    if [[ -d "$sdk/ndk" ]]; then
      ndk_root="$(ls -1d "$sdk/ndk"/* 2>/dev/null | sort -V | tail -1)"
    fi
  fi
  if [[ -z "$ndk_root" || ! -f "$ndk_root/build/cmake/android.toolchain.cmake" ]]; then
    echo "ANDROID_NDK not found. Set ANDROID_NDK or install NDK under \$ANDROID_HOME/ndk." >&2
    exit 1
  fi

  local build_dir="$BUILD_ROOT/android-arm64"
  cmake -S "$HARNESS_DIR" -B "$build_dir" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ndk_root/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-34 \
    -DQNN_SDK_ROOT="$QNN_SDK_ROOT"
  cmake --build "$build_dir" -j"$(nproc)"
  echo "Device CLI: $build_dir/enclave-diffusion-cli"
}

case "$TARGET" in
  host)
    build_host
    ;;
  android|device)
    build_android
    ;;
  all)
    build_host
    build_android
    ;;
  *)
    echo "Usage: $0 [host|android|all]" >&2
    exit 2
    ;;
esac
