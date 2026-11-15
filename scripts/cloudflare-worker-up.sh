#!/usr/bin/env bash
# Run Spring Boot locally, create a temporary Quick Tunnel, then deploy the Worker
# with that tunnel URL as BACKEND_ORIGIN. The Worker serves the SPA and proxies API.
set -Eeuo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source ./.env
  set +a
fi

: "${CLOUDFLARE_API_TOKEN:?Set CLOUDFLARE_API_TOKEN in .env}"
: "${CLOUDFLARE_ACCOUNT_ID:?Set CLOUDFLARE_ACCOUNT_ID in .env}"
: "${APP_BASE_URL:?Set APP_BASE_URL in .env}"
: "${FRONTEND_URL:?Set FRONTEND_URL in .env}"
: "${FRONTEND_ORIGIN:?Set FRONTEND_ORIGIN in .env}"

if [[ "$APP_BASE_URL" != "$FRONTEND_URL" || "$APP_BASE_URL" != "$FRONTEND_ORIGIN" ]]; then
  echo "APP_BASE_URL, FRONTEND_URL and FRONTEND_ORIGIN must be the same Worker origin." >&2
  exit 1
fi
if [[ "$APP_BASE_URL" != "https://family-album.nhacker7k.workers.dev" ]]; then
  echo "APP_BASE_URL must match the existing Worker URL: https://family-album.nhacker7k.workers.dev" >&2
  exit 1
fi

for cmd in java cloudflared curl npx; do
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "$cmd is required. macOS: brew install cloudflared (install JDK 24 and Node.js separately)." >&2
    exit 1
  }
done

export PORT="${PORT:-8080}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod,cloudflare-tunnel}"
export PHOTO_STORAGE_ROOT="${PHOTO_STORAGE_ROOT:-./data/photos}"
export DB_URL="${DB_URL:-jdbc:h2:file:./data/albumdb;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE}"
export DB_USERNAME="${DB_USERNAME:-sa}"
export DB_PASSWORD="${DB_PASSWORD:-}"
export ALLOW_NEW_FAMILY="${ALLOW_NEW_FAMILY:-true}"

mkdir -p ./data ./data/photos
APP_LOG="./data/family-album.log"
TUNNEL_LOG="./data/cloudflared-quick-tunnel.log"
APP_PID=""
TUNNEL_PID=""

backend_healthy() {
  curl -fsS "http://127.0.0.1:$PORT/api/auth/csrf" >/dev/null 2>&1
}

port_in_use() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v nc >/dev/null 2>&1; then
    nc -z 127.0.0.1 "$PORT" >/dev/null 2>&1
  else
    return 1
  fi
}

cleanup() {
  echo "Stopping services started by this script..."
  [[ -n "$TUNNEL_PID" ]] && kill "$TUNNEL_PID" 2>/dev/null || true
  [[ -n "$APP_PID" ]] && kill "$APP_PID" 2>/dev/null || true
  [[ -n "$TUNNEL_PID" ]] && wait "$TUNNEL_PID" 2>/dev/null || true
  [[ -n "$APP_PID" ]] && wait "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Reuse an already-running local backend (for example, one launched by IntelliJ).
# Starting a second embedded-H2 process against the same MVStore file causes error 90020.
if backend_healthy; then
  echo "Reusing the healthy Spring Boot server on port $PORT."
elif port_in_use; then
  echo "Port $PORT is already occupied, but /api/auth/csrf is not healthy." >&2
  echo "Stop the other process or configure PORT to a free port, then retry." >&2
  exit 1
else
  echo "Starting Spring Boot (Java 24)..."
  ./gradlew --no-daemon bootRun >"$APP_LOG" 2>&1 &
  APP_PID=$!

  echo "Waiting for Spring Boot health endpoint..."
  for _ in $(seq 1 90); do
    if backend_healthy; then
      break
    fi
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      if grep -Eqi 'Database may be already in use|The file is locked|90020' "$APP_LOG"; then
        echo "H2 database is locked by another process. Do not delete albumdb.mv.db or its lock files." >&2
        echo "Stop the other Family Album/Spring Boot process (IntelliJ or another terminal), then retry." >&2
      fi
      echo "Spring Boot exited. Recent log:" >&2
      tail -n 80 "$APP_LOG" >&2
      exit 1
    fi
    sleep 2
  done
  if ! backend_healthy; then
    echo "Spring Boot did not become healthy. Check $APP_LOG" >&2
    exit 1
  fi
fi

echo "Starting temporary Cloudflare Quick Tunnel..."
cloudflared tunnel --url "http://127.0.0.1:$PORT" --no-autoupdate >"$TUNNEL_LOG" 2>&1 &
TUNNEL_PID=$!

BACKEND_ORIGIN=""
for _ in $(seq 1 60); do
  BACKEND_ORIGIN="$(grep -Eo 'https://[[:alnum:]-]+\.trycloudflare\.com' "$TUNNEL_LOG" | head -n 1 || true)"
  [[ -n "$BACKEND_ORIGIN" ]] && break
  if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
    echo "Quick Tunnel exited. Recent log:" >&2
    tail -n 80 "$TUNNEL_LOG" >&2
    exit 1
  fi
  sleep 2
done

if [[ -z "$BACKEND_ORIGIN" ]]; then
  echo "Could not read the Quick Tunnel URL. Check $TUNNEL_LOG" >&2
  exit 1
fi

echo "Deploying Worker with the temporary backend origin..."
export CLOUDFLARE_API_TOKEN CLOUDFLARE_ACCOUNT_ID
npx --yes wrangler@4 deploy --var "BACKEND_ORIGIN:$BACKEND_ORIGIN"

echo
echo "Family Album is available at: $APP_BASE_URL"
echo "Backend tunnel: $BACKEND_ORIGIN"
echo "Keep this terminal and your Mac running. Quick Tunnel URL changes after restart."
echo "Logs: $APP_LOG and $TUNNEL_LOG"
wait "$APP_PID"
