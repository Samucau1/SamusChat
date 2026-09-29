#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# The workflow sends its short-lived token on stdin, never in command arguments.
# Keep the server user's existing Docker credentials untouched.
DOCKER_CONFIG="$(mktemp -d)"
export DOCKER_CONFIG
cleanup() {
    rm -f "$DOCKER_CONFIG/config.json"
    rmdir "$DOCKER_CONFIG" || true
}
trap cleanup EXIT
docker login ghcr.io --username "${1:?Missing GitHub actor}" --password-stdin
bash scripts/deploy.sh
