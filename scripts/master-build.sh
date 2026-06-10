#!/usr/bin/env bash
# Build all Enclave native libraries (Rust UniFFI, CMake/QNN, KleidiAI llama) and
# produce a signed release APK.
#
# Usage:
#   ./scripts/master-build.sh [options]
#
# Options:
#   --skip-rust            Skip agent/docgen/classifier Rust binding regeneration
#   --skip-diffusion-deps  Skip cloning MNN/tokenizers diffusion third-party sources
#   --skip-qnn             Skip Qualcomm QNN jniLibs sync (local SDXL QNN won't work)
#   --skip-kleidiai        Skip KleidiAI dual-variant llama jniLibs preparation
#   --help                 Show this help text
#
# Prerequisites:
#   - Android SDK + NDK (see local.properties sdk.dir)
#   - Rust toolchain (cargo)
#   - JDK 17
#   - Release signing: .keystore/enclave_keystore.jks and RELEASE_* keys in local.properties
#   - Optional: qnn.sdk.dir or QNN_SDK_ROOT for local-image-gen QNN runtime libs
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

SKIP_RUST=0
SKIP_DIFFUSION_DEPS=0
SKIP_QNN=0
SKIP_KLEIDIAI=0

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-rust) SKIP_RUST=1 ;;
    --skip-diffusion-deps) SKIP_DIFFUSION_DEPS=1 ;;
    --skip-qnn) SKIP_QNN=1 ;;
    --skip-kleidiai) SKIP_KLEIDIAI=1 ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

log() {
  echo ""
  echo "==> $*"
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

resolve_android_home() {
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    return 0
  fi
  local props="$PROJECT_ROOT/local.properties"
  if [[ -f "$props" ]]; then
    ANDROID_HOME="$(grep -E '^sdk\.dir=' "$props" | head -1 | cut -d= -f2- | tr -d '\r' | sed 's/\\:/:/g' | sed 's/\\\\/\\/g')"
    export ANDROID_HOME
  fi
  [[ -n "${ANDROID_HOME:-}" ]] || die "ANDROID_HOME is unset and sdk.dir is missing from local.properties"
}

ndk_host_prebuilt() {
  case "$(uname -s)" in
    Linux) echo "linux-x86_64" ;;
    Darwin)
      if [[ "$(uname -m)" == "arm64" ]]; then
        echo "darwin-arm64"
      else
        echo "darwin-x86_64"
      fi
      ;;
    *) die "Unsupported host OS for NDK toolchain detection: $(uname -s)" ;;
  esac
}

check_prerequisites() {
  log "Checking prerequisites"
  command -v cargo >/dev/null 2>&1 || die "cargo not found on PATH"
  command -v java >/dev/null 2>&1 || die "java not found on PATH"
  [[ -x "$PROJECT_ROOT/gradlew" ]] || die "gradlew is missing or not executable"
  resolve_android_home

  local ks="$PROJECT_ROOT/.keystore/enclave_keystore.jks"
  [[ -f "$ks" ]] || die "Release keystore not found at $ks"

  local props="$PROJECT_ROOT/local.properties"
  [[ -f "$props" ]] || die "local.properties not found"
  for key in RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
    grep -qE "^${key}=" "$props" || die "Missing $key in local.properties (required for :app:assembleRelease)"
  done
}

build_rust_bindings() {
  log "Building Rust UniFFI bindings (agent, docgen, classifier)"
  "$SCRIPT_DIR/build-agent-bindings.sh"
  "$SCRIPT_DIR/build-docgen-bindings.sh"
  "$SCRIPT_DIR/build-classifier-bindings.sh"
}

setup_diffusion_deps() {
  log "Ensuring local-image-gen diffusion native third-party sources"
  "$SCRIPT_DIR/setup-enclave-diffusion-deps.sh"
}

sync_qnn_libs() {
  log "Syncing Qualcomm QNN runtime libraries (optional)"
  if "$SCRIPT_DIR/sync-qnn-jniLibs.sh"; then
    echo "QNN jniLibs synced."
  else
    echo "WARN: QNN sync failed or SDK not configured; local SDXL QNN generation may not work at runtime." >&2
  fi
}

build_kleidiai_llama_jni_libs() {
  log "Preparing KleidiAI dual-variant llama jniLibs (SVE + NEON via release CMake)"
  local llama_dir="$PROJECT_ROOT/llama-android"
  local jni_libs_dir="$llama_dir/src/main/jniLibs/arm64-v8a"
  local ndk_version="28.2.13676358"
  local host_prebuilt
  host_prebuilt="$(ndk_host_prebuilt)"
  local ndk_lib_dir="$ANDROID_HOME/ndk/$ndk_version/toolchains/llvm/prebuilt/$host_prebuilt/sysroot/usr/lib/aarch64-linux-android"
  local kleidiai_dir="$PROJECT_ROOT/third_party/kleidiai-1.22.0"

  [[ -d "$ANDROID_HOME/ndk/$ndk_version" ]] || die "NDK $ndk_version not installed (required by llama-android)"
  [[ -d "$kleidiai_dir" ]] || die "KleidiAI sources missing at $kleidiai_dir"
  [[ -f "$ndk_lib_dir/libc++_shared.so" ]] || die "libc++_shared.so not found under $ndk_lib_dir"

  rm -rf "$jni_libs_dir"
  mkdir -p "$jni_libs_dir"
  cp "$ndk_lib_dir/libc++_shared.so" "$jni_libs_dir/"
  echo "Copied libc++_shared.so"

  rm -rf "$llama_dir/.cxx" "$llama_dir/build"
  # CMake-only pass: SVE variant cannot share a configure pass with NEON (global ggml flags).
  ./gradlew ':llama-android:buildCMakeRelWithDebInfo[arm64-v8a]' \
    -PCMAKE_ARGS="-DGGML_CPU_ARM_ENABLE_SVE=ON;-DFETCHCONTENT_SOURCE_DIR_KLEIDIAI_DOWNLOAD=$kleidiai_dir" \
    --no-daemon

  local sve_lib
  sve_lib="$(find "$llama_dir/build" -name "libllama-jni-sve.so" -type f 2>/dev/null | head -1 || true)"
  [[ -n "$sve_lib" ]] || die "SVE llama library not found after :llama-android:buildCMakeRelWithDebInfo"
  cp "$sve_lib" "$jni_libs_dir/libllama-jni-sve.so"
  echo "Copied libllama-jni-sve.so"

  # Clear native build cache so the app release build produces the NEON variant.
  rm -rf "$llama_dir/.cxx" "$llama_dir/build"
}

build_native_android_modules() {
  log "Building CMake native Android modules (release)"
  ./gradlew \
    :llama-android:assembleRelease \
    :whisper-android:assembleRelease \
    :local-image-gen-android:assembleRelease \
    --no-daemon
}

build_app_release() {
  log "Building signed release APK"
  ./gradlew :app:assembleRelease --no-daemon

  local apk_dir="$PROJECT_ROOT/app/build/outputs/apk/release"
  if [[ -d "$apk_dir" ]]; then
    echo ""
    echo "Release APK output:"
    find "$apk_dir" -name '*.apk' -print
  fi
}

main() {
  echo "Enclave master build (native + release APK)"
  echo "Project root: $PROJECT_ROOT"

  check_prerequisites

  if [[ "$SKIP_RUST" -eq 0 ]]; then
    build_rust_bindings
  else
    log "Skipping Rust binding regeneration (--skip-rust)"
  fi

  if [[ "$SKIP_DIFFUSION_DEPS" -eq 0 ]]; then
    setup_diffusion_deps
  else
    log "Skipping diffusion third-party setup (--skip-diffusion-deps)"
  fi

  if [[ "$SKIP_QNN" -eq 0 ]]; then
    sync_qnn_libs
  else
    log "Skipping QNN jniLibs sync (--skip-qnn)"
  fi

  if [[ "$SKIP_KLEIDIAI" -eq 0 ]]; then
    build_kleidiai_llama_jni_libs
  else
    log "Skipping KleidiAI llama jniLibs preparation (--skip-kleidiai)"
  fi

  build_native_android_modules
  build_app_release

  echo ""
  echo "Master build complete."
}

main "$@"
