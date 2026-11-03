package com.familyalbum.google;

import com.familyalbum.web.ApiException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google Photos Picker API 클라이언트.
 * 흐름: 세션 생성 → 사용자가 pickerUri 에서 사진 선택 → mediaItemsSet 폴링 → 선택 목록 조회 →
 * baseUrl + "=d" 로 원본 다운로드(Authorization: Bearer, baseUrl 은 약 60분 유효) → 세션 삭제.
 */
@Component
public class GooglePickerClient {

    private static final String BASE = "https://photospicker.googleapis.com/v1";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    public record Session(String id, String pickerUri, boolean mediaItemsSet, long pollIntervalMs, long timeoutMs) {}

    public record PickedItem(String id, String type, String baseUrl, String mimeType, String filename) {}

    private final RestClient rest;

    public GooglePickerClient(RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.rest = builder.requestFactory(factory).build();
    }

    public Session createSession(String accessToken, int maxItemCount) {
        // int64 필드는 proto-JSON 규칙상 문자열로 전달
        Map<String, Object> body = Map.of("pickingConfig", Map.of("maxItemCount", String.valueOf(maxItemCount)));
        return toSession(call(() -> rest.post().uri(BASE + "/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(MAP)));
    }

    public Session getSession(String accessToken, String sessionId) {
        return toSession(call(() -> rest.get().uri(BASE + "/sessions/{id}", sessionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)).retrieve().body(MAP)));
    }

    public List<PickedItem> listMediaItems(String accessToken, String sessionId) {
        List<PickedItem> items = new ArrayList<>();
        String pageToken = null;
        do {
            Map<String, Object> page = listPage(accessToken, sessionId, pageToken);
            if (page.get("mediaItems") instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> it)) continue;
                    Map<?, ?> file = it.get("mediaFile") instanceof Map<?, ?> f ? f : Map.of();
                    items.add(new PickedItem(s(it.get("id")), s(it.get("type")), s(file.get("baseUrl")),
                            s(file.get("mimeType")), s(file.get("filename"))));
                }
            }
            pageToken = s(page.get("nextPageToken"));
        } while (pageToken != null && !pageToken.isBlank());
        return items;
    }

    /** 원본(EXIF 포함) 다운로드: baseUrl 에 =d 를 붙이고 OAuth 헤더를 함께 보낸다. */
    public byte[] download(String accessToken, String baseUrl) {
        byte[] bytes = call(() -> rest.get().uri(URI.create(baseUrl + "=d"))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)).retrieve().body(byte[].class));
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "사진 데이터를 받지 못했어요.");
        }
        return bytes;
    }

    /** 세션 수 제한을 피하기 위해 사용이 끝나면 정리(실패 무시) */
    public void deleteSession(String accessToken, String sessionId) {
        try {
            rest.delete().uri(BASE + "/sessions/{id}", sessionId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)).retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    private Map<String, Object> listPage(String accessToken, String sessionId, String pageToken) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("sessionId", sessionId);
        String url = BASE + "/mediaItems?sessionId={sessionId}&pageSize=100";
        if (pageToken != null && !pageToken.isBlank()) {
            url += "&pageToken={pageToken}";
            vars.put("pageToken", pageToken);
        }
        String finalUrl = url;
        return call(() -> rest.get().uri(finalUrl, vars)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)).retrieve().body(MAP));
    }

    private static Session toSession(Map<String, Object> m) {
        if (m == null) throw new ApiException(HttpStatus.BAD_GATEWAY, "구글 포토 응답이 비어 있어요.");
        Map<?, ?> polling = m.get("pollingConfig") instanceof Map<?, ?> p ? p : Map.of();
        return new Session(s(m.get("id")), s(m.get("pickerUri")), Boolean.TRUE.equals(m.get("mediaItemsSet")),
                millis(polling.get("pollInterval"), 5_000), millis(polling.get("timeoutIn"), 600_000));
    }

    /** "5s", "5.5s" 같은 protobuf Duration 문자열 → ms */
    private static long millis(Object v, long fallback) {
        if (v instanceof String str && str.endsWith("s")) {
            try {
                return (long) (Double.parseDouble(str.substring(0, str.length() - 1)) * 1000);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private static String s(Object o) {
        return o == null ? null : o.toString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                throw new GoogleAuthException("unauthorized");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "구글 포토 요청에 실패했어요. (" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "구글 포토에 연결할 수 없어요.");
        }
    }
}
