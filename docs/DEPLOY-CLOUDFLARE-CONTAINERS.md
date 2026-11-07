# Cloudflare Containers 로 배포하기 (Workers Builds)

> **어느 경로를 쓸까?**
> - **영구 데이터가 필요한 운영** → [VPS 자동 배포](DEPLOY-CLOUDFLARE.md)(GitHub Actions + Docker Compose + Cloudflare Tunnel). DB·사진이 Docker 볼륨에 유지됩니다.
> - **Cloudflare 만으로 올려 보기(시연/실험)** → 이 문서. 컨테이너 디스크가 휘발성이라 외부 DB·저장소 연동 전에는 데이터가 유지되지 않습니다.
> 두 경로는 같은 `Dockerfile` 을 사용합니다. 이 저장소에 `wrangler.jsonc` 가 있으므로 Workers Builds 가 연결되어 있으면 `npx wrangler deploy` 는 **Containers 배포**를 수행합니다(Workers 유료 플랜 필요).

## Workers Builds 오류 원인과 설정
Cloudflare 대시보드에서 GitHub 저장소를 연결해 자동 배포할 때 아래 오류가 났다면:

```
Executing user deploy command: ./gradlew clean bootJar
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```
- **원인**: Deploy command 가 `./gradlew clean bootJar` 로 설정되어 있었고, Workers Builds 빌드 이미지에는 Java(JDK)가 없습니다. 설령 jar 가 만들어져도 Workers 는 jar 를 실행하지 못하므로 이 명령은 배포 명령이 될 수 없습니다.
- **해결**: Gradle/Java 빌드는 **Docker 이미지 안에서**(`Dockerfile`) 수행하고, 배포 명령은 `wrangler deploy` 가 컨테이너 이미지를 빌드·배포하도록 합니다. 저장소에 `wrangler.jsonc`, `cloudflare/worker.js`, `package.json`(+lock)이 추가되어 있습니다.

### 대시보드 설정 (Workers & Pages → 해당 Worker → Settings → Builds)
| 항목 | 값 |
|---|---|
| Git repository | `morrie-kim/family-album` (branch `main`) |
| Root directory | `/` |
| **Build command** | **비워 둠** (npm 의존성은 자동 설치) |
| **Deploy command** | **`npx wrangler deploy`** |

- Worker 이름은 `wrangler.jsonc` 의 `name`(`family-album`)과 **같아야** 합니다. 다른 이름으로 만들었다면 `wrangler.jsonc` 의 `name` 을 그 이름으로 바꾸세요.
- Cloudflare Containers 는 **Workers 유료 플랜**이 필요합니다. 첫 빌드는 Gradle 의존성을 내려받아 몇 분 걸릴 수 있습니다.
- **Variables and Secrets**(같은 Settings 화면)에 값을 넣으면 컨테이너 환경변수로 전달됩니다: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_BASE_URL`, `ALLOW_NEW_FAMILY`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_TOKEN_KEY`, `PEXELS_API_KEY`.
  (`APP_BASE_URL` 을 비우면 접속한 주소가 자동으로 쓰입니다.)
- **접속 URL**: 배포가 성공하면 `https://family-album.<내 계정 서브도메인>.workers.dev` 입니다(대시보드 Worker 화면에 표시). 커스텀 도메인은 Worker → Settings → Domains 에서 연결합니다.

### 동작 방식과 한계 (중요)
- 첫 요청(또는 30분 이상 조용했던 뒤)에는 Spring Boot 컨테이너를 깨우느라 수십 초가 걸립니다. 그동안 “서버를 깨우는 중이에요” 안내 페이지(자동 새로고침)가 보입니다.
- **컨테이너 디스크는 휘발성**입니다.
  - `DB_URL` 이 없으면 **인메모리 H2** 로 동작해 컨테이너가 잠들면 계정·폴더·월드컵 데이터가 사라집니다(시연용). 영구 보관하려면 외부 PostgreSQL(예: Neon)을 만들고 `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require`, `DB_USERNAME`, `DB_PASSWORD` 를 시크릿으로 넣으세요.
  - **업로드한 사진은 컨테이너 로컬 디스크에 저장되어 컨테이너가 잠들면 사라집니다.** R2 같은 객체 저장소를 쓰는 `FileStorage` 구현이 아직 없습니다(아래 “남은 작업”).
- 요청 본문 한도(Workers 플랜별 100MB 등)를 넘지 않도록 업로드 화면은 한 요청을 최대 60MB 로 나눠 보냅니다.
- 이 구성은 Docker 이미지 빌드까지 **실제로 실행해 보지 못했습니다**(작성 환경에 Docker 없음). `wrangler deploy --dry-run` 으로 설정 유효성과 Worker 번들링만 확인했습니다.

## 남은 작업 (Containers 로 데이터를 영구 보관하려면)
1. 외부 PostgreSQL 연결 — 설정만 하면 됨(위 시크릿). Hyperdrive 로 연결 풀링도 가능
2. `FileStorage` 를 S3 호환 저장소(Cloudflare R2)로 교체 — **미구현**. 이 작업 전까지 사진은 휘발성
필요하시면 R2 저장소 구현을 이어서 진행할 수 있습니다.
