#!/usr/bin/env bash
# Backward-compatible entry point. The no-domain Worker setup replaces the old
# Named Tunnel + Pages configuration.
set -euo pipefail
cd "$(dirname "$0")/.."
echo "cloudflare-tunnel-up.sh is deprecated; starting the Worker + Quick Tunnel flow."
exec ./scripts/cloudflare-worker-up.sh "$@"
