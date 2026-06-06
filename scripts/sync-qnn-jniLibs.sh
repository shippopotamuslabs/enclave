#!/usr/bin/env bash
# Copies Qualcomm QNN runtime libraries into local-image-gen-android jniLibs (primary)
# and assets/qnnlibs (legacy fallback).
# libcdsprpc.so is NOT copied — it must load from vendor via uses-native-library + System.loadLibrary.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNILIBS_DIR="$ROOT/local-image-gen-android/src/main/jniLibs/arm64-v8a"
ASSETS_DIR="$ROOT/local-image-gen-android/src/main/assets/qnnlibs"

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
  echo "QNN SDK not found. Set QNN_SDK_ROOT or qnn.sdk.dir in local.properties." >&2
  exit 1
}

ANDROID_LIB="$SDK/lib/aarch64-android"
if [[ ! -d "$ANDROID_LIB" ]]; then
  echo "Missing $ANDROID_LIB (expected QAIRT Android libs)." >&2
  exit 1
fi

mkdir -p "$JNILIBS_DIR" "$ASSETS_DIR"

install_so() {
  local src="$1"
  if [[ ! -f "$src" ]]; then
    return 0
  fi
  cp -f "$src" "$JNILIBS_DIR/"
  cp -f "$src" "$ASSETS_DIR/"
}

copy_android() {
  for name in libQnnHtp.so libQnnSystem.so libQnnHtpPrepare.so \
    libQnnHtpV68Stub.so libQnnHtpV69Stub.so libQnnHtpV73Stub.so \
    libQnnHtpV75Stub.so libQnnHtpV79Stub.so libQnnHtpV81Stub.so; do
    install_so "$ANDROID_LIB/$name"
  done
}

copy_hexagon() {
  local pair
  for pair in \
    "hexagon-v68:libQnnHtpV68.so:libQnnHtpV68Skel.so" \
    "hexagon-v69:libQnnHtpV69.so:libQnnHtpV69Skel.so" \
    "hexagon-v73:libQnnHtpV73.so:libQnnHtpV73Skel.so" \
    "hexagon-v75:libQnnHtpV75.so:libQnnHtpV75Skel.so" \
    "hexagon-v79:libQnnHtpV79.so:libQnnHtpV79Skel.so" \
    "hexagon-v81:libQnnHtpV81.so:libQnnHtpV81Skel.so"; do
    IFS=: read -r folder lib1 lib2 <<<"$pair"
    local unsigned="$SDK/lib/$folder/unsigned"
    if [[ -d "$unsigned" ]]; then
      install_so "$unsigned/$lib1"
      install_so "$unsigned/$lib2"
    fi
  done
}

remove_bundled_cdsp_rpc() {
  rm -f "$JNILIBS_DIR/libcdsprpc.so" "$ASSETS_DIR/libcdsprpc.so"
}

copy_android
copy_hexagon
remove_bundled_cdsp_rpc

chmod 0755 "$JNILIBS_DIR"/*.so 2>/dev/null || true
chmod 0755 "$ASSETS_DIR"/*.so 2>/dev/null || true

echo "Synced QNN libs from $SDK into $JNILIBS_DIR (primary) and $ASSETS_DIR (fallback)."
