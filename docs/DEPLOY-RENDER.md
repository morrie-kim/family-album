# Render 무료 배포

## 구성

- Web Service: Docker, Free
- PostgreSQL: Render Free PostgreSQL(1GB, 30일 만료)
- 사진 바이너리: PostgreSQL의 `photo_content` 테이블(`bytea`)
- 배포: GitHub `main` 브랜치 push 시 Render 자동 배포
- 외부 저장소: 사용하지 않음

## 1. PostgreSQL 생성

Render 대시보드에서 New → PostgreSQL을 선택하고 Free 플랜으로 생성합니다.

생성 후 **Connect** 정보에서 Internal Database URL의 호스트/DB 이름과 사용자명/비밀번호를 확인합니다. 앱의 `DB_URL`은 JDBC 형식이어야 합니다.

```text
jdbc:postgresql://<internal-host>:5432/<database-name>
```

Render가 표시하는 URL이 `postgresql://...` 형식이라면 그대로 넣지 말고 `jdbc:` 접두사를 붙여 JDBC URL로 입력합니다. 사용자명과 비밀번호는 각각 `DB_USERNAME`, `DB_PASSWORD`에 입력합니다.

## 2. Web Service 생성

저장소 루트의 `render.yaml`을 사용해 Blueprint로 생성하거나, New → Web Service에서 GitHub 저장소를 직접 연결합니다.

| 설정 | 값 |
|---|---|
| Branch | `main` |
| Runtime | Docker |
| Dockerfile Path | `./Dockerfile` |
| Plan | Free |
| Health Check Path | `/login` |

Blueprint 사용 시 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_BASE_URL` 값을 입력합니다.

## 3. 환경변수

| Key | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://<internal-host>:5432/<database-name>` |
| `DB_USERNAME` | Render PostgreSQL 사용자명 |
| `DB_PASSWORD` | Render PostgreSQL 비밀번호 |
| `APP_BASE_URL` | `https://<서비스명>.onrender.com` |
| `ALLOW_NEW_FAMILY` | 최초 가입 전 `true`, 최초 가족 생성 후 `false` 권장 |
| `GOOGLE_CLIENT_ID` | Google OAuth 사용 시 |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 사용 시 |
| `GOOGLE_TOKEN_KEY` | Google OAuth 사용 시, base64 32바이트 키 |

Google OAuth를 사용한다면 승인된 redirect URI를 `<APP_BASE_URL>/google/callback`으로 등록합니다.

## 4. 배포 확인

1. Web Service 배포 로그에서 Spring Boot 기동 및 PostgreSQL 연결을 확인합니다.
2. `https://<서비스명>.onrender.com/login`으로 접속합니다.
3. 첫 가족 계정을 만든 뒤 `ALLOW_NEW_FAMILY=false`로 변경합니다.
4. 사진 업로드 후 앱을 재시작해도 사진이 조회되는지 확인합니다.

## 무료 플랜의 중요한 제한

- Free Web Service는 15분간 인바운드 요청이 없으면 sleep 상태가 되며, 첫 요청은 cold start가 발생할 수 있습니다.
- Free PostgreSQL은 1GB이며 생성 후 30일에 만료됩니다. 만료 후 14일 grace period가 지나면 삭제되고 무료 DB 백업은 제공되지 않습니다.
- DB가 삭제되면 계정, 가족, 폴더, 사진 본문, 월드컵 데이터가 모두 사라집니다. 만료 전에 필요한 데이터를 직접 백업해야 합니다.
- 사진 본문도 PostgreSQL에 저장하므로 1GB 용량을 고려해 사진 업로드를 관리해야 합니다. 대용량 원본/대량 업로드 용도로는 적합하지 않습니다.
- Render Free Web Service의 로컬 파일시스템은 영구 저장소가 아닙니다. 신규 사진 저장에는 로컬 파일시스템을 사용하지 않습니다.

## 기존 데이터 이전

기존 VPS의 `photo` 메타데이터와 `/data/photos` 파일은 Render DB로 자동 이전되지 않습니다. 기존 가족 데이터를 옮길 계획이라면 DB 백업/복원과 사진 바이너리 이관을 별도로 진행해야 합니다.
