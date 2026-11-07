# Cloudflare 로 배포하기

## 먼저 알아둘 점
- 가족앨범은 **Java 24 / Spring Boot 상시 서버**(DB + 사진 파일 저장)입니다. **Cloudflare Workers(V8 격리 환경)에는 JVM 서버를 그대로 올릴 수 없습니다.**
- Cloudflare Containers 는 Dockerfile 로 Java 앱도 실행할 수 있지만, ① Workers 유료 플랜이 필요하고 ② **컨테이너 디스크가 휘발**(잠들었다 깨면 이미지 상태로 초기화)합니다. 현재 구조(로컬 디스크 사진 저장, H2/로컬 DB)로는 데이터가 사라지므로 외부 PostgreSQL + 객체 저장소(R2 등) 연동 코드가 먼저 필요합니다(아래 “Containers 로 가려면”).
- 그래서 이 저장소는 **Docker Compose(앱 + PostgreSQL) + Cloudflare Tunnel** 구성을 제공합니다. 앱은 내 서버/PC 의 Docker 에서 돌고(데이터는 볼륨에 영속), Cloudflare 가 HTTPS 공개 주소와 보호를 담당합니다. 포트 개방·공인 IP 가 필요 없습니다.

## 준비물
- 항상 켜져 있는 PC/NAS/VPS 와 Docker (Compose v2)
- (고정 주소) Cloudflare 계정과 Cloudflare 에 등록한 도메인
- (임시 주소) 계정 없이도 가능 — 아래 “임시 주소” 참고

## 고정 도메인으로 배포 (권장)
1. **터널 만들기** — Cloudflare Zero Trust 대시보드 → Networks → Tunnels → *Create a tunnel* → *Cloudflared* → 이름 입력 후 **토큰 복사**
   - *Public hostname* 추가: 호스트명 `album.내도메인.com`, Service: **HTTP**, URL: **`app:8080`**
2. **환경변수** — `cp .env.example .env` 후 최소한 아래를 채웁니다.
   ```env
   POSTGRES_PASSWORD=<긴 무작위 문자열>
   APP_BASE_URL=https://album.내도메인.com
   CLOUDFLARE_TUNNEL_TOKEN=<1번에서 복사한 토큰>
   ```
3. **기동**
   ```bash
   docker compose --profile named up -d --build
   docker compose logs -f app          # "Started FamilyAlbumApplication" 확인
   ```
4. **접속 URL** → `https://album.내도메인.com` (1번에서 정한 호스트명)
5. 내 가족을 만든 뒤 `.env` 에 `ALLOW_NEW_FAMILY=false` 를 넣고 `docker compose --profile named up -d` 로 재적용 → 외부인이 새 가족을 만들지 못하고 초대 코드 가입만 가능합니다.

## 임시 주소(계정 불필요)
```bash
docker compose --profile quick up -d --build
docker compose logs cloudflared-quick | grep trycloudflare.com   # https://<임의>.trycloudflare.com
```
기동할 때마다 주소가 바뀌고 가용성 보장이 없어 **시험용**입니다. 주소가 고정이 아니므로 구글 포토 연동(redirect URI 사전 등록)은 쓸 수 없습니다.

## 구글 포토 / 이미지 검색
- 구글 콘솔 *승인된 리디렉션 URI*: `https://album.내도메인.com/google/callback` (= `APP_BASE_URL` + `/google/callback`)
- `.env` 에 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_TOKEN_KEY`(`openssl rand -base64 32`) 설정 → README 의 구글 포토 절 참고
- `PEXELS_API_KEY` 는 선택(없으면 Openverse)

## 보안 권장
- `prod` 프로파일로 실행되므로 **Swagger 는 비노출**, 세션 쿠키는 Secure 입니다(HTTPS 필요 — 터널이 제공).
- 인터넷에 공개되므로 **Cloudflare Access**(Zero Trust → Access → Applications → Self-hosted)로 가족 이메일만 통과시키는 1차 관문을 두면 앱 로그인 앞에 한 겹이 더 생깁니다.
- `127.0.0.1:8080` 으로만 로컬 포트를 열어 두었습니다(터널 외 직접 노출 없음). 로컬 http 에서는 Secure 쿠키 때문에 일부 브라우저(Safari)에서 로그인이 유지되지 않을 수 있으니 확인은 터널 주소로 하세요.

## 운영
```bash
# 업데이트
git pull && docker compose --profile named up -d --build

# 백업 (DB + 사진)
docker compose exec -T postgres pg_dump -U familyalbum familyalbum > backup-$(date +%F).sql
docker run --rm -v family-album_photos:/data -v "$PWD":/out alpine tar czf /out/photos-$(date +%F).tgz -C /data .
```
- 볼륨 이름은 프로젝트 디렉터리명에 따라 다를 수 있습니다(`docker volume ls`).
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 로 첫 기동에 테이블을 만듭니다. 스키마 마이그레이션(Flyway/Liquibase)이 아직 없으므로 장기 운영 전에 도입하세요.

## 이 구성은 아직 시험되지 않았습니다
작성 환경에서는 Docker 빌드와 Maven Central/Cloudflare 접속이 불가능해 `Dockerfile`·`docker-compose.yml` 은 **실행해 보지 못했습니다**(compose YAML 문법만 검증). 첫 기동에서 문제가 나면 로그를 알려 주세요. 참고: 베이스 이미지 `eclipse-temurin:24` 는 비 LTS 라 태그가 더 이상 갱신되지 않을 수 있습니다. 필요하면 `build.gradle` toolchain 과 `Dockerfile` 을 LTS 버전으로 함께 올리세요.

## Cloudflare Containers 로 가려면 (참고)
Workers 유료 플랜 + `wrangler deploy`(로컬 Docker 필요)로 같은 `Dockerfile` 을 올릴 수 있습니다. 단 디스크가 휘발하므로 먼저 다음이 필요합니다.
1. 외부 PostgreSQL 연결(`DB_URL` 등; Neon/Supabase 등 — Cloudflare Hyperdrive 로 연결 풀링 가능)
2. `FileStorage` 를 S3 호환 저장소(Cloudflare R2)로 교체
3. Worker(`@cloudflare/containers`)에서 컨테이너로 요청을 전달하는 코드와 `wrangler.jsonc`
이 작업을 원하시면 별도로 진행할 수 있습니다.
