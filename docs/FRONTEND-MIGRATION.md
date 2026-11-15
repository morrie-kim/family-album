# Cloudflare Worker 프론트엔드 전환

## 마이그레이션 원칙

기존 Thymeleaf 화면은 이전 기간 동안 유지합니다. 신규 SPA는 별도 `frontend/` 디렉터리에서 제공하며, 기존 서버 화면은 롤백 및 호환성을 위해 유지합니다.

## API 인증

- `GET /api/auth/csrf`: CSRF 토큰 쿠키 발급 및 헤더 이름 반환
- `POST /api/auth/login`: 이메일/비밀번호 인증 후 서버 세션 생성
- `POST /api/auth/signup`: 회원가입
- `GET /api/auth/me`: 로그인 사용자
- `POST /api/auth/logout`: 세션 종료

브라우저 요청은 `credentials: "include"`를 사용하고 변경 요청에는 CSRF 헤더를 전달합니다. Worker가 프론트엔드와 API를 동일한 Origin으로 제공하므로 별도 CORS/third-party cookie 구성이 필요하지 않습니다.

## 기능별 API

- 가족/폴더/사진: 기존 `/api/family`, `/api/folders`, `/api/photos`
- 월드컵: `/api/worldcups/**`
- 인터넷 이미지 검색: `/api/images/search?q=...`
- Google Photos: `/api/google/**`

## 배포 환경변수

백엔드:
- `FRONTEND_ORIGIN=https://family-album.nhacker7k.workers.dev`
- `FRONTEND_URL=https://family-album.nhacker7k.workers.dev`
- `APP_BASE_URL=https://family-album.nhacker7k.workers.dev`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_TOKEN_KEY` (선택)

Cloudflare Worker:
- `BACKEND_ORIGIN`: 실행 중인 Quick Tunnel의 `https://<random>.trycloudflare.com` 주소. 실행 스크립트가 자동 감지해 Worker 배포 변수로 설정합니다.

## Google OAuth

승인된 리디렉션 URI는 다음과 같습니다.

```text
https://family-album.nhacker7k.workers.dev/google/callback
```

OAuth state는 백엔드 세션에서 검증합니다. Worker가 브라우저 세션 쿠키를 백엔드로 전달하므로 callback에서도 같은 세션을 사용합니다.

## 배포 문서

도메인 구매 없이 사용하는 구성과 실행 순서는 [DEPLOY-CLOUDFLARE-LOCAL.md](DEPLOY-CLOUDFLARE-LOCAL.md)를 참고하세요. Quick Tunnel은 임시 개발/테스트 연결이므로 상시 운영에는 고정 도메인과 Named Tunnel 또는 상시 서버를 권장합니다.
