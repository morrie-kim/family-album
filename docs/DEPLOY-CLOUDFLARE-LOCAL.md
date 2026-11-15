# Cloudflare Workers + Quick Tunnel 배포 (도메인 구매 없이)

## 목표 구조

- 프론트엔드: `https://family-album.nhacker7k.workers.dev`
- API: 동일한 Worker Origin의 `/api/*`
- Google OAuth callback: `https://family-album.nhacker7k.workers.dev/google/callback`
- API 백엔드: 로컬 Spring Boot `http://127.0.0.1:8080`
- 백엔드 공개 연결: Cloudflare Quick Tunnel의 임시 `*.trycloudflare.com` 주소
- DB/사진: 로컬 `./data` 디렉터리

Worker가 정적 SPA를 제공하고 `/api/*` 및 OAuth callback만 Quick Tunnel을 통해 로컬 Spring Boot로 프록시합니다. 브라우저는 항상 Worker 주소만 호출하므로 세션/CSRF 쿠키가 동일 Origin에 유지됩니다.

> **중요:** Quick Tunnel은 개발·테스트용 임시 터널입니다. URL은 실행할 때마다 바뀌고, 실행 중인 Mac이 꺼지거나 스크립트를 종료하면 서비스가 중단됩니다. Cloudflare도 운영 트래픽에는 안정적인 Named Tunnel을 권장합니다. 현재 별도 도메인이 없으므로 도메인 구매 없이 가능한 테스트 구성을 사용합니다.

## 1. 사전 준비

1. Cloudflare 계정에 Worker `family-album`이 생성되어 있어야 합니다. 주소가 `https://family-album.nhacker7k.workers.dev`인지 확인합니다.
2. macOS에 JDK 24, Node.js/npm, cloudflared를 설치합니다.
   ```bash
   brew install cloudflared
   java -version
   node --version
   cloudflared --version
   ```
3. Cloudflare Dashboard > My Profile > API Tokens에서 토큰을 생성합니다. 권한은 해당 계정의 **Workers Scripts: Edit**가 필요합니다.
4. Cloudflare Dashboard > Workers & Pages > Overview에서 Account ID를 확인합니다.

## 2. 환경변수

저장소 루트에서 실행합니다.

```bash
cp .env.example .env
```

`.env`에 다음 값을 설정합니다.

```dotenv
CLOUDFLARE_API_TOKEN=실제_API_Token
CLOUDFLARE_ACCOUNT_ID=Cloudflare_Account_ID
APP_BASE_URL=https://family-album.nhacker7k.workers.dev
FRONTEND_URL=https://family-album.nhacker7k.workers.dev
FRONTEND_ORIGIN=https://family-album.nhacker7k.workers.dev
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_TOKEN_KEY=
ALLOW_NEW_FAMILY=true
```

API Token과 Google Secret은 GitHub나 채팅에 올리지 마세요. `.env`는 Git에서 제외됩니다.

## 3. Google Photos OAuth 설정 (선택)

Google Cloud Console의 OAuth 클라이언트 ID(웹 애플리케이션)에서 승인된 리디렉션 URI를 다음과 정확히 일치시킵니다.

```text
https://family-album.nhacker7k.workers.dev/google/callback
```

Google Photos Picker API 활성화, OAuth 동의 화면 및 테스트 사용자 설정도 필요합니다. `GOOGLE_TOKEN_KEY`는 `openssl rand -base64 32`로 생성합니다.

## 4. 실행 및 배포

```bash
chmod +x scripts/cloudflare-worker-up.sh
./scripts/cloudflare-worker-up.sh
```

스크립트가 다음을 자동 수행합니다.

1. `/api/auth/csrf`를 먼저 확인해 이미 실행 중인 Spring Boot가 있으면 해당 서버를 재사용합니다. 없으면 로컬 Spring Boot를 `prod,cloudflare-tunnel` 프로파일로 실행합니다.
2. 백엔드 준비 상태를 확인합니다. 지정 포트가 다른 프로세스에 점유되어 있으면 중복 실행하지 않고 종료합니다.
3. `cloudflared tunnel --url http://127.0.0.1:8080`으로 Quick Tunnel을 시작합니다.
4. 발급된 임시 `trycloudflare.com` 주소를 읽어 `BACKEND_ORIGIN` 변수로 Worker를 배포합니다.
5. Worker는 SPA 정적 파일을 제공하고 API/OAuth callback 요청을 로컬 백엔드로 전달합니다.

> H2 파일 DB는 기본적으로 한 프로세스만 직접 열 수 있습니다. IntelliJ에서 이미 Family Album을 실행했다면 스크립트가 정상 응답 중인 서버를 재사용합니다. 다른 프로세스가 DB를 사용 중인데 백엔드가 정상 응답하지 않는다면 해당 프로세스를 먼저 종료하세요. `albumdb.mv.db` 또는 H2 잠금 파일을 삭제하면 안 됩니다.

실행 중인 터미널을 닫지 마세요. 재시작하면 Quick Tunnel URL이 바뀌며 스크립트가 Worker를 다시 배포합니다.

## 5. 최초 계정 및 보안

1. Worker URL을 열어 첫 가족과 관리자 계정을 생성합니다.
2. 첫 가족 생성 후 `.env`의 `ALLOW_NEW_FAMILY=false`로 변경하고 스크립트를 재시작합니다.
3. 초대 코드 가입만 허용되는지 확인합니다.
4. 가족 계정으로 로그인해 사진 업로드/열람/삭제, 폴더 권한, 가족 초대, 월드컵 기능을 점검합니다.

## 6. 쿠키와 프록시 동작

- 프론트엔드 API Base URL은 빈 문자열(상대 경로)입니다.
- Worker는 `/api/*`와 `/google/callback`만 백엔드로 프록시합니다.
- 브라우저의 `JSESSIONID` 및 CSRF 쿠키를 백엔드로 전달하고, 백엔드의 `Set-Cookie` 응답은 Worker Origin으로 그대로 반환합니다.
- `APP_BASE_URL`, `FRONTEND_URL`, `FRONTEND_ORIGIN`은 모두 Worker Origin으로 같아야 합니다.
- Google OAuth callback도 Worker Origin으로 들어온 뒤 백엔드에 전달됩니다.

## 운영상 한계

- Quick Tunnel은 임시 URL이며 SLA/고정 주소를 제공하지 않습니다. URL이 바뀔 때마다 Worker를 재배포해야 합니다.
- Quick Tunnel 주소를 아는 사람은 로컬 백엔드에 직접 접근할 수 있습니다. 계정 인증을 유지하고 관리자 계정을 보호하세요.
- Mac이 켜져 있고 네트워크에 연결되어 있어야 합니다. 절전/종료 시 API가 중단됩니다.
- H2 DB와 사진 파일은 로컬 디스크에 있습니다. `./data`를 별도 저장장치에 정기 백업하세요.
- 가족 사진 등 실제 데이터를 장기간 보관하는 운영 환경에는 이 구성을 권장하지 않습니다. 고정 도메인과 Named Tunnel 또는 상시 운영 서버로 이전하는 것이 안전합니다.
