# GitHub Actions + VPS 자동 배포

이 저장소는 Java 24 / Spring Boot 앱과 PostgreSQL을 Docker Compose로 실행하고 Cloudflare Tunnel로 외부에 공개합니다.

## GitHub Actions

.github/workflows/deploy.yml은 main push 때 다음을 수행합니다.

1. Temurin Java 24 설치
2. ./gradlew clean test bootJar 실행
3. Dockerfile로 Java 24 기반 이미지 빌드
4. GHCR(ghcr.io)에 커밋 SHA 태그로 이미지 push
5. SSH로 VPS 접속 후 새 이미지 pull 및 Compose 재기동

Cloudflare Workers Builds에서 ./gradlew clean bootJar를 실행하지 마세요. 이 저장소의 Spring Boot JVM은 VPS에서 실행합니다.

## GitHub Repository Secrets

Settings → Secrets and variables → Actions에 다음 값을 등록합니다.

| Secret | 설명 |
|---|---|
| VPS_HOST | VPS 공인 IP 또는 SSH 호스트 |
| VPS_USER | SSH 사용자 |
| VPS_SSH_KEY | VPS 사용자에 등록된 SSH private key |
| VPS_SSH_PORT | SSH 포트 (생략 시 22) |
| GHCR_USERNAME | GitHub 사용자명 |
| GHCR_PULL_TOKEN | GHCR 패키지에 대한 read:packages 권한이 있는 PAT |

이미지 push는 Actions의 GITHUB_TOKEN을 사용합니다. VPS에서 private GHCR 이미지를 pull하기 위해 별도의 read-only PAT를 사용합니다.

## VPS 최초 준비

Ubuntu 예시:

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
mkdir -p ~/family-album
```

저장소의 docker-compose.yml을 VPS의 ~/family-album/docker-compose.yml에 한 번 복사합니다. 그리고 .env.example을 참고해 ~/family-album/.env를 직접 생성하고 운영값을 채웁니다. 최소 필수값은 다음과 같습니다.

- POSTGRES_PASSWORD: 충분히 긴 임의 비밀번호
- CLOUDFLARE_TUNNEL_TOKEN: Cloudflare Tunnel 토큰
- APP_BASE_URL: 실제 HTTPS 공개 주소 (예: https://album.example.com)

Google OAuth 및 이미지 검색을 사용한다면 관련 환경변수도 설정합니다. .env는 절대 Git에 커밋하지 마세요.

Cloudflare Tunnel의 Public Hostname은 HTTP / http://app:8080으로 설정하고 Tunnel 토큰은 VPS .env에 보관합니다.

## 배포

필수 GitHub Secrets와 VPS의 Compose/.env 준비가 끝나면 main에 push합니다. Actions에서 Build and Deploy Family Album (Java 24) 실행 결과를 확인합니다.

VPS에서 상태/로그 확인:

```bash
cd ~/family-album
docker compose --profile named ps
docker compose --profile named logs -f app
docker compose --profile named logs -f cloudflared
```

현재 배포는 기존 PostgreSQL 및 사진 볼륨을 유지하면서 앱 컨테이너를 교체합니다. 무중단 배포는 아니며, 데이터베이스 스키마 변경 전에는 백업을 권장합니다.

## Cloudflare 대시보드 설정 (Free 플랜)

이 저장소는 Cloudflare Workers/Pages 에 직접 배포하는 프로젝트가 아닙니다. Spring Boot 는 Java 24 런타임이 있는 Docker 컨테이너에서 실행해야 하며, Cloudflare Free 는 VPS 앞단의 Tunnel 로 사용합니다.

과거에 Workers Builds 에 이 GitHub 저장소를 연결하고 Deploy command 를 `npx wrangler deploy` 로 지정했다면 다음과 같이 정리하세요.

1. Cloudflare 대시보드의 **Workers & Pages → 해당 Worker → Settings → Builds** 로 이동합니다.
2. 이 저장소에 연결된 Workers Builds 자동 배포를 비활성화하거나 Git 연결을 해제합니다. 특히 Deploy command `npx wrangler deploy` 를 실행하지 않도록 합니다.
3. Cloudflare Tunnel 의 Public Hostname 은 VPS 앱으로 연결합니다: **HTTP → `http://app:8080`** (Tunnel 을 Docker Compose 의 `cloudflared` 서비스로 실행하는 경우).
4. 앱 빌드/이미지 배포는 GitHub Actions 의 `.github/workflows/deploy.yml` 이 담당합니다. GHCR 이미지 빌드는 main push 시 실행되며, VPS 자동 재기동은 Repository Variable `ENABLE_VPS_DEPLOY=true` 일 때만 실행됩니다.

> `wrangler.jsonc` 를 다시 추가하거나 `assets.directory` 를 지정하는 방식은 이 문제의 해결책이 아닙니다. 정적 파일 배포 오류만 없앨 뿐 Spring Boot 서버를 실행하지 않습니다.
