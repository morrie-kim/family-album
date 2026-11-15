const isBackendRequest = (pathname) =>
  pathname.startsWith("/api/") || pathname === "/google/callback";

export default {
  async fetch(request, env) {
    const incomingUrl = new URL(request.url);

    // 정적 SPA 파일은 Workers Static Assets가 직접 제공합니다.
    if (!isBackendRequest(incomingUrl.pathname)) {
      return env.ASSETS.fetch(request);
    }

    if (!env.BACKEND_ORIGIN) {
      return new Response("Backend tunnel is not configured. Start scripts/cloudflare-worker-up.sh.", {
        status: 503,
        headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" }
      });
    }

    let backendOrigin;
    try {
      backendOrigin = new URL(env.BACKEND_ORIGIN);
    } catch {
      return new Response("Invalid BACKEND_ORIGIN configuration.", { status: 500 });
    }

    if (backendOrigin.protocol !== "https:") {
      return new Response("BACKEND_ORIGIN must use HTTPS.", { status: 500 });
    }

    const targetUrl = new URL(
      incomingUrl.pathname + incomingUrl.search,
      backendOrigin.origin
    );

    const headers = new Headers(request.headers);
    headers.set("X-Forwarded-Host", incomingUrl.host);
    headers.set("X-Forwarded-Proto", incomingUrl.protocol.slice(0, -1));
    headers.set("X-Forwarded-Port", incomingUrl.port || "443");

    const init = {
      method: request.method,
      headers,
      redirect: "manual"
    };
    if (request.method !== "GET" && request.method !== "HEAD") {
      init.body = request.body;
    }

    // 브라우저의 세션/CSRF 쿠키와 백엔드 Set-Cookie를 같은 Worker Origin으로 전달합니다.
    const upstream = await fetch(new Request(targetUrl, init));
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: upstream.headers
    });
  }
};
