# 가족앨범

가족 구성원이 각자 계정을 만들고, **폴더별로 계정마다 접근 권한을 정해** 사진을 공유하는 서비스.
단일 Spring Boot 서버(화면 + REST API)이며 구글 포토에서 사진을 가져올 수 있습니다.

- Java 24, Spring Boot 3.5.x, Spring MVC + Thymeleaf, Spring Data JPA, Spring Security(세션 로그인 + CSRF)
- DB: H2(개발, 기본) / PostgreSQL(`prod` 프로파일) · 사진 파일: 로컬 디스크 `data/photos/{familyId}/{uuid}.ext`

## 실행
```bash
./gradlew bootRun                      # http://localhost:8080  (Windows: gradlew.bat bootRun)
```
Gradle Wrapper(8.14.3)가 포함되어 있어 별도 Gradle 설치가 필요 없습니다. 최초 실행 시 배포본을 내려받으며 SHA-256 체크섬을 검증합니다.
**JDK 24** 가 설치되어 있어야 합니다(`build.gradle` 의 toolchain 이 Java 24 를 요구).
회원가입(새 가족 이름 입력) → 첫 가입자가 **가족 관리자(ADMIN)** 가 되고 기본 폴더 “가족 앨범”이 만들어집니다.
`가족` 메뉴의 초대 코드로 다른 가족이 가입하면 기본 폴더에 **올리기(EDIT)** 권한이 자동 부여됩니다.

> ⚠ 이전 버전(폴더/권한 도입 전)으로 만든 개발 DB 는 스키마가 달라 `./data` 를 지우고 시작하세요.

## 1. 폴더별·계정별 접근 권한
| 권한 | 의미 |
|---|---|
| 접근 없음 | 폴더가 목록에도 보이지 않고, 직접 URL 로 접근해도 404 |
| 보기 (VIEW) | 폴더 안 사진 열람 |
| 올리기 (EDIT) | 보기 + 사진 업로드/가져오기 + **내가 올린 사진** 삭제 |
| 관리 (MANAGE) | **가족 관리자** 또는 **폴더를 만든 사람** — 항상 전체 권한. 이름 변경·권한 설정·폴더 삭제, 다른 사람 사진 삭제 |

- 사진 화면 상단 폴더 바에서 폴더 전환 · `새 폴더` · `폴더 설정`(관리 권한자만). 폴더 설정 화면에서 가족 구성원별로 *접근 없음 / 보기 / 올리기* 를 선택합니다.
- 기본 폴더는 삭제할 수 없고, 사진이 남은 폴더도 삭제할 수 없습니다.
- 권한 판정은 `FolderAccessService` 한 곳에서 처리하고 단위 테스트(`FolderAccessServiceTest`)로 고정했습니다.
- API: `GET/POST /api/folders`, `GET/PUT/DELETE /api/folders/{id}` (Swagger 참고)

## 2. 구글 포토 연결
각 가족 구성원이 **자기 구글 계정**을 연결하고, 구글 포토 선택 창(Picker)에서 고른 사진만 폴더로 가져옵니다.

> 2025-03-31 부터 구글은 전체 라이브러리를 읽는 Library API 스코프(`photoslibrary.readonly` 등)를 제거했습니다.
> 따라서 이 앱은 **Picker API**(`photospicker.mediaitems.readonly`)만 사용하며, 사용자가 직접 선택한 사진 외에는 접근하지 않습니다.

### 설정 (한 번)
1. [Google Cloud Console](https://console.cloud.google.com/) 에서 프로젝트 생성 → **Google Photos Picker API** 사용 설정
2. **OAuth 동의 화면** 구성(외부) → 범위에 `.../auth/photospicker.mediaitems.readonly` 추가 → **테스트 사용자**에 가족 구글 계정 추가
3. **사용자 인증 정보 → OAuth 클라이언트 ID(웹 애플리케이션)** 생성
   - 승인된 리디렉션 URI: `https://<NGROK_DOMAIN>/google/callback` (로컬 테스트는 `http://localhost:8080/google/callback`)
4. 환경변수 설정(`.env.example` 참고): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_TOKEN_KEY`(`openssl rand -base64 32`)

### 사용
상단 `구글 포토` 메뉴 → `구글 계정 연결` → 가져올 폴더 선택 → `구글 포토에서 사진 고르기` → 열린 창에서 선택·완료 → 자동으로 가져옵니다(한 번에 최대 50장).
- 이미 가져온 사진(구글 mediaItem id 기준)은 건너뜁니다. 동영상은 제외됩니다.
- refresh token 은 AES-256-GCM 으로 암호화해 저장하고, 연결 해제 시 구글 쪽 토큰도 폐기합니다.

### 알아둘 점
- **‘테스트’ 게시 상태의 앱은 refresh token 이 7일 뒤 만료**됩니다(구글 정책). 만료되면 앱이 연결을 지우고 “다시 연결해 주세요”를 안내합니다. 오래 쓰려면 동의 화면을 *프로덕션*으로 게시하세요(민감 스코프는 구글 검증이 필요할 수 있고, 검증 전에는 “확인되지 않은 앱” 경고와 사용자 수 제한이 있습니다).
- 가져온 사진의 HEIC 등 일부 형식은 서버 허용 형식(JPEG/PNG/WEBP/GIF/HEIC)에 따라 거절될 수 있습니다.

## 3-1. Cloudflare 로 배포 (Docker + Tunnel)
`docker compose --profile named up -d --build` — 앱 + PostgreSQL + Cloudflare Tunnel 구성(고정 도메인 HTTPS). 절차와 주의사항은 [docs/DEPLOY-CLOUDFLARE.md](docs/DEPLOY-CLOUDFLARE.md).
(Cloudflare Workers 에는 JVM 앱을 올릴 수 없고, Containers 는 디스크가 휘발해 현재 구조로는 부적합합니다.)

## 3. ngrok 으로 호스팅
```bash
cp .env.example .env        # NGROK_DOMAIN, GOOGLE_* 채우기
./scripts/ngrok-up.sh       # ngrok 터널 → APP_BASE_URL 설정 → 앱 기동(profile: ngrok)
```
- ngrok 계정의 **고정(static) 도메인**을 쓰세요. 구글 redirect URI 를 미리 등록해야 하므로 주소가 바뀌면 연동이 깨집니다.
- 수동 실행: `ngrok http 8080 --url=<도메인>` 후 `APP_BASE_URL=https://<도메인> SPRING_PROFILES_ACTIVE=ngrok ./gradlew bootRun`
- `ngrok` 프로파일은 세션 쿠키를 `Secure` 로 설정하고, `forward-headers-strategy: framework` 로 `X-Forwarded-*` 를 신뢰합니다.
- **인터넷에 공개되는 서버**입니다. 내 가족을 만든 뒤에는 `ALLOW_NEW_FAMILY=false` 로 재시작해 외부인이 새 가족을 만들지 못하게 하세요(초대 코드 가입만 가능). 필요하면 ngrok 의 IP 제한/인증 기능도 함께 사용하세요.
- ngrok 은 내 PC 가 켜져 있는 동안만 서비스됩니다. 상시 운영은 서버/클라우드 배포를 권장합니다.

## 4. 다중 업로드
- `사진 올리기` 시트: 여러 장 선택/드래그 앤 드롭/붙여넣기 → 미리보기 → **올릴 폴더 선택** → 진행률. 브라우저가 10장씩 순차 전송, 파일별 독립 처리(일부 실패해도 나머지 저장).
- API: `POST /api/photos/batch` (multipart `files`, `folderId`)
- 설정: `app.upload.max-files-per-request`(50), `app.upload.max-file-size`(20MB), `server.tomcat.max-part-count: 100` (Tomcat 기본 10 → 반드시 상향)
- 리버스 프록시 뒤라면 본문 크기 한도(`client_max_body_size` 등)도 확인

## 5. 이상형 월드컵 (사이드 기능)
상단 `월드컵` 메뉴에서 **주제를 입력**하고 후보를 등록한 뒤, 라운드를 골라 가족과 함께 우승자를 가립니다.

1. **새 월드컵**: 주제 입력 + 후보 등록(4~64개)
   - *인터넷 이미지*: **주제를 입력하면 관련 이미지를 인터넷에서 검색해 보여주고**, 체크한 이미지로 월드컵을 만듭니다(앞 16장 자동 선택, 검색어 수정·재검색 가능)
   - *앨범 사진*: 폴더를 고르고 사진을 체크(최신 60장 중 선택, 전체 선택 지원)
   - *텍스트*: 한 줄에 하나씩(공백·대소문자 중복은 자동 정리)
2. **시작**: 후보 수에 맞는 라운드(4/8/16/32/64강) 중 선택 → 후보를 무작위로 뽑아 대진 생성
3. **진행**: 카드를 눌러 선택(결승까지). 서버가 진행 상태를 보관하므로 중간에 나가도 이어서 할 수 있고, 더블 클릭으로 대진을 건너뛸 수 없습니다.
4. **결과**: 내 우승/준우승/라운드별 탈락 + **가족 전체 랭킹**(우승 횟수 → 승률 → 승수, 동률은 같은 순위)

- **접근 제어**: 텍스트 월드컵은 가족 전체, **사진 월드컵은 선택한 폴더를 볼 수 있는 계정만** 보이고 참여할 수 있습니다(후보 사진은 모두 그 폴더에서만 선택 가능 → 폴더 권한 우회 불가).
- **인터넷 이미지 검색**: `PEXELS_API_KEY` 가 있으면 Pexels(고화질, 한글 검색 지원), 없으면 키 없이 쓰는 **Openverse**(Creative Commons 등 오픈 라이선스)를 사용합니다.
  - Openverse 익명 한도는 낮습니다(페이지당 20건, 분당/일 호출 제한). 한 번의 검색은 최대 3회 호출(60장)이고 같은 검색어는 30분간 캐시합니다. 한글보다 **영어 검색어**가 잘 나옵니다.
  - 이미지 주소는 클라이언트 값이 아니라 **서버의 검색 결과(id)** 에서만 가져오고, `https` + 제공처 도메인(openverse.org, pexels.com)만 허용합니다.
  - 작성자·라이선스 출처는 월드컵 상세 화면의 *이미지 출처* 와 결과 화면에 표시됩니다(라이선스상 표기 유지 필요).
  - 외부 이미지는 브라우저가 제공처에서 직접 불러옵니다(`referrerpolicy=no-referrer`). 서버에 사본을 저장하지 않습니다.
- 플레이 기록은 본인만 볼 수 있고, 삭제는 만든 사람 또는 가족 관리자만 가능합니다.
- 월드컵 후보로 쓰이는 사진은 월드컵을 삭제하기 전까지 삭제할 수 없습니다(409).
- 토너먼트 상태 머신(`WorldcupPlay`)은 DB 없이 단위 테스트합니다(`WorldcupPlayTest`).

## 6. API 문서 (Swagger / OpenAPI)
| 환경 | Swagger UI | OpenAPI JSON |
|---|---|---|
| dev (기본) | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| prod | **비노출** (404) | **비노출** (404) |

- API 인증: HTTP Basic(이메일/비밀번호), stateless. 운영에서는 반드시 HTTPS.
- 운영 비노출: `application-prod.yml` 의 springdoc 비활성화 + `OpenApiConfig` `@Profile("!prod")` + 보안 설정에서 문서 경로 허용 조건부.
  회귀 방지 테스트: `SwaggerProdExposureTest`, `SwaggerDevExposureTest`
- ⚠ `ngrok` 프로파일은 `prod` 가 아니므로 **Swagger 가 공개 URL 로 노출**됩니다. 외부 공개 시 `SPRING_PROFILES_ACTIVE=ngrok,prod` 로 함께 지정하세요(prod 는 `DB_URL` 등 필요).

## 구조
```
com.familyalbum
 ├─ config      SecurityConfig, OpenApiConfig, AppProperties
 ├─ domain      Family, AppUser, Folder, FolderAccess, Photo, GoogleAccount, Permission/AccessLevel/FamilyRole
 ├─ repository  Spring Data JPA
 ├─ security    AppPrincipal, AppUserDetailsService
 ├─ service     AuthService, FolderService, FolderAccessService, PhotoService, PhotoUploadService, FamilyService, FileStorage
 ├─ worldcup    Worldcup/Entry/Play/Match, Bracket, WorldcupService, WorldcupDtos
 ├─ google      GoogleOAuthClient, GooglePickerClient, GoogleConnectionService, GoogleImportService, TokenCipher
 ├─ web         화면 컨트롤러(Album/Folder/Google/Worldcup/Auth), WebAdvice, Dtos
 └─ api         REST(Account/Folder/Photo) + ApiExceptionHandler
```

## 운영 전 보완 필요
1. Flyway/Liquibase 스키마 마이그레이션 (prod 는 `ddl-auto: validate`)
2. 업로드 매직바이트 검증, 썸네일 생성
3. 로그인 시도 제한, 비밀번호 재설정, 가족 구성원 탈퇴/강퇴·관리자 위임
4. 구글 가져오기를 백그라운드 작업으로 전환(현재는 요청 안에서 동기 처리, 최대 50장)
5. 통합 테스트(Testcontainers)
