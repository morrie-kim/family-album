# Cloudflare Pages + Cloudflare Tunnel 배포

## 목표 구조

- Frontend: Cloudflare Pages, `https://www.example.com`
- Backend: 로컬 Spring Boot, `http://localhost:8080`
- API 공개 주소: Cloudflare Named Tunnel, `https://api.example.com`
- DB/사진: 로컬 `./data` 디렉터리 (백업 필요)

## 1. Pages 배포

1. Cloudflare Dashboard → Workers & Pages → Create → Pages → Connect to Git.
2. `morrie-kim/family-album` 저장소, `main` 브랜치를 선택합니다.
3. Framework preset은 None으로 설정합니다.
4. Root directory를 `frontend`로 지정합니다.
5. Build command는 비워두고 Build output directory는 `.`로 설정합니다.
6. 프로젝트 생성 후 Custom Domains에서 `www.example.com`을 연결합니다.
7. 저장소의 `frontend/config.js`에서 API 주소를 `https://api.example.com`으로 맞춥니다.

Pages가 `frontend/` 폴더만 정적 배포하므로 Java 소스나 비밀키는 공개되지 않습니다.

## 2. Named Tunnel

1. Cloudflare Dashboard → Zero Trust → Networks → Tunnels에서 Named Tunnel을 생성합니다.
2. macOS에 `brew install cloudflared`로 설치합니다.
3. Tunnel의 Published Application Route를 추가합니다.
   - Hostname: `api.example.com`
   - Service: `http://localhost:8080`
4. Dashboard가 제공하는 Tunnel Token을 복사합니다.

## 3. 로컬 환경변수

```bash
cp .env.example .env
```

`.env`:

```dotenv
CLOUDFLARE_TUNNEL_TOKEN=실제_Tunnel_Token
APP_BASE_URL=https://api.example.com
FRONTEND_URL=https://www.example.com
FRONTEND_ORIGIN=https://www.example.com
ALLOW_NEW_FAMILY=true
```

Google Photos를 사용한다면 Google Cloud Console에 다음 redirect URI를 등록합니다.

```text
https://api.example.com/google/callback
```

첫 가족 관리자 계정을 생성한 뒤에는 `ALLOW_NEW_FAMILY=false`로 변경합니다.

## 4. 실행

```bash
chmod +x scripts/cloudflare-tunnel-up.sh
./scripts/cloudflare-tunnel-up.sh
```

이 명령은 Cloudflare Tunnel과 Spring Boot를 실행합니다. 외부에서 `https://api.example.com/api/auth/csrf`가 응답하는지 확인한 다음 Pages 사이트에서 로그인합니다.

## 5. 인증 및 CORS

SPA API는 HTTP Basic이 아니라 서버 세션 쿠키 인증을 사용합니다.

- API 요청은 `credentials: "include"`로 전송
- `GET /api/auth/csrf`에서 CSRF 토큰 발급
- POST/PUT/DELETE 요청에 응답의 headerName에 해당하는 CSRF 헤더 포함
- 백엔드 `FRONTEND_ORIGIN`은 Pages의 정확한 Origin과 일치해야 합니다.
- Pages와 API는 같은 등록 가능 도메인의 HTTPS 서브도메인으로 운영합니다. `pages.dev`와 별도 도메인을 혼용하면 브라우저의 third-party cookie 정책으로 세션이 차단될 수 있습니다.

## 6. 기존 기능과 전환 범위

기존 Thymeleaf 화면은 롤백 및 호환성을 위해 유지됩니다. Pages SPA에는 로그인/회원가입, 사진 목록·업로드·삭제, 폴더 권한, 가족 정보, Google Photos Picker, 월드컵 생성·진행·랭킹 화면이 포함됩니다. 기능별 API는 기존 서비스 계층의 권한 검증을 재사용합니다.

전환 후에도 실제 사용자 계정으로 다음 시나리오를 검증하세요.
1. 신규 가족 생성 및 초대 코드 가입
2. 폴더 권한 없음/보기/올리기 검증
3. 사진 업로드, 열람, 삭제 권한 검증
4. Google OAuth 연결, Picker 가져오기, 연결 해제
5. 텍스트/앨범 사진/인터넷 이미지 월드컵 생성 및 플레이

## 운영상 주의

- 로컬 PC가 꺼지거나 절전 상태면 API가 중단됩니다.
- H2와 사진 파일은 로컬 디스크에 저장됩니다. PC 백업 및 디스크 장애 대응이 필요합니다.
- Tunnel Token, Google OAuth secret, 암호화 키를 Git에 커밋하지 마세요.
- 로컬 PC를 상시 켜둘 수 없는 운영 환경이라면 동일한 Spring Boot 컨테이너를 VPS/클라우드에 배포하는 것이 좋습니다.
