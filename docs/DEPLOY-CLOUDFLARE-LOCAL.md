# 로컬 Spring Boot + Cloudflare Tunnel

이 문서는 현재 가족앨범의 **서버 렌더링(Thymeleaf) 구조를 유지한 채** 로컬 Spring Boot 전체를 Cloudflare Tunnel로 공개하는 구성입니다.

> 중요: 현재 화면은 Thymeleaf 템플릿(`src/main/resources/templates`)에서 서버가 HTML을 생성합니다. 따라서 이 단계에서는 프론트엔드 HTML 자체가 Cloudflare Pages에서 실행되는 구조가 아닙니다. Cloudflare Tunnel이 화면과 API 요청을 모두 로컬 Spring Boot로 전달합니다. Pages에 정적 파일만 올리는 것으로는 로그인/앨범/폴더/월드컵 화면을 분리할 수 없습니다. Pages 프론트엔드 완전 분리는 Thymeleaf 화면을 별도 SPA/정적 프론트엔드로 전환하고 필요한 REST API를 추가하는 별도 리팩터링이 필요합니다.

## 1. 구성

- 로컬 Spring Boot: `http://localhost:8080`
- Cloudflare Named Tunnel: `https://api.example.com` → `http://localhost:8080`
- 데이터베이스 기본값: 로컬 H2 파일 DB(`./data/albumdb`)
- 사진 파일 기본 경로: `./data/photos`

H2와 사진은 로컬 PC에 저장되므로 PC의 디스크 백업이 필요합니다. PC가 종료되거나 절전 상태가 되면 서비스에 접근할 수 없습니다.

## 2. 사전 준비

1. Cloudflare에 도메인을 등록하고 DNS를 Cloudflare에서 관리합니다.
2. Cloudflare Dashboard → **Networks → Tunnels**에서 Named Tunnel을 생성합니다.
3. Tunnel의 Published Application Route를 추가합니다.
   - Subdomain: `api`
   - Domain: 본인 도메인(예: `example.com`)
   - Service type: `HTTP`
   - URL: `localhost:8080`
4. Tunnel Token을 복사합니다. Token은 비밀번호와 동일하게 취급하고 Git에 커밋하지 않습니다.
5. macOS: `brew install cloudflared`
6. Java 24가 설치되어 있어야 합니다.

## 3. 환경변수

```bash
cp .env.example .env
```

`.env`에 아래 값을 설정합니다.

```dotenv
CLOUDFLARE_TUNNEL_TOKEN=실제_Tunnel_Token
APP_BASE_URL=https://api.example.com
ALLOW_NEW_FAMILY=true
```

Google Photos Picker를 사용한다면 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_TOKEN_KEY`도 설정하고 Google Cloud Console의 승인된 리디렉션 URI에 `https://api.example.com/google/callback`을 등록합니다.

최초 가족 계정을 만든 뒤에는 `ALLOW_NEW_FAMILY=false`로 변경합니다.

## 4. 실행

저장소 루트에서 실행합니다.

```bash
chmod +x scripts/cloudflare-tunnel-up.sh
./scripts/cloudflare-tunnel-up.sh
```

스크립트는 Tunnel을 실행한 다음 `prod,cloudflare-tunnel` 프로파일로 Spring Boot를 시작합니다. 기본 H2 DB와 사진 파일은 `./data` 아래에 저장됩니다. PostgreSQL을 사용할 경우 `.env`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 지정하면 됩니다.

종료할 때는 `Ctrl+C`를 누릅니다. 스크립트가 Tunnel 프로세스를 종료합니다.

## 5. 확인

```bash
curl -I https://api.example.com/login
```

로그인 페이지 응답이 오는지 확인합니다. 이 구성에서 브라우저의 페이지와 API는 모두 동일한 Spring Boot 서버에서 제공됩니다.

## 6. Cloudflare Pages 프론트엔드 분리 계획

현재 앱은 Thymeleaf 기반의 세션 로그인/폼 전송/서버 렌더링 화면을 사용하고, 일부 REST API만 `/api/**`로 제공하고 있습니다. Pages에서 프론트엔드를 완전히 제공하려면 다음 작업이 선행되어야 합니다.

1. Thymeleaf 화면과 폼 흐름을 정적 HTML/JavaScript 앱으로 전환
2. 폴더, 사진, 가족, 월드컵, Google Picker 기능을 위한 REST API 보완
3. 세션 또는 토큰 인증, CORS, CSRF, 쿠키 정책 재설계
4. 정적 프론트엔드를 Pages에 배포하고 API만 Tunnel의 `api.example.com`으로 호출
5. 프론트/백엔드 통합 테스트 및 Google OAuth redirect URI 갱신

이 전환 전에는 Pages 배포만으로 현재 화면 기능을 보존할 수 없습니다.
