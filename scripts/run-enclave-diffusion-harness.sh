#!/usr/bin/env bash
# Fast iteration harness for Enclave diffusion C++.
#
# Host (Linux): validate pack, parse QNN metadata, run MNN CLIP — no HTP required.
# Device (adb): push CLI + QNN libs, run qnn-init / generate on phone.
#
# Examples:
#   ./scripts/run-enclave-diffusion-harness.sh --target host \
#     --model-dir /path/to/sdxl-qnn --step all
#
#   ./scripts/run-enclave-diffusion-harness.sh --target device \
#     --device-model-dir /sdcard/Android/data/com.browntowndev.enclave/files/models/sdxl-qnn \
#     --step qnn-init --prompt "a cat"
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_HOST="$ROOT/build/enclave-diffusion-harness/host/enclave-diffusion-harness"
BUILD_DEVICE="$ROOT/build/enclave-diffusion-harness/android-arm64/enclave-diffusion-cli"
JNILIBS="$ROOT/local-image-gen-android/src/main/jniLibs/arm64-v8a"
QNN_SDK_ROOT="${QNN_SDK_ROOT:-/opt/qcom/aistack/qairt/2.47.0.260601}"

TARGET="host"
MODEL_DIR=""
DEVICE_MODEL_DIR=""
DEVICE_OUTPUT="/sdcard/Download/enclave-harness-out.png"
HARNESS_ARGS=()
STEP=""
RUN_UNIT_TESTS=0

usage() {
  cat <<'EOF'
Usage: run-enclave-diffusion-harness.sh [script options] [-- harness options]

Script options:
  --target host|device     Where to execute (default: host)
  --model-dir PATH         Host model pack directory
  --device-model-dir PATH  Model pack path on device (required for device target)
  --step STEP              validate|qnn-metadata|clip|qnn-init|generate|all
  --unit-tests             Run existing C++ unit tests (host only)
  --rebuild                Force rebuild before run
  --help

Remaining args are forwarded to enclave-diffusion-harness / enclave-diffusion-cli.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      TARGET="$2"
      shift 2
      ;;
    --model-dir)
      MODEL_DIR="$2"
      shift 2
      ;;
    --device-model-dir)
      DEVICE_MODEL_DIR="$2"
      shift 2
      ;;
    --step)
      STEP="$2"
      shift 2
      ;;
    --unit-tests)
      RUN_UNIT_TESTS=1
      shift
      ;;
    --rebuild)
      "$ROOT/scripts/build-enclave-diffusion-harness.sh" "$TARGET"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --)
      shift
      HARNESS_ARGS+=("$@")
      break
      ;;
    *)
      HARNESS_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ "$RUN_UNIT_TESTS" -eq 1 ]]; then
  UNIT_BUILD="$ROOT/build/enclave-diffusion-host-tests"
  cmake -S "$ROOT/local-image-gen-android/src/test/cpp" -B "$UNIT_BUILD"
  cmake --build "$UNIT_BUILD" -j"$(nproc)"
  ctest --test-dir "$UNIT_BUILD" --output-on-failure
  echo "Unit tests passed."
  if [[ -z "$MODEL_DIR" && "$TARGET" == "host" && -z "$STEP" && ${#HARNESS_ARGS[@]} -eq 0 ]]; then
    exit 0
  fi
fi

if [[ -n "$STEP" ]]; then
  HARNESS_ARGS=(--step "$STEP" "${HARNESS_ARGS[@]}")
fi

run_host() {
  if [[ ! -x "$BUILD_HOST" ]]; then
    "$ROOT/scripts/build-enclave-diffusion-harness.sh" host
  fi
  if [[ -z "$MODEL_DIR" ]]; then
    echo "--model-dir is required for --target host" >&2
    exit 2
  fi
  exec "$BUILD_HOST" --model-dir "$MODEL_DIR" "${HARNESS_ARGS[@]}"
}

run_device() {
  if [[ ! -x "$BUILD_DEVICE" ]]; then
    "$ROOT/scripts/build-enclave-diffusion-harness.sh" android
  fi
  if [[ -z "$DEVICE_MODEL_DIR" ]]; then
    DEVICE_MODEL_DIR="/sdcard/Android/data/com.browntowndev.enclave/files/models/sdxl-qnn"
    echo "Using default device model dir: $DEVICE_MODEL_DIR"
  fi
  if [[ ! -d "$JNILIBS" ]]; then
    echo "Missing jniLibs. Run scripts/sync-qnn-jniLibs.sh first." >&2
    exit 1
  fi

  adb wait-for-device
  REMOTE_DIR="/data/local/tmp/enclave-harness"
  adb shell "mkdir -p '$REMOTE_DIR/qnnlibs'"
  adb push "$BUILD_DEVICE" "$REMOTE_DIR/enclave-diffusion-cli"
  SDXL_QNN_LIBS=(
    libQnnHtp.so
    libQnnSystem.so
    libQnnHtpPrepare.so
    libQnnHtpV79.so
    libQnnHtpV79Skel.so
    libQnnHtpV79Stub.so
  )
  for lib in "${SDXL_QNN_LIBS[@]}"; do
    if [[ ! -f "$JNILIBS/$lib" ]]; then
      echo "Missing $JNILIBS/$lib — run scripts/sync-qnn-jniLibs.sh" >&2
      exit 1
    fi
    adb push "$JNILIBS/$lib" "$REMOTE_DIR/qnnlibs/"
  done

  local forwarded=("${HARNESS_ARGS[@]}")
  if [[ ${#forwarded[@]} -eq 0 || " ${forwarded[*]} " != *" --step "* ]]; then
    forwarded=(--step "${STEP:-qnn-init}" "${forwarded[@]}")
  fi
  if [[ " ${forwarded[*]} " != *" --prompt "* ]]; then
    forwarded+=(--prompt "a photo of a cat")
  fi

  quote_remote() {
    local quoted=()
    local arg
    for arg in "$@"; do
      quoted+=("$(printf '%q' "$arg")")
    done
    printf '%s ' "${quoted[@]}"
  }

  local remote_cli_args=(
    --model-dir "$DEVICE_MODEL_DIR"
    --qnn-runtime-dir "$REMOTE_DIR/qnnlibs"
    --dsp-library-path "$REMOTE_DIR/qnnlibs"
    --output "$DEVICE_OUTPUT"
    "${forwarded[@]}"
  )
  local remote_cli_quoted
  remote_cli_quoted="$(quote_remote "${remote_cli_args[@]}")"

  adb logcat -c || true
  set +e
  adb shell "cd '$REMOTE_DIR' && \
    chmod +x ./enclave-diffusion-cli && \
    export LD_LIBRARY_PATH='$REMOTE_DIR/qnnlibs:/vendor/lib64' && \
    export DSP_LIBRARY_PATH='$REMOTE_DIR/qnnlibs' && \
    export ADSP_LIBRARY_PATH='$REMOTE_DIR/qnnlibs' && \
    export CDSP_LIBRARY_PATH='$REMOTE_DIR/qnnlibs' && \
    ./enclave-diffusion-cli ${remote_cli_quoted}"
  local rc=$?
  set -e

  echo ""
  echo "=== logcat (enclave-qnn) ==="
  adb logcat -d -s enclave-qnn:V Enclave:V AndroidRuntime:E | tail -200

  local effective_step="${STEP:-}"
  if [[ -z "$effective_step" ]]; then
    for arg in "${forwarded[@]}"; do
      if [[ "$arg" == "generate" ]]; then
        effective_step="generate"
        break
      fi
    done
    if [[ -z "$effective_step" ]]; then
      for ((i = 0; i < ${#forwarded[@]}; i++)); do
        if [[ "${forwarded[i]}" == "--step" && $((i + 1)) -lt ${#forwarded[@]} ]]; then
          effective_step="${forwarded[i + 1]}"
          break
        fi
      done
    fi
  fi

  local pull_from="$DEVICE_OUTPUT"
  for ((i = 0; i < ${#forwarded[@]}; i++)); do
    if [[ "${forwarded[i]}" == "--output" && $((i + 1)) -lt ${#forwarded[@]} ]]; then
      pull_from="${forwarded[i + 1]}"
      break
    fi
  done

  if [[ "$rc" -eq 0 && "$effective_step" == "generate" ]]; then
    local pull_to="$ROOT/build/enclave-diffusion-harness/last-device-output.png"
    adb pull "$pull_from" "$pull_to" >/dev/null
    echo "Pulled output image from $pull_from to $pull_to"
  fi
  exit "$rc"
}

case "$TARGET" in
  host)
    run_host
    ;;
  device)
    run_device
    ;;
  *)
    echo "Unknown --target: $TARGET" >&2
    exit 2
    ;;
esac
