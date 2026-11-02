# 가족앨범 (단일 서버)

백엔드와 프론트엔드를 하나의 Spring Boot 애플리케이션으로 통합한 버전입니다.

- Java 24, Spring Boot 3.5.x, Spring MVC + Thymeleaf, Spring Data JPA, Spring Security(세션 로그인 + CSRF)
- DB: H2(개발, 기본) / PostgreSQL(`prod` 프로파일)
- 사진 파일: 로컬 디스크 `data/photos/{familyId}/{uuid}.ext`

## 실행
```bash
gradle wrapper --gradle-version 8.14   # 최초 1회
./gradlew bootRun                      # http://localhost:8080
```
회원가입(새 가족 이름 입력) → `가족` 메뉴의 초대 코드를 가족에게 전달 → 같은 코드로 가입하면 앨범 공유.

## 환경변수
- `PHOTO_STORAGE_ROOT` (기본 `./data/photos`)
- prod: `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

## 통합하면서 달라진 점
- 별도 서버 간 REST 호출/JWT 제거 → 컨트롤러가 서비스를 직접 호출, 인증은 HttpSession 기반
- 이미지는 `/photos/{id}/file` 에서 권한 확인 후 직접 스트리밍(프록시 단계 없음)
- CSRF 보호 기본 적용(Thymeleaf `th:action` 이 토큰 자동 삽입)
- `/api/**` REST API 는 같은 서비스 위에 별도 보안 체인(Basic, stateless)으로 제공

## 다중 업로드
- 화면: `사진 올리기` 시트에서 여러 장 선택 / 드래그 앤 드롭 / 붙여넣기 → 미리보기(개별 제거) → 공개 범위 선택 → 진행률 표시
  - 브라우저가 10장씩 순차 요청(`POST /photos`, Ajax)으로 나눠 전송하므로 장수가 많아도 요청 크기·파트 수 한도에 걸리지 않음
  - 파일별 독립 처리: 일부가 실패해도 나머지는 저장되고, 실패 파일만 사유와 함께 남아 재시도 가능
  - JS 비활성 환경에서는 같은 폼이 일반 전송(`files` 다중 필드) 후 리다이렉트로 폴백
- API: `POST /api/photos/batch` (multipart `files` 다중) → `{ uploaded: [...], failed: [{filename, reason}] }`
- 설정(`application.yml`)
  - `app.upload.max-files-per-request` (기본 50), `app.upload.max-file-size` (기본 20MB, 파일별 검사)
  - `server.tomcat.max-part-count: 100` — **Tomcat 기본값이 10**이라 설정하지 않으면 11장 이상 한 번에 보낼 때 실패
  - `spring.servlet.multipart.max-file-size / max-request-size` 는 요청 전체 방어선(50MB / 300MB)
- 리버스 프록시(Nginx/Ingress/Route) 뒤라면 `client_max_body_size` 등 본문 크기 한도도 함께 확인

## UI
`TOSS-DESIGN.md` 의 색상·타이포·간격·컴포넌트·모션·다크모드 토큰을 `static/css/app.css` 에 반영
(상단 바 56px blur, 모바일 하단 탭, 카드 16px, 입력 필드 포커스 스타일, 페이지당 하나의 Primary CTA, 아웃라인 1.5px 아이콘, `prefers-reduced-motion` 대응).
- 폰트는 문서의 스택을 그대로 쓰되 Toss 전용 폰트는 번들하지 않으므로 시스템 한글 폰트로 대체됨

## API 문서 (Swagger / OpenAPI)
화면(Thymeleaf)과 별개로 같은 서비스 계층 위에 REST API(`/api/**`)를 제공하며 springdoc-openapi 로 문서화합니다.

| 환경 | Swagger UI | OpenAPI JSON |
|---|---|---|
| dev (기본) | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| prod | **비노출** (404) | **비노출** (404) |

- API 인증: HTTP Basic(이메일/비밀번호), stateless, CSRF 없음. Swagger UI 우측 상단 **Authorize** 에 입력 후 *Try it out*. 운영에서는 반드시 HTTPS.
- 회원가입 `POST /api/auth/signup` 은 인증 불필요.
- 운영 비노출은 3중으로 보장:
  1. `application-prod.yml` 의 `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` → 엔드포인트 미등록
  2. `OpenApiConfig` 가 `@Profile("!prod")` → 문서 메타 빈도 로드 안 됨
  3. `SecurityConfig` 가 문서 켜진 환경에서만 `/v3/api-docs/**`, `/swagger-ui/**` 를 permitAll
- 회귀 방지: `SwaggerProdExposureTest`(prod 에서 로그인 사용자도 404), `SwaggerDevExposureTest`

## 구조
```
com.familyalbum
 ├─ config      SecurityConfig, AppProperties
 ├─ domain      Family, AppUser, Photo, Visibility
 ├─ repository  Spring Data JPA
 ├─ security    AppPrincipal, AppUserDetailsService
 ├─ service     AuthService, PhotoService, FamilyService, FileStorage
 ├─ web         AuthWebController, AlbumController, WebAdvice, Dtos (화면)
 └─ api         AccountApiController, PhotoApiController, ApiExceptionHandler (REST)
```

## 운영 전 보완 필요
1. Flyway/Liquibase 스키마 마이그레이션 (prod 는 `ddl-auto: validate`)
2. 업로드 매직바이트 검증, 썸네일 생성
3. 로그인 시도 제한, 비밀번호 재설정, 가족 구성원 탈퇴/강퇴
4. 테스트 코드 (Testcontainers 권장)
