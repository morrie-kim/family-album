#!/usr/bin/env bash
# Cloudflare Named Tunnel -> local Spring Boot.
# Prerequisites: Java 24, cloudflared, a Named Tunnel with
# Published Application Route: api.<domain> -> http://localhost:8080
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

: "${CLOUDFLARE_TUNNEL_TOKEN:?Set CLOUDFLARE_TUNNEL_TOKEN in .env}"
: "${APP_BASE_URL:?Set APP_BASE_URL, e.g. https://api.example.com}"

command -v cloudflared >/dev/null 2>&1 || {
  echo "cloudflared is not installed. macOS: brew install cloudflared" >&2
  exit 1
}
command -v java >/dev/null 2>&1 || {
  echo "Java 24 is required." >&2
  exit 1
}

PORT="${PORT:-8080}"
export PORT
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod,cloudflare-tunnel}"
export PHOTO_STORAGE_ROOT="${PHOTO_STORAGE_ROOT:-./data/photos}"
# Keep local demo data on disk. Override DB_URL explicitly to use PostgreSQL.
export DB_URL="${DB_URL:-jdbc:h2:file:./data/albumdb;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE}"
export DB_USERNAME="${DB_USERNAME:-sa}"
export DB_PASSWORD="${DB_PASSWORD:-}"
mkdir -p ./data ./data/photos

echo "Starting Cloudflare Tunnel for ${APP_BASE_URL} -> http://localhost:${PORT}"
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
  echo "cloudflared exited during startup. Check the tunnel token and route." >&2
  exit 1
fi

echo "Starting Spring Boot with profiles: $SPRING_PROFILES_ACTIVE"
echo "Public API/app URL: $APP_BASE_URL"
echo "Google OAuth callback (if enabled): $APP_BASE_URL/google/callback"
./gradlew --no-daemon bootRun
