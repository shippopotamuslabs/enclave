#!/usr/bin/env bash
# Deploy Pocket Crew Firebase Cloud Functions (and their predeploy TypeScript build).
#
# Usage:
#   ./scripts/deploy-firebase-functions.sh              # deploy all functions
#   ./scripts/deploy-firebase-functions.sh --dry-run  # print firebase command only
#
# Requires: firebase CLI on PATH, npm, and project login (`firebase login`).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DRY_RUN=false
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        -h | --help)
            echo "Usage: $0 [--dry-run]"
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg" >&2
            exit 1
            ;;
    esac
done

if ! command -v firebase >/dev/null 2>&1; then
    echo "ERROR: firebase CLI not found on PATH. Install: npm install -g firebase-tools" >&2
    exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
    echo "ERROR: npm not found on PATH." >&2
    exit 1
fi

cd "$PROJECT_ROOT"

if [[ ! -f firebase.json ]]; then
    echo "ERROR: firebase.json not found in $PROJECT_ROOT" >&2
    exit 1
fi

if [[ ! -d functions ]]; then
    echo "ERROR: functions/ directory not found." >&2
    exit 1
fi

echo "==> Installing functions dependencies (if needed)..."
if [[ ! -d functions/node_modules ]]; then
    npm --prefix functions install
fi

echo "==> Building functions TypeScript..."
npm --prefix functions run build

DEPLOY_CMD=(firebase deploy --only functions)

if $DRY_RUN; then
    echo "Dry run — would execute: ${DEPLOY_CMD[*]}"
    exit 0
fi

echo "==> Deploying Firebase functions to project $(firebase use 2>/dev/null | sed -n 's/.*(\(.*\)).*/\1/p' || echo 'default')..."
"${DEPLOY_CMD[@]}"

echo "==> Deploy complete."
