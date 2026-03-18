#!/bin/bash
# Build script for KleidiAI Fat APK with dual SVE/NEON libraries
# This script builds both library variants and prepares them for bundling

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LLAMA_ANDROID_DIR="$SCRIPT_DIR/llama-android"
JNI_LIBS_DIR="$LLAMA_ANDROID_DIR/src/main/jniLibs/arm64-v8a"
BUILD_OUTPUT_DIR="$LLAMA_ANDROID_DIR/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a"

echo "=========================================="
echo "KleidiAI Fat APK Build Script"
echo "=========================================="

# Create output directory
mkdir -p "$JNI_LIBS_DIR"

# Clean previous builds
rm -f "$JNI_LIBS_DIR"/*.so

# Build NEON variant (SVE disabled - for Tensor G3 / Pixel 8)
echo ""
echo "[1/2] Building NEON variant (SVE disabled)..."
./gradlew :llama-android:assembleRelease -PCMAKE_ARGS="-DGGML_CPU_ARM_ENABLE_SVE=OFF" --no-daemon -q

NEON_SRC="$BUILD_OUTPUT_DIR/libllama-jni-neon.so"
if [ -f "$NEON_SRC" ]; then
    cp "$NEON_SRC" "$JNI_LIBS_DIR/libllama-jni-neon.so"
    echo "      -> libllama-jni-neon.so"
else
    echo "ERROR: NEON build failed - file not found: $NEON_SRC"
    exit 1
fi

# Build SVE variant (SVE enabled - for Samsung / Snapdragon)
echo ""
echo "[2/2] Building SVE variant (SVE enabled)..."
./gradlew :llama-android:assembleRelease -PCMAKE_ARGS="-DGGML_CPU_ARM_ENABLE_SVE=ON" --no-daemon -q

# The SVE build also produces libllama-jni-neon.so (renamed by CMake OUTPUT_NAME)
SVE_SRC="$BUILD_OUTPUT_DIR/libllama-jni-neon.so"
if [ -f "$SVE_SRC" ]; then
    cp "$SVE_SRC" "$JNI_LIBS_DIR/libllama-jni-sve.so"
    echo "      -> libllama-jni-sve.so"
else
    echo "ERROR: SVE build failed - file not found: $SVE_SRC"
    exit 1
fi

echo ""
echo "=========================================="
echo "Build Complete!"
echo "=========================================="
echo ""
echo "Libraries prepared in: $JNI_LIBS_DIR"
ls -lh "$JNI_LIBS_DIR"
echo ""
