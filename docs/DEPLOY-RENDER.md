# Render 무료 배포

## 구성

- Web Service: Docker, Free
- 기본 데이터베이스: H2 파일 DB (`/tmp/family-album`)
- 사진 바이너리: 애플리케이션의 기존 사진 저장 설정 사용
- 배포: GitHub `main` 브랜치 push 시 Render 자동 배포
- 기본 배포에는 PostgreSQL, Neon, Cloudflare 등 외부 DB가 필요하지 않습니다.

## 1. Web Service 생성

저장소 루트의 `render.yaml`을 사용해 Blueprint로 생성하거나, New → Web Service에서 GitHub 저장소를 직접 연결합니다.

| 설정 | 값 |
|---|---|
| Branch | `main` |
| Runtime | Docker |
| Dockerfile Path | `./Dockerfile` |
| Plan | Free |
| Health Check Path | `/login` |

## 2. 환경변수

최소한의 기본 배포는 아래 설정으로 동작하며 DB 관련 환경변수는 필요하지 않습니다.

| Key | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `APP_BASE_URL` | `https://<서비스명>.onrender.com` |
| `ALLOW_NEW_FAMILY` | 최초 가입 전 `true`, 최초 가족 생성 후 `false` 권장 |
| `GOOGLE_CLIENT_ID` | Google OAuth 사용 시 |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 사용 시 |
| `GOOGLE_TOKEN_KEY` | Google OAuth 사용 시, base64 32바이트 키 |

Google OAuth를 사용한다면 승인된 redirect URI를 `<APP_BASE_URL>/google/callback`으로 등록합니다.

### PostgreSQL을 나중에 연결하려는 경우

기본 H2 대신 PostgreSQL을 사용하려면 Render 환경변수에 다음 값을 추가합니다.

- `DB_URL`: `jdbc:postgresql://<internal-host>:5432/<database-name>` 또는 Render가 제공하는 `postgres://...` URL
- `DB_USERNAME`: PostgreSQL 사용자명
- `DB_PASSWORD`: PostgreSQL 비밀번호

DB_URL이 없으면 H2 파일 DB가 자동으로 선택됩니다.

## 3. 배포 확인

1. Render 배포 로그에서 Spring Boot 기동 및 애플리케이션의 `Started FamilyAlbumApplication` 메시지를 확인합니다.
2. `https://<서비스명>.onrender.com/login`으로 접속합니다.
3. 첫 가족 계정을 만든 뒤 `ALLOW_NEW_FAMILY=false`로 변경합니다.
4. 로그인, 앨범 생성, 사진 업로드/조회 기능을 확인합니다.

## 4. 무료 플랜의 중요한 데이터 보존 제한

**중요: Render Free Web Service의 파일시스템은 영구 저장소가 아닙니다.**

- 이 기본 구성의 H2 DB 파일은 `/tmp/family-album`에 생성됩니다.
- Render Free에서는 재배포, 인스턴스 교체, 재시작 등으로 파일이 사라질 수 있습니다. 따라서 계정, 가족, 앨범, 사진 메타데이터 등 DB 데이터가 유실될 수 있습니다.
- 사진 파일을 로컬 파일시스템에 저장하는 기능도 동일하게 유실 위험이 있습니다.
- Render Free Web Service는 15분간 인바운드 요청이 없으면 sleep 상태가 되며, 첫 요청은 cold start가 발생할 수 있습니다.
- 가족 사진을 장기간 보존하는 실사용 서비스에는 이 H2 + Free 구성은 적합하지 않습니다. 영구 DB와 영구 파일 스토리지를 연결하거나, 주기적인 외부 백업을 별도로 구성해야 합니다.
- 무료 PostgreSQL을 사용하더라도 무료 DB의 만료/삭제 정책과 백업 부재를 고려해야 합니다.

이 구성은 우선 비용 없이 앱을 기동하고 기능을 검증하기 위한 데모/테스트 배포용입니다.

## 기존 데이터 이전

기존 VPS의 DB와 사진 파일은 Render로 자동 이전되지 않습니다. 기존 데이터를 옮기려면 DB 백업/복원과 사진 파일 이관을 별도로 진행해야 합니다.
