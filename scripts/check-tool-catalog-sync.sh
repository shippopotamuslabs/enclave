#!/bin/bash
# AGENT-038 — ensure every Kotlin ToolDefinition name exists in Rust tool_catalog.json
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TOOL_DEF="$ROOT/core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ToolDefinition.kt"
CATALOG="$ROOT/third_party/agent_core/resources/tool_catalog.json"

if [[ ! -f "$TOOL_DEF" || ! -f "$CATALOG" ]]; then
  echo "ERROR: missing ToolDefinition.kt or tool_catalog.json"
  exit 1
fi

mapfile -t K_NAMES < <(grep -E '^\s*name = "' "$TOOL_DEF" | sed -E 's/.*name = "([^"]+)".*/\1/' | sort -u)
mapfile -t R_NAMES < <(jq -r '.tools[].name' "$CATALOG" | sort -u)

MISSING=()
for k in "${K_NAMES[@]}"; do
  if ! printf '%s\n' "${R_NAMES[@]}" | grep -qx "$k"; then
    MISSING+=("$k")
  fi
done

if ((${#MISSING[@]} > 0)); then
  echo "ERROR: Kotlin tools missing from Rust catalog:"
  printf '  - %s\n' "${MISSING[@]}"
  exit 1
fi

echo "OK: all Kotlin ToolDefinition names are present in tool_catalog.json (${#K_NAMES[@]} tools)."
