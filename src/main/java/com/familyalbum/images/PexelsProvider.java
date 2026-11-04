package com.familyalbum.images;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Pexels — 고화질 스톡 사진. 무료 API 키(PEXELS_API_KEY)가 있을 때만 사용되며, 한글 검색(locale=ko-KR)을 지원한다.
 * Pexels 라이선스상 출처(Pexels 링크) 표기를 유지해야 하므로 credit 에 제공처를 항상 포함한다.
 */
@Component
public class PexelsProvider implements ImageSearchProvider {

    private static final String URL = "https://api.pexels.com/v1/search?query={q}&per_page={n}&locale=ko-KR";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final ImageSearchProperties props;

    public PexelsProvider(RestClient.Builder builder, ImageSearchProperties props) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.rest = builder.requestFactory(factory).build();
        this.props = props;
    }

    @Override public String name() { return "Pexels"; }
    @Override public int priority() { return 2; }
    @Override public boolean available() { return props.pexelsApiKey() != null && !props.pexelsApiKey().isBlank(); }

    @Override
    public List<ImageResult> search(String query, int maxResults) {
        try {
            Map<String, Object> body = rest.get()
                    .uri(URL, Map.of("q", query, "n", Math.min(Math.max(maxResults, 1), 80)))
                    .header(HttpHeaders.AUTHORIZATION, props.pexelsApiKey())
                    .header(HttpHeaders.USER_AGENT, props.userAgent())
                    .retrieve().body(MAP);
            return parse(body);
        } catch (HttpClientErrorException e) {
            boolean limited = e.getStatusCode().value() == 429;
            throw new ImageSearchException(limited
                    ? "이미지 검색 요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
                    : "이미지 검색에 실패했어요. API 키 설정을 확인해 주세요.", limited);
        } catch (RestClientException e) {
            throw new ImageSearchException("이미지 검색 서버에 연결할 수 없어요.", false);
        }
    }

    /** /v1/search 응답 → 결과. 화면용으로 src.large(가로 940px)를 사용 */
    static List<ImageResult> parse(Map<String, Object> body) {
        List<ImageResult> list = new ArrayList<>();
        if (body == null || !(body.get("photos") instanceof List<?> photos)) return list;
        for (Object o : photos) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object id = m.get("id");
            Object src = m.get("src");
            String url = src instanceof Map<?, ?> s && s.get("large") != null ? s.get("large").toString() : null;
            if (id == null || url == null) continue;
            String alt = m.get("alt") == null ? null : m.get("alt").toString();
            String photographer = m.get("photographer") == null ? null : m.get("photographer").toString();
            String page = m.get("url") == null ? null : m.get("url").toString();
            list.add(new ImageResult("pexels-" + id, alt == null || alt.isBlank() ? null : alt, url,
                    photographer, "Pexels License", page, "Pexels"));
        }
        return list;
    }
}
