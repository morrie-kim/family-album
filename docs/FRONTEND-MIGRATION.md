# Cloudflare Pages 프론트엔드 전환

## 마이그레이션 원칙

기존 Thymeleaf 화면은 이전 기간 동안 유지합니다. 신규 Pages SPA는 별도 `frontend/` 디렉터리에서 개발하며, 기능별 API 전환과 회귀 테스트가 완료되기 전에는 기존 화면을 제거하지 않습니다.

## API 인증

- `GET /api/auth/csrf`: CSRF 토큰 쿠키 발급 및 헤더 이름 반환
- `POST /api/auth/login`: 이메일/비밀번호 인증 후 서버 세션 생성
- `POST /api/auth/signup`: 회원가입
- `GET /api/auth/me`: 로그인 사용자
- `POST /api/auth/logout`: 세션 종료

브라우저 요청은 `credentials: "include"`를 사용해야 합니다. 변경 요청에는 `X-XSRF-TOKEN` 헤더를 보내야 합니다. Pages와 API는 동일한 등록 가능 도메인의 HTTPS 서브도메인을 사용해야 브라우저의 SameSite 쿠키 정책과 호환됩니다.

## 기능별 API

- 가족/폴더/사진: 기존 `/api/family`, `/api/folders`, `/api/photos`
- 월드컵: `/api/worldcups/**`
- 인터넷 이미지 검색: `/api/images/search?q=...`
- Google Photos: `/api/google/**`

## 배포 환경변수

백엔드:
- `FRONTEND_ORIGIN=https://www.example.com`
- `FRONTEND_URL=https://www.example.com`
- `APP_BASE_URL=https://api.example.com`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_TOKEN_KEY` (선택)

Cloudflare Pages:
- `VITE_API_BASE_URL` 같은 빌드 변수를 쓰지 않는 순수 정적 앱이면 `frontend/config.js`의 API URL을 배포 환경에 맞게 지정합니다.

## 중요

Google OAuth callback은 `https://api.example.com/google/callback`을 사용합니다. 기존 콜백은 서버 세션의 OAuth state를 검증하므로 API 도메인 세션 쿠키가 브라우저에 유지되어야 합니다.

사진 업로드는 Cloudflare 및 Tunnel 요청 본문 제한을 고려해 배치를 유지합니다. 사진 원본은 Spring Boot의 설정된 영속 스토리지에 저장됩니다.
