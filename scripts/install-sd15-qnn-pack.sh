#!/usr/bin/env bash
# Installs a Local Dream–compatible SD 1.5 QNN model pack next to the catalog GGUF sentinel.
# Catalog SHA for stable-diffusion-v1-5-Q4_0.gguf stays unchanged; generation uses QNN .bin files.
#
# Usage:
#   ./scripts/install-sd15-qnn-pack.sh [MODELS_DIR]
#
# Default MODELS_DIR: Android external files models dir on a connected device is not used here;
# pass the host path that mirrors .../files/models/ (e.g. adb-pulled or emulator sync).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS_DIR="${1:-$ROOT/.local-models}"

# ChilloutMix SD1.5 QNN2.28 — adjust zip name for your SoC (8gen3 / 8gen2 / 8gen1).
ZIP_URL="${SD15_QNN_ZIP_URL:-https://huggingface.co/xororz/sd-qnn/resolve/main/AnythingV5_qnn2.28_8gen2.zip}"
PACK_DIR="$MODELS_DIR/sd15-qnn"

mkdir -p "$MODELS_DIR"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading SD 1.5 QNN pack..."
curl -fL --retry 3 -o "$TMP/sd15-qnn.zip" "$ZIP_URL"
unzip -q -o "$TMP/sd15-qnn.zip" -d "$TMP/extract"

# Zip root may be a single folder; flatten to PACK_DIR.
SRC="$TMP/extract"
if [[ $(find "$SRC" -mindepth 1 -maxdepth 1 -type d | wc -l) -eq 1 ]]; then
  SRC="$(find "$SRC" -mindepth 1 -maxdepth 1 -type d | head -1)"
fi

rm -rf "$PACK_DIR"
mkdir -p "$PACK_DIR"
cp -a "$SRC/." "$PACK_DIR/"

for f in clip_v2.mnn token_emb.bin pos_emb.bin unet.bin vae_decoder.bin tokenizer.json; do
  if [[ ! -f "$PACK_DIR/$f" ]]; then
    echo "Missing $f in $PACK_DIR — wrong zip or SoC variant?" >&2
    exit 1
  fi
done

echo "Installed QNN pack at $PACK_DIR"
echo "On device, mirror under: Android/data/<package>/files/models/sd15-qnn/"
echo "Keep stable-diffusion-v1-5-Q4_0.gguf in files/models/ for catalog SHA verification."
