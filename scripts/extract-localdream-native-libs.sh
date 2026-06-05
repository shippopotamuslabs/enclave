#!/usr/bin/env bash
# Bundles QNN runtime (assets only) + libstable_diffusion_core.so (jniLibs) for :local-image-gen-android.
#
# Preferred (requires Qualcomm QAIRT license):
#   ./scripts/sync-qnn-jniLibs.sh
#   ./scripts/build-stable-diffusion-core.sh
#
# Fallback when qnn.sdk.dir is not set: extract from a published Local Dream APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNI_DIR="$ROOT/local-image-gen-android/src/main/jniLibs/arm64-v8a"
ASSETS_DIR="$ROOT/local-image-gen-android/src/main/assets/qnnlibs"
WORK="${WORK_DIR:-$ROOT/build/extract-localdream-libs}"
APK_URL="${LOCAL_DREAM_APK_URL:-https://github.com/xororz/local-dream/releases/download/v2.6.1/LocalDream_armv8a_2.6.1.apk}"

if bash "$ROOT/scripts/sync-qnn-jniLibs.sh" 2>/dev/null && [[ -f "$JNI_DIR/libstable_diffusion_core.so" ]]; then
  echo "Native libs ready via QNN SDK scripts."
  exit 0
fi

echo "QNN SDK not configured; extracting native libs from Local Dream APK..."
mkdir -p "$WORK" "$JNI_DIR" "$ASSETS_DIR"
curl -fL --retry 3 -o "$WORK/LocalDream.apk" "$APK_URL"
unzip -qo "$WORK/LocalDream.apk" "lib/arm64-v8a/libstable_diffusion_core.so" "assets/qnnlibs/*.so" -d "$WORK"
cp -f "$WORK/lib/arm64-v8a/libstable_diffusion_core.so" "$JNI_DIR/"
chmod +x "$JNI_DIR/libstable_diffusion_core.so"
rm -f "$JNI_DIR"/libQnn*.so
cp -f "$WORK/assets/qnnlibs/"*.so "$ASSETS_DIR/"
echo "Installed libstable_diffusion_core.so into jniLibs and $(ls -1 "$ASSETS_DIR"/*.so | wc -l) QNN libs into assets/qnnlibs only."
echo "Run ./scripts/trim-qnn-assets.sh to shrink assets for a single SoC tier."