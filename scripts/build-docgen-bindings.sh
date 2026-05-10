#!/bin/bash
set -e

# Configuration
NDK_VERSION="27.0.11718014"
ANDROID_API=34
TARGET_ARCH="aarch64-linux-android"

# Resolve paths
# SCRIPT_DIR is pocket-crew/scripts
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# PROJECT_ROOT is pocket-crew
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# RUST_ROOT is now the submodule path inside pocket-crew
RUST_ROOT="$PROJECT_ROOT/third_party/docgen_core"

ANDROID_HOME="${ANDROID_HOME:-$(grep "sdk.dir" "$PROJECT_ROOT/local.properties" | cut -d'=' -f2 | tr -d ' ')}"

if [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: Cannot find ANDROID_HOME. Ensure local.properties exists in $PROJECT_ROOT"
    exit 1
fi

TOOLCHAIN="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TOOLCHAIN/bin:$PATH"

# Set environment variables for cargo
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/bin/${TARGET_ARCH}${ANDROID_API}-clang"
export AR="$TOOLCHAIN/bin/llvm-ar"

echo "Building docgen_core for $TARGET_ARCH from submodule $RUST_ROOT..."

# Build Rust library
cd "$RUST_ROOT"
cargo build --release --target "$TARGET_ARCH"

echo "Generating Kotlin bindings..."

# Generate Kotlin bindings using the config in the Android project
# uniffi-bindgen is a binary in the docgen_core crate
cargo run --release --bin uniffi-bindgen generate \
    --library "$RUST_ROOT/target/$TARGET_ARCH/release/libdocgen_core.so" \
    --language kotlin \
    --out-dir "$PROJECT_ROOT/core/docgen/src/main/kotlin" \
    --config "$PROJECT_ROOT/core/docgen/uniffi.toml"

# Copy .so to jniLibs
echo "Copying library to jniLibs..."
mkdir -p "$PROJECT_ROOT/core/docgen/src/main/jniLibs/arm64-v8a"
cp "$RUST_ROOT/target/$TARGET_ARCH/release/libdocgen_core.so" "$PROJECT_ROOT/core/docgen/src/main/jniLibs/arm64-v8a/"

echo "Android build and binding generation complete."
