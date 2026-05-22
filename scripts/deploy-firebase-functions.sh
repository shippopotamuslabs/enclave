#!/usr/bin/env bash
# Deploy Enclave Firebase Cloud Functions (and their predeploy TypeScript build).
#
# Usage:
#   ./scripts/deploy-firebase-functions.sh              # deploy all functions
#   ./scripts/deploy-firebase-functions.sh --dry-run    # print firebase command only
#
# Requires: firebase CLI on PATH, npm, and project login (`firebase login`).
# Target project is read from .firebaserc (projects.default).
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

if [[ ! -f .firebaserc ]]; then
    echo "ERROR: .firebaserc not found in $PROJECT_ROOT" >&2
    exit 1
fi

FIREBASE_PROJECT="$(
    sed -n 's/.*"default"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' .firebaserc | head -1
)"

if [[ -z "$FIREBASE_PROJECT" ]]; then
    echo "ERROR: Could not read projects.default from .firebaserc" >&2
    exit 1
fi

if [[ "$FIREBASE_PROJECT" == "pocket-crew-491212" ]]; then
    echo "ERROR: .firebaserc still points at deprecated project pocket-crew-491212." >&2
    echo "       Update projects.default to enclave-497101 (or your Enclave Firebase project ID)." >&2
    exit 1
fi

if [[ ! -f firebase.json ]]; then
    echo "ERROR: firebase.json not found in $PROJECT_ROOT" >&2
    exit 1
fi

if [[ ! -d functions ]]; then
    echo "ERROR: functions/ directory not found." >&2
    exit 1
fi

echo "==> Target Firebase project: $FIREBASE_PROJECT"

echo "==> Installing functions dependencies (if needed)..."
if [[ ! -d functions/node_modules ]]; then
    npm --prefix functions install
fi

echo "==> Building functions TypeScript..."
npm --prefix functions run build

DEPLOY_CMD=(firebase deploy --only functions --project "$FIREBASE_PROJECT")

if $DRY_RUN; then
    echo "Dry run — would execute: ${DEPLOY_CMD[*]}"
    exit 0
fi

echo "==> Deploying Firebase functions..."
if ! "${DEPLOY_CMD[@]}"; then
    echo "ERROR: Firebase functions deploy failed. See output above." >&2
    echo "       Common fixes for a new project:" >&2
    echo "         1. Create Firestore (Native) in Firebase Console → Firestore → Create database" >&2
    echo "         2. Upgrade to Blaze if deploying scheduled/trigger functions" >&2
    echo "         3. Retry after IAM/API propagation (1–2 minutes)" >&2
    exit 1
fi

echo "==> Deploy complete (project: $FIREBASE_PROJECT)."
