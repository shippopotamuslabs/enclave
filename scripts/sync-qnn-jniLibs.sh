#!/usr/bin/env bash
# Copies Qualcomm QNN runtime libraries into local-image-gen-android assets/qnnlibs.
# QNN libs are loaded at runtime via dlopen from the app-private dir that
# QnnRuntimeBootstrap copies them to — they must NOT also live in jniLibs/
# (that duplicates ~221 MB of .so files into lib/arm64-v8a/ in the final APK).
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
  rm -f "$ASSETS_DIR/libcdsprpc.so"
}

remove_stale_jnilibs_copies() {
  # Remove QNN .so files left in jniLibs by older versions of this script
  # (they duplicated ~221 MB into lib/arm64-v8a/ in the APK).
  local removed=0
  for f in libQnnHtp.so libQnnSystem.so libQnnHtpPrepare.so \
    libQnnHtpV68.so libQnnHtpV68Skel.so libQnnHtpV68Stub.so \
    libQnnHtpV69.so libQnnHtpV69Skel.so libQnnHtpV69Stub.so \
    libQnnHtpV73.so libQnnHtpV73Skel.so libQnnHtpV73Stub.so \
    libQnnHtpV75.so libQnnHtpV75Skel.so libQnnHtpV75Stub.so \
    libQnnHtpV79.so libQnnHtpV79Skel.so libQnnHtpV79Stub.so \
    libQnnHtpV81.so libQnnHtpV81Skel.so libQnnHtpV81Stub.so; do
    if [[ -f "$JNILIBS_DIR/$f" ]]; then
      rm -f "$JNILIBS_DIR/$f"
      removed=$((removed + 1))
    fi
  done
  if [[ $removed -gt 0 ]]; then
    echo "Removed $removed stale QNN .so file(s) from jniLibs (deduplicated)."
  fi
}

copy_android
copy_hexagon
remove_bundled_cdsp_rpc
remove_stale_jnilibs_copies

chmod 0755 "$ASSETS_DIR"/*.so 2>/dev/null || true

echo "Synced QNN libs from $SDK into $ASSETS_DIR."
