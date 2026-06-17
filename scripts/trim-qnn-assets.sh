#!/usr/bin/env bash
# Keeps only tier-appropriate QNN libs in assets/qnnlibs (see QnnRuntimeLibraryCatalog).
# Usage: TIER=gen3_plus ./scripts/trim-qnn-assets.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS_DIR="$ROOT/local-image-gen-android/src/main/assets/qnnlibs"
TIER="${TIER:-gen1_plus}"

keep=()
case "$TIER" in
  gen3_plus)
    keep=(
      libQnnHtp.so libQnnSystem.so
      libQnnHtpV73Stub.so libQnnHtpV73.so libQnnHtpV73Skel.so
      libQnnHtpV79Stub.so libQnnHtpV79.so libQnnHtpV79Skel.so
      libQnnHtpV81Stub.so libQnnHtpV81.so libQnnHtpV81Skel.so
    )
    ;;
  gen1_plus)
    keep=(
      libQnnHtp.so libQnnSystem.so
      libQnnHtpV73Stub.so libQnnHtpV73.so libQnnHtpV73Skel.so
      libQnnHtpV75Stub.so libQnnHtpV75.so libQnnHtpV75Skel.so
      libQnnHtpV79Stub.so libQnnHtpV79.so libQnnHtpV79Skel.so
    )
    ;;
  *)
    keep=(
      libQnnHtp.so libQnnSystem.so
      libQnnHtpV69Stub.so libQnnHtpV69.so libQnnHtpV69Skel.so
      libQnnHtpV73Stub.so libQnnHtpV73.so libQnnHtpV73Skel.so
    )
    ;;
esac

for f in "$ASSETS_DIR"/*.so; do
  base="$(basename "$f")"
  found=0
  for k in "${keep[@]}"; do
    if [[ "$base" == "$k" ]]; then found=1; break; fi
  done
  if [[ $found -eq 0 ]]; then
    rm -f "$f"
    echo "Removed $base"
  fi
done
echo "Trimmed assets for tier=$TIER; kept ${#keep[@]} libraries."