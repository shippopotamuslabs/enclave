#!/usr/bin/env bash
# Run RustRover headless code inspections and print a human/agent-readable summary.
#
# Requires RustRover on PATH or RUSTROVER_HOME. Close the RustRover GUI first —
# the CLI inspector cannot run while an IDE window is open on this project.
#
# Usage:
#   ./scripts/rustrover-inspect.sh [options]
#
# Options:
#   --scope SCOPE          rust-workspace (default), desktop, or all
#   --changes              Inspect only local uncommitted changes
#   --path DIR             Override scope with a single subdirectory
#   --format FORMAT        Report format: json (default), xml, or plain
#   --output DIR           Directory for inspection artifacts (default: build/rustrover-inspections)
#   --profile PATH         Inspection profile XML (auto-detected by default)
#   --rustrover PATH       Path to rustrover.sh (auto-detected by default)
#   --verbosity LEVEL      -v0, -v1, or -v2 (default: -v2)
#   --print-raw            Dump every report file to stdout after the run
#   --fail-on LEVEL        Exit 1 if issues at or above LEVEL are found (ERROR, WARNING, WEAK WARNING)
#   --help                 Show this help text
#
# Scopes:
#   rust-workspace  desktop/ + third_party/{agent_core,classifier_core,docgen_core}
#   desktop         desktop app only (fastest)
#   all             entire monorepo incl. Kotlin, vendored C++, HTML, etc. (slow/noisy)
#
# Examples:
#   ./scripts/rustrover-inspect.sh --changes --scope desktop --fail-on WARNING
#   ./scripts/rustrover-inspect.sh --scope rust-workspace
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SCOPE_CHANGES=0
SCOPE="rust-workspace"
SCOPE_PATH=""
FORMAT="json"
OUTPUT_DIR="$PROJECT_ROOT/build/rustrover-inspections"
PROFILE_PATH=""
RUSTROVER_BIN=""
VERBOSITY="-v2"
PRINT_RAW=0
FAIL_ON=""

usage() {
  sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//'
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --scope)
      [[ $# -ge 2 ]] || die "--scope requires rust-workspace, desktop, or all"
      SCOPE="$2"
      shift
      ;;
    --changes) SCOPE_CHANGES=1 ;;
    --path)
      [[ $# -ge 2 ]] || die "--path requires a directory argument"
      SCOPE_PATH="$2"
      shift
      ;;
    --format)
      [[ $# -ge 2 ]] || die "--format requires json, xml, or plain"
      FORMAT="$2"
      shift
      ;;
    --output)
      [[ $# -ge 2 ]] || die "--output requires a directory argument"
      OUTPUT_DIR="$2"
      shift
      ;;
    --profile)
      [[ $# -ge 2 ]] || die "--profile requires a file path"
      PROFILE_PATH="$2"
      shift
      ;;
    --rustrover)
      [[ $# -ge 2 ]] || die "--rustrover requires a path to rustrover.sh"
      RUSTROVER_BIN="$2"
      shift
      ;;
    --verbosity)
      [[ $# -ge 2 ]] || die "--verbosity requires -v0, -v1, or -v2"
      VERBOSITY="$2"
      shift
      ;;
    --print-raw) PRINT_RAW=1 ;;
    --fail-on)
      [[ $# -ge 2 ]] || die "--fail-on requires ERROR, WARNING, or WEAK WARNING"
      FAIL_ON="$2"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1"
      ;;
  esac
  shift
done

case "$FORMAT" in
  json|xml|plain) ;;
  *) die "Unsupported format: $FORMAT (expected json, xml, or plain)" ;;
esac

case "$SCOPE" in
  rust-workspace|desktop|all) ;;
  *) die "Unsupported scope: $SCOPE (expected rust-workspace, desktop, or all)" ;;
esac

case "$VERBOSITY" in
  -v0|-v1|-v2) ;;
  *) die "Unsupported verbosity: $VERBOSITY (expected -v0, -v1, or -v2)" ;;
esac

if [[ -n "$FAIL_ON" ]]; then
  case "$FAIL_ON" in
    ERROR|WARNING|"WEAK WARNING") ;;
    *) die "Unsupported --fail-on level: $FAIL_ON" ;;
  esac
fi

resolve_rustrover_bin() {
  if [[ -n "$RUSTROVER_BIN" ]]; then
    [[ -x "$RUSTROVER_BIN" ]] || die "RustRover launcher is not executable: $RUSTROVER_BIN"
    echo "$RUSTROVER_BIN"
    return 0
  fi

  if [[ -n "${RUSTROVER_HOME:-}" && -x "$RUSTROVER_HOME/bin/rustrover.sh" ]]; then
    echo "$RUSTROVER_HOME/bin/rustrover.sh"
    return 0
  fi

  if command -v rustrover.sh >/dev/null 2>&1; then
    command -v rustrover.sh
    return 0
  fi

  local candidate
  for candidate in "$HOME"/opt/RustRover-*/bin/rustrover.sh; do
    if [[ -x "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done

  die "Could not find rustrover.sh. Set RUSTROVER_HOME or pass --rustrover /path/to/rustrover.sh"
}

resolve_profile_path() {
  if [[ -n "$PROFILE_PATH" ]]; then
    [[ -f "$PROFILE_PATH" ]] || die "Inspection profile not found: $PROFILE_PATH"
    echo "$PROFILE_PATH"
    return 0
  fi

  local idea_profile="$PROJECT_ROOT/.idea/inspectionProfiles/Project_Default.xml"
  if [[ -f "$idea_profile" ]]; then
    echo "$idea_profile"
    return 0
  fi

  local tracked_profile="$PROJECT_ROOT/config/rustrover/inspectionProfiles/Project_Default.xml"
  [[ -f "$tracked_profile" ]] || die "Inspection profile not found at $tracked_profile"
  echo "$tracked_profile"
}

assert_rustrover_not_running() {
  if pgrep -af '[R]ustRover' >/dev/null 2>&1; then
    die "RustRover is already running. Close the IDE window, then rerun this script."
  fi
}

resolve_scope_paths() {
  if [[ -n "$SCOPE_PATH" ]]; then
    if [[ "$SCOPE_PATH" != /* ]]; then
      SCOPE_PATH="$PROJECT_ROOT/$SCOPE_PATH"
    fi
    [[ -d "$SCOPE_PATH" ]] || die "Scope path is not a directory: $SCOPE_PATH"
    echo "$SCOPE_PATH"
    return 0
  fi

  case "$SCOPE" in
    desktop)
      echo "$PROJECT_ROOT/desktop"
      ;;
    all)
      echo "$PROJECT_ROOT"
      ;;
    rust-workspace)
      local rel_paths=(
        desktop
        third_party/agent_core
        third_party/classifier_core
        third_party/docgen_core
      )
      local rel abs_paths=()
      for rel in "${rel_paths[@]}"; do
        abs_paths+=("$PROJECT_ROOT/$rel")
      done
      printf '%s\n' "${abs_paths[@]}"
      ;;
  esac
}

describe_scope() {
  if [[ -n "$SCOPE_PATH" ]]; then
    echo "custom: $SCOPE_PATH"
    return 0
  fi
  case "$SCOPE" in
    rust-workspace) echo "rust workspace (desktop + agent/classifier/docgen cores)" ;;
    desktop) echo "desktop/" ;;
    all) echo "entire monorepo (slow/noisy)" ;;
  esac
}

run_inspect() {
  local scope_path="$1"
  local scope_output="$2"

  mkdir -p "$scope_output"

  local inspect_args=(
    "$PROJECT_ROOT"
    "$PROFILE_PATH"
    "$scope_output"
    "-format" "$FORMAT"
    "$VERBOSITY"
  )

  if [[ "$SCOPE_CHANGES" -eq 1 ]]; then
    inspect_args+=("-changes")
  fi

  if [[ "$scope_path" != "$PROJECT_ROOT" ]]; then
    inspect_args+=("-d" "$scope_path")
  fi

  echo ""
  echo "==> Inspecting ${scope_path#$PROJECT_ROOT/}..."
  "$RUSTROVER_BIN" inspect "${inspect_args[@]}"
}

RUSTROVER_BIN="$(resolve_rustrover_bin)"
PROFILE_PATH="$(resolve_profile_path)"

mapfile -t SCOPE_PATHS < <(resolve_scope_paths)

mkdir -p "$OUTPUT_DIR"
rm -rf "${OUTPUT_DIR:?}/"*

echo "RustRover inspection"
echo "  launcher : $RUSTROVER_BIN"
echo "  project  : $PROJECT_ROOT"
echo "  profile  : $PROFILE_PATH"
echo "  output   : $OUTPUT_DIR"
echo "  format   : $FORMAT"
echo "  scope    : $(describe_scope)"
echo "  changes  : $([[ "$SCOPE_CHANGES" -eq 1 ]] && echo yes || echo no)"

assert_rustrover_not_running

echo ""
echo "==> Running RustRover inspect (first run may take several minutes)..."

if [[ "${#SCOPE_PATHS[@]}" -eq 1 ]]; then
  run_inspect "${SCOPE_PATHS[0]}" "$OUTPUT_DIR"
else
  for scope_path in "${SCOPE_PATHS[@]}"; do
    scope_label="${scope_path#$PROJECT_ROOT/}"
    scope_label="${scope_label//\//-}"
    run_inspect "$scope_path" "$OUTPUT_DIR/scopes/$scope_label"
  done
fi

mapfile -t report_files < <(find "$OUTPUT_DIR" -type f | sort)
if [[ "${#report_files[@]}" -eq 0 ]]; then
  echo ""
  echo "Inspection finished, but no report files were written under $OUTPUT_DIR"
  exit 0
fi

echo ""
echo "==> Wrote ${#report_files[@]} report file(s) under $OUTPUT_DIR"

if [[ "$PRINT_RAW" -eq 1 ]]; then
  echo ""
  echo "==> Raw report output"
  for report in "${report_files[@]}"; do
    echo ""
    echo "--- $report ---"
    cat "$report"
  done
fi

if command -v python3 >/dev/null 2>&1; then
  SUMMARY_FILE="$(mktemp)"
  set +e
  FAIL_ON="$FAIL_ON" python3 - "$OUTPUT_DIR" "$FORMAT" "$SUMMARY_FILE" <<'PY'
import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

output_dir = Path(sys.argv[1])
report_format = sys.argv[2]
summary_file = Path(sys.argv[3])
fail_on = os.environ.get("FAIL_ON", "")
lines = []

SEVERITY_RANK = {
    "ERROR": 4,
    "WARNING": 3,
    "WEAK WARNING": 2,
    "INFO": 1,
    "TYPO": 1,
}

def should_count(level: str) -> bool:
    if not fail_on:
        return True
    return SEVERITY_RANK.get(level, 0) >= SEVERITY_RANK.get(fail_on, 0)

issues = []

def add_issue(level, file_path, line, description, source):
    issues.append(
        {
            "level": level,
            "file": file_path,
            "line": line,
            "description": description,
            "source": source,
        }
    )

for path in sorted(output_dir.rglob("*")):
    if not path.is_file():
        continue

    suffix = path.suffix.lower()
    if report_format == "json" and suffix == ".json":
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue

        def walk(node, source=path.name):
            if isinstance(node, dict):
                level = node.get("severity") or node.get("level") or node.get("type")
                description = node.get("description") or node.get("msg") or node.get("problem")
                file_path = node.get("file") or node.get("filePath") or node.get("path")
                line = node.get("line") or node.get("offset") or node.get("lineNumber")
                if description and (file_path or level):
                    add_issue(str(level or "UNKNOWN"), str(file_path or ""), str(line or ""), str(description), source)
                for value in node.values():
                    walk(value, source)
            elif isinstance(node, list):
                for item in node:
                    walk(item, source)

        walk(payload)
        continue

    if suffix == ".xml":
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue

        for problem in root.iter("problem"):
            level = (problem.findtext("severity") or problem.findtext("level") or "UNKNOWN").strip()
            description = (problem.findtext("description") or problem.findtext("msg") or "").strip()
            file_path = (problem.findtext("file") or problem.findtext("filePath") or "").strip()
            line = (problem.findtext("line") or problem.findtext("offset") or "").strip()
            if description:
                add_issue(level, file_path, line, description, path.name)

if issues:
    lines.append("==> Inspection findings")
    for issue in issues:
        location = issue["file"]
        if issue["line"]:
            location = f"{location}:{issue['line']}"
        lines.append(f"[{issue['level']}] {location} -- {issue['description']} ({issue['source']})")

counts = {}
for issue in issues:
    level = issue["level"]
    if should_count(level):
        counts[level] = counts.get(level, 0) + 1

if counts:
    lines.append("")
    lines.append(
        "Summary: "
        + ", ".join(
            f"{level}={counts[level]}"
            for level in sorted(counts, key=lambda k: SEVERITY_RANK.get(k, 0), reverse=True)
        )
    )
else:
    lines.append("")
    lines.append("No parsed inspection findings.")

summary_file.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
if fail_on and counts:
    sys.exit(1)
PY
  python_status=$?
  set -e
  cat "$SUMMARY_FILE"
  rm -f "$SUMMARY_FILE"
  if [[ "$python_status" -ne 0 ]]; then
    exit "$python_status"
  fi
else
  echo ""
  echo "Install python3 to get a parsed summary. Report files:"
  printf '  %s\n' "${report_files[@]}"
fi
