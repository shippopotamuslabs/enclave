#!/usr/bin/env bash
# Build/install debug APK, optionally push T5 title assets, run harness via adb.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROMPT="${1:-hello}"
APP_ID="com.browntowndev.enclave"
STAGING_DIR="/data/local/tmp/titlegen"
DIST_DIR="$ROOT/build/chat_title_generator_dist"

echo "Building debug APK..."
./gradlew :app:assembleDebug --no-configuration-cache

APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
echo "Installing debug APK..."
adb install -r "$APP_APK" || {
  echo "Install failed. If signatures mismatch, run: adb uninstall $APP_ID"
  exit 1
}

MODEL="$DIST_DIR/model_q4.onnx"
MODEL_DATA="$DIST_DIR/model_q4.onnx_data"
TOKENIZER="$DIST_DIR/tokenizer.json"

if [[ -f "$MODEL" && -f "$MODEL_DATA" && -f "$TOKENIZER" ]]; then
  echo "Staging LFM2.5 title generator assets on device (app copies into sandbox)..."
  adb shell "rm -rf /storage/emulated/0/Android/data/${APP_ID}/files/models"
  adb shell "mkdir -p $STAGING_DIR"
  adb push "$TOKENIZER" "$STAGING_DIR/tokenizer.json"
  adb push "$MODEL" "$STAGING_DIR/model_q4.onnx"
  adb push "$MODEL_DATA" "$STAGING_DIR/model_q4.onnx_data"
else
  echo "WARN: $DIST_DIR LFM2.5 assets missing; harness will only work if models are already staged on device"
fi

echo "Running harness (prompt=$PROMPT)..."
adb logcat -c
adb shell am start -W -n "$APP_ID/.titlegen.TitleGenHarnessActivity" \
  -e prompt "$PROMPT" >/dev/null
sleep 120

echo
echo "Harness logcat:"
adb logcat -d -s TitleGenHarness:I TitleGenHarness:W OnnxChatTitleGen:D OnnxChatTitleGen:W GenerateChatTitle:D