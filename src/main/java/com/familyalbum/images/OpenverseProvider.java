package com.familyalbum.images;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Openverse(WordPress) — Creative Commons 등 오픈 라이선스 이미지 검색. API 키 불필요.
 * 익명 한도: 페이지당 최대 20건, 분당 20회/일 200회(공식 수치가 아니라 운영 상황에 따라 달라질 수 있음).
 */
@Component
public class OpenverseProvider implements ImageSearchProvider {

    static final int PAGE_SIZE = 20;
    private static final String URL = "https://api.openverse.org/v1/images/?q={q}&page_size=" + PAGE_SIZE + "&page={page}";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final String userAgent;

    public OpenverseProvider(RestClient.Builder builder, ImageSearchProperties props) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.rest = builder.requestFactory(factory).build();
        this.userAgent = props.userAgent();
    }

    @Override public String name() { return "Openverse"; }
    @Override public int priority() { return 1; }
    @Override public boolean available() { return true; }

    @Override
    public List<ImageResult> search(String query, int maxResults) {
        List<ImageResult> out = new ArrayList<>();
        int pages = (maxResults + PAGE_SIZE - 1) / PAGE_SIZE;
        for (int page = 1; page <= pages; page++) {
            Map<String, Object> body;
            try {
                body = rest.get().uri(URL, Map.of("q", query, "page", page))
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .retrieve().body(MAP);
            } catch (HttpClientErrorException e) {
                if (!out.isEmpty()) break; // 일부라도 받았으면 그것으로 진행
                boolean limited = e.getStatusCode().value() == 429;
                throw new ImageSearchException(limited
                        ? "이미지 검색 요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
                        : "이미지 검색에 실패했어요. 잠시 후 다시 시도해 주세요.", limited);
            } catch (RestClientException e) {
                if (!out.isEmpty()) break;
                throw new ImageSearchException("이미지 검색 서버에 연결할 수 없어요.", false);
            }
            List<ImageResult> batch = parse(body);
            out.addAll(batch);
            if (batch.size() < PAGE_SIZE) break; // 마지막 페이지
        }
        return out;
    }

    /** /v1/images/ 응답 → 결과. id 또는 thumbnail 이 없는 항목은 제외 */
    static List<ImageResult> parse(Map<String, Object> body) {
        List<ImageResult> list = new ArrayList<>();
        if (body == null || !(body.get("results") instanceof List<?> results)) return list;
        for (Object o : results) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String id = str(m.get("id"));
            String thumb = str(m.get("thumbnail"));
            if (id == null || thumb == null) continue;
            list.add(new ImageResult(id, str(m.get("title")), thumb, str(m.get("creator")),
                    licenseLabel(str(m.get("license")), str(m.get("license_version"))),
                    str(m.get("foreign_landing_url")), "Openverse"));
        }
        return list;
    }

    static String licenseLabel(String license, String version) {
        if (license == null || license.isBlank()) return "CC";
        String l = license.toLowerCase(Locale.ROOT);
        if (l.equals("cc0")) return "CC0";
        if (l.equals("pdm")) return "Public Domain";
        return "CC " + license.toUpperCase(Locale.ROOT) + (version == null || version.isBlank() ? "" : " " + version);
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }
}
