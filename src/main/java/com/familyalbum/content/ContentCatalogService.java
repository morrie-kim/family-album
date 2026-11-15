package com.familyalbum.content;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 콘텐츠 검색의 출처 링크를 구성한다.
 *
 * KOBIS/TMDB API 및 허가가 확인되지 않은 사이트 자동 수집은 사용하지 않는다.
 * 나무위키는 검색 링크만 제공하며 문서 본문·이미지·데이터를 복제하지 않는다.
 * robots.txt와 이용약관 확인을 마친 출처만 추후 수집기 allowlist에 추가한다.
 */
@Service
public class ContentCatalogService {
    private static final Set<String> KINDS = Set.of("all", "movie", "tv", "person");

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

        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
        String googleQuery = switch (mode) {
            case "movie" -> q + " 영화 출연진 개봉일";
            case "tv" -> q + " 드라마 예능 출연진 방영일";
            case "person" -> q + " 배우 출연 작품 필모그래피";
            default -> q + " 영화 드라마 예능 배우";
        };
        String googleEncoded = URLEncoder.encode(googleQuery, StandardCharsets.UTF_8);

        List<Map<String, String>> links = List.of(
                Map.of("name", "나무위키", "url",
                        "https://namu.wiki/Search?q=" + encoded,
                        "description", "원문 검색 결과로 이동합니다. 가족앨범은 문서 내용을 복제하지 않습니다."),
                Map.of("name", "웹 전체 검색", "url",
                        "https://www.google.com/search?q=" + googleEncoded,
                        "description", "방송사·제작사·배급사 등 공개 출처를 확인하세요.")
        );

        return Map.of(
                "results", List.of(),
                "total", 0,
                "query", q,
                "kind", mode,
                "source", "EXTERNAL_SEARCH_LINKS",
                "searchLinks", links,
                "crawlerStatus", "DISABLED_PENDING_SOURCE_PERMISSION_REVIEW",
                "ottAvailability", "NOT_VERIFIED"
        );
    }
}
