#!/usr/bin/env bash
# Builds libstable_diffusion_core.so from xororz/local-dream (Apache-2.0) and copies into jniLibs.
# Requires: Android NDK (via Gradle), QNN SDK at qnn.sdk.dir / QNN_SDK_ROOT, git, cmake.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNI_OUT="$ROOT/local-image-gen-android/src/main/jniLibs/arm64-v8a"
BUILD_ROOT="${BUILD_ROOT:-$ROOT/build/local-dream-core}"
SRC_ROOT="${LOCAL_DREAM_SRC:-$BUILD_ROOT/local-dream}"

resolve_sdk() {
  if [[ -n "${QNN_SDK_ROOT:-}" && -d "$QNN_SDK_ROOT" ]]; then
    echo "$QNN_SDK_ROOT"
    return 0
  fi
  local prop="$ROOT/local.properties"
  if [[ -f "$prop" ]]; then
    local dir
    dir="$(grep -E '^qnn\.sdk\.dir=' "$prop" | head -1 | cut -d= -f2- | tr -d '\r')"
    if [[ -n "$dir" && -d "$dir" ]]; then
      echo "$dir"
      return 0
    fi
  fi
  return 1
}

SDK="$(resolve_sdk)" || {
  echo "QNN SDK required to build libstable_diffusion_core.so." >&2
  exit 1
}

mkdir -p "$BUILD_ROOT"
if [[ ! -d "$SRC_ROOT/.git" ]]; then
  git clone --depth 1 https://github.com/xororz/local-dream.git "$SRC_ROOT"
fi

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}"
if [[ -z "$NDK" && -d "$ROOT/local.properties" ]]; then
  sdk="$(grep sdk.dir "$ROOT/local.properties" | cut -d= -f2- | tr -d '\r')"
  if [[ -n "$sdk" ]]; then
    NDK="$(ls -d "$sdk"/ndk/* 2>/dev/null | sort -V | tail -1)"
  fi
fi
[[ -n "$NDK" ]] || { echo "Set ANDROID_NDK_HOME or sdk.dir in local.properties." >&2; exit 1; }

CMAKE_DIR="$SRC_ROOT/app/src/main/cpp"
BUILD_DIR="$BUILD_ROOT/out"
mkdir -p "$BUILD_DIR"

cmake -S "$CMAKE_DIR" -B "$BUILD_DIR" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-34 \
  -DQNN_SDK_ROOT="$SDK"

cmake --build "$BUILD_DIR" -j"$(nproc)"

SO="$(find "$BUILD_DIR" -name 'libstable_diffusion_core.so' | head -1)"
[[ -f "$SO" ]] || { echo "Build did not produce libstable_diffusion_core.so" >&2; exit 1; }

mkdir -p "$JNI_OUT"
cp -f "$SO" "$JNI_OUT/"
chmod +x "$JNI_OUT/libstable_diffusion_core.so"
echo "Installed $JNI_OUT/libstable_diffusion_core.so"