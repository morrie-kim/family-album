import { Container, getContainer } from "@cloudflare/containers";

/**
 * 가족앨범 Spring Boot 컨테이너.
 *  - 요청이 오면 컨테이너를 깨우고(콜드 스타트 수십 초) 8080 포트가 열릴 때까지 기다린 뒤 전달한다.
 *  - 마지막 요청 후 sleepAfter 동안 조용하면 컨테이너는 잠들고, **컨테이너 디스크는 초기화**된다(휘발성).
 *    영구 보관이 필요하면 DB_URL(외부 PostgreSQL) 시크릿을 설정하고 사진 저장소(R2 등)를 연동해야 한다.
 */
export class FamilyAlbum extends Container {
  defaultPort = 8080;
  sleepAfter = "30m";

  constructor(ctx, env) {
    super(ctx, env);
    // Worker 의 Variables/Secrets 중 앱에 필요한 값만 컨테이너 환경변수로 전달
    const names = [
      "APP_BASE_URL", "ALLOW_NEW_FAMILY",
      "DB_URL", "DB_USERNAME", "DB_PASSWORD",
      "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "GOOGLE_TOKEN_KEY",
      "PEXELS_API_KEY",
    ];
    const passed = Object.fromEntries(
      names.filter((n) => env[n] !== undefined && env[n] !== "").map((n) => [n, String(env[n])]),
    );
    this.envVars = { SPRING_PROFILES_ACTIVE: "prod,cloudflare", ...passed };
  }

  async fetch(request) {
    const url = new URL(request.url);

    // 공개 주소를 따로 설정하지 않았다면 접속한 주소를 사용(구글 OAuth redirect URI 계산에 쓰임)
    if (!this.envVars.APP_BASE_URL) {
      this.envVars = { ...this.envVars, APP_BASE_URL: url.origin };
    }

    // Spring 이 https 로 인식하도록(Secure 쿠키, 리다이렉트 URL) 전달 헤더를 명시
    const headers = new Headers(request.headers);
    headers.set("X-Forwarded-Proto", "https");
    headers.set("X-Forwarded-Host", url.host);
    const forwarded = new Request(request, { headers });

    try {
      // 이미 떠 있으면 즉시 반환, 잠들어 있으면 Spring Boot 기동(콜드 스타트)을 기다린다.
      await this.startAndWaitForPorts({
        ports: this.defaultPort,
        cancellationOptions: { portReadyTimeoutMS: 85_000, instanceGetTimeoutMS: 30_000 },
      });
    } catch (err) {
      console.error("container not ready:", err);
      return new Response(WAKING_PAGE, {
        status: 503,
        headers: { "Content-Type": "text/html; charset=utf-8", "Retry-After": "5", "Cache-Control": "no-store" },
      });
    }
    return super.fetch(forwarded);
  }

  onError(error) {
    console.error("container error:", error);
  }
}

const WAKING_PAGE = `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="refresh" content="5">
<title>가족앨범</title><style>body{font-family:system-ui,-apple-system,"Noto Sans KR",sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#f9fafb;color:#191f28}
main{text-align:center;padding:24px}h1{font-size:24px;margin:0 0 8px}p{color:#6b7684;margin:0}</style></head>
<body><main><h1>서버를 깨우는 중이에요</h1><p>잠시 후 자동으로 다시 시도합니다…</p></main></body></html>`;

export default {
  async fetch(request, env) {
    // 단일 인스턴스(이름 고정)로 전달
    return getContainer(env.FAMILY_ALBUM).fetch(request);
  },
};
