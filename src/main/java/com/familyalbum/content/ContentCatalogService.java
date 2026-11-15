package com.familyalbum.content;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 콘텐츠 검색의 출처 및 OTT별 외부 검색 링크를 구성한다.
 *
 * KOBIS/TMDB API 및 허가가 확인되지 않은 사이트 자동 수집은 사용하지 않는다.
 * 나무위키는 검색 링크만 제공하며 문서 본문·이미지·데이터를 복제하지 않는다.
 * OTT 링크는 작품 제공 여부를 보증하지 않으며, 검색 결과에서 사용자가 직접 확인한다.
 */
@Service
public class ContentCatalogService {
    private static final Set<String> KINDS = Set.of("all", "movie", "tv", "person");

    private record OttProvider(String name, String domain) {}

    private static final List<OttProvider> VOD_PROVIDERS = List.of(
            new OttProvider("넷플릭스", "netflix.com"),
            new OttProvider("티빙", "tving.com"),
            new OttProvider("웨이브", "wavve.com"),
            new OttProvider("디즈니+", "disneyplus.com"),
            new OttProvider("쿠팡플레이", "coupangplay.com"),
            new OttProvider("왓챠", "watcha.com"),
            new OttProvider("Apple TV+", "tv.apple.com"),
            new OttProvider("라프텔", "laftel.net")
    );

    private static final List<OttProvider> LIVE_PROVIDERS = List.of(
            new OttProvider("티빙", "tving.com"),
            new OttProvider("웨이브", "wavve.com")
    );

    public Map<String, Object> search(String query, String kind) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "검색어를 2자 이상 입력해 주세요.");
        }

        String mode = kind == null ? "all" : kind.toLowerCase(Locale.ROOT);
        if (!KINDS.contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원하지 않는 검색 유형입니다.");
        }

        String encoded = encode(q);
        String googleQuery = switch (mode) {
            case "movie" -> q + " 영화 출연진 개봉일";
            case "tv" -> q + " 드라마 예능 출연진 방영일";
            case "person" -> q + " 배우 출연 작품 필모그래피";
            default -> q + " 영화 드라마 예능 배우";
        };
        String googleEncoded = encode(googleQuery);

        List<Map<String, String>> links = List.of(
                link("나무위키", "https://namu.wiki/Search?q=" + encoded,
                        "원문 검색 결과로 이동합니다. 가족앨범은 문서 내용을 복제하지 않습니다."),
                link("웹 전체 검색", "https://www.google.com/search?q=" + googleEncoded,
                        "방송사·제작사·배급사 등 공개 출처를 확인하세요.")
        );

        return Map.of(
                "results", List.of(),
                "total", 0,
                "query", q,
                "kind", mode,
                "source", "EXTERNAL_SEARCH_LINKS",
                "searchLinks", links,
                "vodLinks", createOttLinks(q, VOD_PROVIDERS, "다시보기"),
                "liveLinks", createOttLinks(q, LIVE_PROVIDERS, "실시간 방송"),
                "crawlerStatus", "DISABLED_PENDING_SOURCE_PERMISSION_REVIEW",
                "ottAvailability", "NOT_VERIFIED"
        );
    }

    private List<Map<String, String>> createOttLinks(
            String query, List<OttProvider> providers, String viewingType) {
        List<Map<String, String>> links = new ArrayList<>();
        for (OttProvider provider : providers) {
            String searchQuery = query + " " + viewingType + " site:" + provider.domain();
            String url = "https://www.google.com/search?q=" + encode(searchQuery);
            links.add(link(provider.name(), url,
                    provider.name() + " 관련 " + viewingType + " 검색 결과입니다. 실제 제공 여부는 해당 결과에서 확인하세요."));
        }
        return List.copyOf(links);
    }

    private Map<String, String> link(String name, String url, String description) {
        Map<String, String> link = new LinkedHashMap<>();
        link.put("name", name);
        link.put("url", url);
        link.put("description", description);
        return Map.copyOf(link);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
