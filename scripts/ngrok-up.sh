#!/usr/bin/env bash
# ngrok 터널을 먼저 열고, 그 공개 주소(APP_BASE_URL)로 앱을 기동한다.
# 사용법: cp .env.example .env 후 값 채우고  ./scripts/ngrok-up.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then set -a; . ./.env; set +a; fi

: "${NGROK_DOMAIN:?NGROK_DOMAIN 이 필요합니다 (.env 참고)}"
PORT="${PORT:-8080}"
command -v ngrok >/dev/null || { echo "ngrok 이 설치되어 있지 않습니다. https://ngrok.com/download"; exit 1; }

echo "▶ ngrok 터널 시작: https://${NGROK_DOMAIN} → localhost:${PORT}"
# ngrok v3.16+ 는 --url, 구버전은 --domain 을 사용합니다.
ngrok http "$PORT" --url="$NGROK_DOMAIN" --log=stdout > /tmp/family-album-ngrok.log 2>&1 &
NGROK_PID=$!
trap 'kill "$NGROK_PID" 2>/dev/null || true' EXIT

for i in $(seq 1 15); do
  if curl -fs http://127.0.0.1:4040/api/tunnels | grep -q "$NGROK_DOMAIN"; then break; fi
  if ! kill -0 "$NGROK_PID" 2>/dev/null; then echo "ngrok 실행 실패:"; tail -20 /tmp/family-album-ngrok.log; exit 1; fi
  sleep 1
done

export APP_BASE_URL="https://${NGROK_DOMAIN}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-ngrok}"

echo "▶ 공개 주소: ${APP_BASE_URL}"
echo "▶ 구글 콘솔 '승인된 리디렉션 URI' 에 등록: ${APP_BASE_URL}/google/callback"
if [ -x ./gradlew ]; then ./gradlew bootRun; else gradle bootRun; fi
