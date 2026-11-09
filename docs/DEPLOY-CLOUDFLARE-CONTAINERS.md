# Cloudflare Containers 배포 안내 (중단)

Cloudflare Free 플랜에서는 이 저장소의 Spring Boot 서버를 Cloudflare Containers 로 배포할 수 없습니다. 따라서 Containers 전용 Worker 설정과 `npx wrangler deploy` 방식은 제거했습니다.

## 권장 배포 경로

- Spring Boot 3.5 / Java 24 앱: VPS 의 Docker Compose 컨테이너에서 실행
- 데이터베이스: PostgreSQL 컨테이너(영구 볼륨)
- 사진: VPS 의 Docker 볼륨(영구 저장)
- 외부 HTTPS 접근: Cloudflare Tunnel (Free 플랜 사용 가능)
- 이미지 빌드/배포: GitHub Actions → GHCR → VPS SSH 배포

설정과 최초 배포 절차는 [DEPLOY-CLOUDFLARE.md](DEPLOY-CLOUDFLARE.md) 를 참고하세요.

## 기존 Workers Builds 연결 정리

Workers Builds 에서 이 저장소를 연결해 둔 경우 해당 Worker 의 **Settings → Builds** 에서 Git 연결을 해제하거나 자동 배포를 비활성화하세요. Deploy command 에 `npx wrangler deploy` 를 남겨두면 정적 파일 또는 Worker 설정을 찾으려 하므로 이 프로젝트의 배포 경로로 사용하지 마세요.
