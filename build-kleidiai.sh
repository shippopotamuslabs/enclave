#!/bin/bash
# Build script for KleidiAI Fat APK with dual SVE/NEON libraries
#
# Usage: ./build-kleidiai.sh
#
# This script is OPTIONAL for development builds.
# For day-to-day development in Android Studio, just build normally.
# Run this script before creating a release/production build to ensure
# both SVE and NEON variants are included for optimal CPU detection.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LLAMA_ANDROID_DIR="$SCRIPT_DIR/llama-android"
JNI_LIBS_DIR="$LLAMA_ANDROID_DIR/src/main/jniLibs/arm64-v8a"

# Resolve ANDROID_HOME
ANDROID_HOME="${ANDROID_HOME:-$(grep "sdk.dir" "$SCRIPT_DIR/local.properties" | cut -d'=' -f2 | tr -d ' ')}"
if [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: Cannot find ANDROID_HOME"
    exit 1
fi

NDK_VERSION="27.0.11718014"
NDK_LIB_DIR="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android"
KLEIDIAI_DIR="$SCRIPT_DIR/third_party/kleidiai-1.22.0"

echo "=========================================="
echo "KleidiAI Dual Variant Build"
echo "=========================================="

# =============================================================================
# STEP 1: Setup - Copy libc++_shared.so from NDK
# =============================================================================
echo ""
echo "[1/3] Copying libc++_shared.so from NDK..."
mkdir -p "$JNI_LIBS_DIR"
cp "$NDK_LIB_DIR/libc++_shared.so" "$JNI_LIBS_DIR/"
echo "      -> libc++_shared.so"

# =============================================================================
# STEP 2: Build SVE variant and copy to jniLibs
# =============================================================================
echo ""
echo "[2/3] Building SVE variant..."
rm -rf "$LLAMA_ANDROID_DIR/.cxx"
rm -rf "$LLAMA_ANDROID_DIR/build"

./gradlew :llama-android:assembleDebug \
    -PCMAKE_ARGS="-DGGML_CPU_ARM_ENABLE_SVE=ON;-DFETCHCONTENT_SOURCE_DIR_KLEIDIAI_DOWNLOAD=$KLEIDIAI_DIR" \
    --no-daemon -q

SVE_LIB=$(find "$LLAMA_ANDROID_DIR/build" -name "libllama-jni-neon.so" -type f 2>/dev/null | head -1)
if [ -n "$SVE_LIB" ]; then
    cp "$SVE_LIB" "$JNI_LIBS_DIR/libllama-jni-sve.so"
    echo "      -> libllama-jni-sve.so"
else
    echo "ERROR: SVE library not found"
    exit 1
fi

# =============================================================================
# STEP 3: Build app (CMake builds NEON variant)
# =============================================================================
echo ""
echo "[3/3] Building app with NEON variant..."
rm -rf "$LLAMA_ANDROID_DIR/.cxx"
rm -rf "$LLAMA_ANDROID_DIR/build"

./gradlew :app:assembleDebug --no-daemon -q
echo "      -> App built"

echo ""
echo "=========================================="
echo "Build Complete!"
echo "=========================================="
echo ""
echo "Libraries in $JNI_LIBS_DIR:"
ls -lh "$JNI_LIBS_DIR"
echo ""
echo "Next: APK is ready at app/build/outputs/apk/debug/app-debug.apk"
