#!/bin/bash
set -e

# Configuration (match scripts/build-docgen-bindings.sh)
NDK_VERSION="27.0.11718014"
ANDROID_API=34
TARGET_ARCH="aarch64-linux-android"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RUST_ROOT="$PROJECT_ROOT/third_party/agent_core"

ANDROID_HOME="${ANDROID_HOME:-$(grep '^sdk.dir=' "$PROJECT_ROOT/local.properties" 2>/dev/null | cut -d'=' -f2 | tr -d ' ')}"

if [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: Cannot find ANDROID_HOME. Set ANDROID_HOME or add sdk.dir to local.properties"
    exit 1
fi

TOOLCHAIN="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TOOLCHAIN/bin:$PATH"

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/bin/${TARGET_ARCH}${ANDROID_API}-clang"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384 ${CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS:-}"
export AR="$TOOLCHAIN/bin/llvm-ar"

echo "Building agent_core for $TARGET_ARCH from $RUST_ROOT..."
cd "$RUST_ROOT"
cargo build --release --target "$TARGET_ARCH"

echo "Generating Kotlin bindings..."
mkdir -p "$PROJECT_ROOT/core/agent/src/main/kotlin"
cargo run --release --bin uniffi-bindgen generate \
    --library "$RUST_ROOT/target/$TARGET_ARCH/release/libagent_core.so" \
    --language kotlin \
    --out-dir "$PROJECT_ROOT/core/agent/src/main/kotlin" \
    --config "$PROJECT_ROOT/core/agent/uniffi.toml" \
    --no-format

echo "Copying library to jniLibs..."
mkdir -p "$PROJECT_ROOT/core/agent/src/main/jniLibs/arm64-v8a"
cp "$RUST_ROOT/target/$TARGET_ARCH/release/libagent_core.so" \
    "$PROJECT_ROOT/core/agent/src/main/jniLibs/arm64-v8a/"

echo "Agent bindings build complete."
