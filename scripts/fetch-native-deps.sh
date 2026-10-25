#!/usr/bin/env bash
# Fetch the pinned native source trees the llama build needs.
#
# Neither tree is a submodule and neither is committed (third_party/* is
# gitignored apart from the submodule carve-outs):
#
#   third_party/llama.cpp          ggml-org/llama.cpp @ b8241
#                                  (62b8143ad217fc7d3404119a9440be4a18ce31e0)
#                                  plus the tracked I8MM runtime patch in
#                                  llama-android/patches/
#   third_party/kleidiai-1.22.0    ARM-software/kleidiai @ v1.22.0
#                                  (573736b97bec7d15dc112db2d68a260c3e1737d8)
#
# The pins are the trees the committed SVE library in
# llama-android/src/main/jniLibs/ was built from (ENC-210). Re-running this
# script is a no-op when both trees already match their pins: stamp files
# in each tree record the fetched revision and, for llama.cpp, the patch.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

LLAMA_COMMIT="62b8143ad217fc7d3404119a9440be4a18ce31e0"
LLAMA_TARBALL="https://codeload.github.com/ggml-org/llama.cpp/tar.gz/${LLAMA_COMMIT}"
LLAMA_DIR="$ROOT/third_party/llama.cpp"
LLAMA_PATCH="$ROOT/llama-android/patches/llama.cpp-b8241-i8mm-runtime.patch"
LLAMA_STAMP="$LLAMA_DIR/.enclave-fetch-stamp"

KLEIDIAI_COMMIT="573736b97bec7d15dc112db2d68a260c3e1737d8"
KLEIDIAI_TARBALL="https://codeload.github.com/ARM-software/kleidiai/tar.gz/${KLEIDIAI_COMMIT}"
KLEIDIAI_DIR="$ROOT/third_party/kleidiai-1.22.0"
KLEIDIAI_STAMP="$KLEIDIAI_DIR/.enclave-fetch-stamp"

fetch_tree() {
    # $1 tarball URL, $2 dest dir, $3 stamp file, $4 stamp content
    local url="$1" dest="$2" stamp="$3" want="$4"
    if [[ -f "$stamp" ]] && grep -qx "$want" "$stamp"; then
        echo "OK (already pinned): $dest"
        return 0
    fi
    echo "Fetching $url"
    rm -rf "$dest"
    mkdir -p "$dest"
    curl -fL --retry 3 "$url" | tar -xz -C "$dest" --strip-components=1
    printf '%s\n' "$want" > "$stamp"
    echo "Installed: $dest"
}

fetch_tree "$LLAMA_TARBALL" "$LLAMA_DIR" "$LLAMA_STAMP" "llama.cpp $LLAMA_COMMIT"

# The I8MM runtime patch is the only local modification to upstream
# llama.cpp. Without it the fetched tree is plain upstream b8241 and does
# not match the tree the committed SVE library was built from.
if [[ ! -f "$LLAMA_PATCH" ]]; then
    echo "Missing tracked patch: $LLAMA_PATCH" >&2
    echo "It is produced from the donor working tree; see llama-android/patches/README.md." >&2
    exit 1
fi
if ! grep -qx "patch llama.cpp-b8241-i8mm-runtime" "$LLAMA_STAMP"; then
    echo "Applying $LLAMA_PATCH"
    (cd "$LLAMA_DIR" && patch -p1 --forward < "$LLAMA_PATCH")
    echo "patch llama.cpp-b8241-i8mm-runtime" >> "$LLAMA_STAMP"
fi

fetch_tree "$KLEIDIAI_TARBALL" "$KLEIDIAI_DIR" "$KLEIDIAI_STAMP" "kleidiai $KLEIDIAI_COMMIT"

# stb_image.h is a hard dependency of the diffusion engine: image_io.cpp
# includes it unconditionally, and engine/CMakeLists.txt puts
# engine/thirdparty/stb on the include path when the directory exists.
# The rest of engine/thirdparty (MNN, tokenizers-cpp, xtensor) stays a
# manual checkout: those deps are optional (the build degrades with
# warnings and stubs without them) and their pins are recorded nowhere.
STB_COMMIT="003501f6d7a8668ce8b6d875e34dd97ad0d5d5b5"
STB_DIR="$ROOT/local-image-gen-android/src/main/cpp/engine/thirdparty/stb"
if [[ -f "$STB_DIR/stb_image.h" ]]; then
    echo "OK (already present): $STB_DIR/stb_image.h"
else
    echo "Fetching stb_image.h (nothings/stb @ $STB_COMMIT, v2.30)"
    mkdir -p "$STB_DIR"
    curl -fL --retry 3 -o "$STB_DIR/stb_image.h" \
        "https://raw.githubusercontent.com/nothings/stb/${STB_COMMIT}/stb_image.h"
    echo "Installed: $STB_DIR/stb_image.h"
fi

echo "Native source trees ready:"
echo "  $LLAMA_DIR"
echo "  $KLEIDIAI_DIR"
echo "  $STB_DIR/stb_image.h"
