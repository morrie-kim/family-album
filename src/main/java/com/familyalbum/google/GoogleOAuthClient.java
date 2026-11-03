package com.familyalbum.google;

import com.familyalbum.config.AppProperties;
import com.familyalbum.web.ApiException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 구글 OAuth 2.0 (웹 서버 방식, authorization code + offline access). 토큰/응답 본문은 로그에 남기지 않는다. */
@Component
public class GoogleOAuthClient {

    /** Picker API 전용 최소 스코프: 사용자가 직접 고른 사진에만 접근 */
    public static final String SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly";

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

    public record Tokens(String accessToken, String refreshToken) {}

    private final AppProperties props;
    private final RestClient rest;

    public GoogleOAuthClient(AppProperties props, RestClient.Builder builder) {
        this.props = props;
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.rest = builder.requestFactory(factory).build();
    }

    /** 구글 콘솔의 "승인된 리디렉션 URI"에 정확히 이 값을 등록해야 한다. */
    public String redirectUri() {
        String base = props.publicBaseUrl() == null ? "" : props.publicBaseUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/google/callback";
    }

    public String authorizationUrl(String state) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", props.google().clientId());
        q.put("redirect_uri", redirectUri());
        q.put("response_type", "code");
        q.put("scope", SCOPE);
        q.put("access_type", "offline");   // refresh token 발급
        q.put("prompt", "consent");        // 재연결 시에도 refresh token 을 다시 받기 위해
        q.put("state", state);
        return AUTH_URL + "?" + q.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public Tokens exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.google().clientId());
        form.add("client_secret", props.google().clientSecret());
        form.add("redirect_uri", redirectUri());
        form.add("grant_type", "authorization_code");
        Map<String, Object> res = post(TOKEN_URL, form);
        return new Tokens(str(res, "access_token"), str(res, "refresh_token"));
    }

    /** refresh token 으로 access token 발급. 폐기/만료(invalid_grant)면 GoogleAuthException. */
    public String refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.google().clientId());
        form.add("client_secret", props.google().clientSecret());
        form.add("grant_type", "refresh_token");
        String token = str(post(TOKEN_URL, form), "access_token");
        if (token == null) throw new ApiException(HttpStatus.BAD_GATEWAY, "구글에서 액세스 토큰을 받지 못했어요.");
        return token;
    }

    /** 연결 해제 시 구글 쪽 토큰도 폐기(실패해도 무시) */
    public void revoke(String token) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", token);
            rest.post().uri(REVOKE_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    private Map<String, Object> post(String url, MultiValueMap<String, String> form) {
        try {
            return rest.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (HttpClientErrorException e) {
            if (e.getResponseBodyAsString().contains("invalid_grant")) {
                throw new GoogleAuthException("invalid_grant");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "구글 인증 서버 요청에 실패했어요. (" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "구글 서버에 연결할 수 없어요.");
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v == null ? null : v.toString();
    }
}
