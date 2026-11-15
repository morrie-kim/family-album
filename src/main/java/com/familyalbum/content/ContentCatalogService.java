package com.familyalbum.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * KOBIS 기반 국내 영화·영화인 검색.
 *
 * KOBIS는 영화 및 영화인 데이터 제공처이며 TV 드라마·예능이나 국내 OTT 제공 현황은 제공하지 않는다.
 * 검증되지 않은 OTT 정보를 결과에 채우지 않는다.
 */
@Service
public class ContentCatalogService {
    private static final String BASE =
            "https://www.kobis.or.kr/kobisopenapi/webservice/rest";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final RestClient client;
    private final String apiKey;

    public ContentCatalogService(RestClient.Builder builder,
                                 @Value("${app.kobis.api-key:}") String apiKey) {
        this.client = builder.build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Map<String, Object> search(String query, String kind) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "검색어를 2자 이상 입력해 주세요.");
        }

        String mode = kind == null ? "all" : kind.toLowerCase(Locale.ROOT);
        if (!Set.of("all", "movie", "person", "tv").contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 검색 유형입니다.");
        }
        if ("tv".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "KOBIS는 영화·영화인 데이터만 제공합니다. 드라마·예능은 검증 가능한 별도 시리즈 데이터 연동 후 제공할 예정입니다.");
        }

        requireKey();
        List<Map<String, Object>> items =
                "person".equals(mode) ? searchPeople(q) : searchMovies(q);

        return Map.of(
                "results", items,
                "total", items.size(),
                "source", "KOBIS",
                "supportedKinds", List.of("movie", "person"),
                "ottAvailability", "NOT_INTEGRATED"
        );
    }

    private List<Map<String, Object>> searchMovies(String query) {
        JsonNode response = get("/movie/searchMovieList.json",
                Map.of("movieNm", query, "itemPerPage", "10"));
        List<Map<String, Object>> results = new ArrayList<>();

        for (JsonNode movie : children(response.path("movieListResult"), "movieList")) {
            String movieCode = value(movie, "movieCd");
            if (movieCode.isBlank()) continue;
            JsonNode detailResponse = get("/movie/searchMovieInfo.json",
                    Map.of("movieCd", movieCode));
            JsonNode detail = detailResponse.path("movieInfoResult").path("movieInfo");
            results.add(toMovie(detail, movie));
        }
        return results;
    }

    private List<Map<String, Object>> searchPeople(String query) {
        JsonNode response = get("/people/searchPeopleList.json",
                Map.of("peopleNm", query, "itemPerPage", "10"));
        List<Map<String, Object>> results = new ArrayList<>();
        Set<String> seenMovies = new HashSet<>();

        for (JsonNode person : children(response.path("peopleListResult"), "peopleList")) {
            String peopleCode = value(person, "peopleCd");
            String personName = value(person, "peopleNm");
            if (peopleCode.isBlank()) continue;

            JsonNode detailResponse = get("/people/searchPeopleInfo.json",
                    Map.of("peopleCd", peopleCode));
            JsonNode detail = detailResponse.path("peopleInfoResult").path("peopleInfo");
            for (JsonNode film : children(detail.path("filmos"), "filmo")) {
                String movieCode = value(film, "movieCd");
                if (movieCode.isBlank() || !seenMovies.add(movieCode)) continue;

                JsonNode movieResponse = get("/movie/searchMovieInfo.json",
                        Map.of("movieCd", movieCode));
                JsonNode movieDetail = movieResponse.path("movieInfoResult").path("movieInfo");
                Map<String, Object> item = toMovie(movieDetail, film);
                item.put("matchedPerson", personName);
                item.put("matchedRole", value(film, "moviePartNm"));
                results.add(item);
                if (results.size() >= 12) return results;
            }
        }
        return results;
    }

    private Map<String, Object> toMovie(JsonNode detail, JsonNode fallback) {
        String title = first(value(detail, "movieNm"), value(fallback, "movieNm"));
        String release = first(value(detail, "openDt"), value(fallback, "openDt"));
        String state = state(release);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", first(value(detail, "movieCd"), value(fallback, "movieCd")));
        out.put("mediaType", "movie");
        out.put("mediaLabel", "영화");
        out.put("title", title);
        out.put("originalTitle", first(value(detail, "movieNmEn"), value(fallback, "movieNmEn")));
        out.put("overview", "");
        out.put("releaseDate", normalizeDate(release));
        out.put("year", first(value(detail, "prdtYear"), value(fallback, "prdtYear"),
                release.length() >= 4 ? release.substring(0, 4) : ""));
        out.put("status", state);
        out.put("statusLabel", stateLabel(state));
        out.put("posterUrl", "");
        out.put("backdropUrl", "");
        out.put("rating", 0);
        out.put("genres", children(detail.path("genres"), "genre").stream()
                .map(g -> value(g, "genreNm")).filter(s -> !s.isBlank()).distinct().toList());

        List<Map<String, String>> cast = new ArrayList<>();
        for (JsonNode actor : children(detail.path("actors"), "actor").stream().limit(8).toList()) {
            String name = value(actor, "peopleNm");
            if (!name.isBlank()) {
                cast.add(Map.of("name", name, "role", value(actor, "cast")));
            }
        }
        out.put("cast", cast);

        // OTT 제공 정보는 KOBIS 응답에 없으므로 비어 있는 상태를 명시한다.
        out.put("providers", List.of());
        out.put("providerLink", "");
        out.put("ottChecked", false);
        out.put("ottStatus", "NOT_INTEGRATED");
        out.put("dataSource", "KOBIS");
        return out;
    }

    private JsonNode get(String path, Map<String, String> params) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(BASE + path)
                .queryParam("key", apiKey);
        params.forEach(uri::queryParam);
        try {
            JsonNode response = client.get()
                    .uri(uri.build().encode().toUri())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.has("faultInfo")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "KOBIS에서 정상적인 응답을 받지 못했습니다. API 키와 요청 한도를 확인해 주세요.");
            }
            return response;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "KOBIS 인증에 실패했습니다. .env의 KOBIS_API_KEY를 확인해 주세요.");
            }
            if (e.getStatusCode().value() == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "KOBIS 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "KOBIS 영화 정보를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void requireKey() {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "KOBIS API 인증 정보가 없습니다. 프로젝트 루트 .env에 KOBIS_API_KEY를 설정해 주세요.");
        }
    }

    private static List<JsonNode> children(JsonNode parent, String field) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode node = parent.path(field);
        if (node.isArray()) node.forEach(result::add);
        else if (node.isObject()) result.add(node);
        return result;
    }

    private static String value(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String state(String openDate) {
        if (openDate == null || openDate.isBlank()) return "unknown";
        try {
            LocalDate release = LocalDate.parse(openDate, DateTimeFormatter.BASIC_ISO_DATE);
            return release.isAfter(LocalDate.now(KOREA)) ? "upcoming" : "ended";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String normalizeDate(String date) {
        if (date == null || date.length() != 8) return date == null ? "" : date;
        return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
    }

    private static String stateLabel(String state) {
        return switch (state) {
            case "upcoming" -> "개봉 예정";
            case "ended" -> "개봉 완료";
            default -> "정보 확인 필요";
        };
    }
}
