#!/usr/bin/env bash
# Start a local Spring Boot instance and expose it through a Cloudflare Named Tunnel.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source ./.env
  set +a
fi

: "${CLOUDFLARE_TUNNEL_TOKEN:?Set CLOUDFLARE_TUNNEL_TOKEN in .env}"
: "${APP_BASE_URL:?Set APP_BASE_URL, e.g. https://api.example.com}"
: "${FRONTEND_ORIGIN:?Set FRONTEND_ORIGIN, e.g. https://www.example.com}"
: "${FRONTEND_URL:?Set FRONTEND_URL, e.g. https://www.example.com}"

command -v cloudflared >/dev/null 2>&1 || {
  echo "cloudflared is not installed. macOS: brew install cloudflared" >&2
  exit 1
}
command -v java >/dev/null 2>&1 || {
  echo "Java 24 is required. Install JDK 24 and retry." >&2
  exit 1
}

export PORT="${PORT:-8080}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod,cloudflare-tunnel}"
export PHOTO_STORAGE_ROOT="${PHOTO_STORAGE_ROOT:-./data/photos}"
export DB_URL="${DB_URL:-jdbc:h2:file:./data/albumdb;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE}"
export DB_USERNAME="${DB_USERNAME:-sa}"
export DB_PASSWORD="${DB_PASSWORD:-}"
export ALLOW_NEW_FAMILY="${ALLOW_NEW_FAMILY:-true}"
mkdir -p ./data ./data/photos

echo "Starting Cloudflare Tunnel: ${APP_BASE_URL} -> http://localhost:${PORT}"
cloudflared tunnel run --token "$CLOUDFLARE_TUNNEL_TOKEN" &
TUNNEL_PID=$!

cleanup() {
  echo "Stopping Cloudflare Tunnel..."
  kill "$TUNNEL_PID" 2>/dev/null || true
  wait "$TUNNEL_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

sleep 2
if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
  echo "cloudflared exited. Check the Named Tunnel token and Public Hostname route." >&2
  exit 1
fi

echo "Starting Spring Boot. Frontend origin: ${FRONTEND_ORIGIN}"
./gradlew --no-daemon bootRun
