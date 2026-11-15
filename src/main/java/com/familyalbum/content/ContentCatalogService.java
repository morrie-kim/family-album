package com.familyalbum.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TMDB 기반 콘텐츠 검색 및 대한민국 OTT 제공 정보 조회.
 * 검색 결과는 최신 정보가 중요하므로 캐시하지 않고 요청 시 조회한다.
 */
@Service
public class ContentCatalogService {
    private static final String BASE = "https://api.themoviedb.org/3";
    private final RestClient client;
    private final String apiKey;

    public ContentCatalogService(RestClient.Builder builder,
                                 @Value("${app.tmdb.api-key:}") String apiKey) {
        this.client = builder.build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Map<String, Object> search(String query, String kind) {
        requireKey();
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 2자 이상 입력해 주세요.");
        String mode = kind == null ? "all" : kind.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> items = new ArrayList<>();
        if ("person".equals(mode)) {
            JsonNode people = get("/search/person", Map.of("query", q, "include_adult", "false"));
            for (JsonNode person : nodes(people, "results").stream().limit(5).toList()) {
                long id = person.path("id").asLong();
                String personName = text(person, "name");
                JsonNode credits = get("/person/" + id + "/combined_credits", Map.of());
                List<JsonNode> works = new ArrayList<>();
                nodes(credits, "cast").forEach(works::add);
                nodes(credits, "crew").forEach(works::add);
                works.stream().filter(w -> "movie".equals(w.path("media_type").asText()) || "tv".equals(w.path("media_type").asText()))
                    .sorted(Comparator.comparing((JsonNode w) -> date(w)).reversed())
                    .distinct().limit(10).forEach(w -> {
                        Map<String,Object> item = enrich(w, w.path("media_type").asText());
                        item.put("matchedPerson", personName);
                        String role = text(w, "character");
                        if (role.isBlank()) role = text(w, "job");
                        item.put("matchedRole", role);
                        items.add(item);
                    });
            }
        } else {
            String endpoint = "movie".equals(mode) ? "/search/movie" : "tv".equals(mode) ? "/search/tv" : "/search/multi";
            JsonNode result = get(endpoint, Map.of("query", q, "include_adult", "false"));
            for (JsonNode node : nodes(result, "results").stream().limit(12).toList()) {
                String mediaType = "movie".equals(mode) ? "movie" : "tv".equals(mode) ? "tv" : node.path("media_type").asText();
                if (!"movie".equals(mediaType) && !"tv".equals(mediaType)) continue;
                items.add(enrich(node, mediaType));
            }
        }
        return Map.of("results", items, "total", items.size(), "source", "TMDB");
    }

    private Map<String,Object> enrich(JsonNode node, String type) {
        long id = node.path("id").asLong();
        JsonNode detail = get("/" + type + "/" + id, Map.of("append_to_response", "credits"));
        JsonNode providers = get("/" + type + "/" + id + "/watch/providers", Map.of());
        JsonNode kr = providers.path("results").path("KR");
        String release = type.equals("movie") ? text(detail, "release_date") : text(detail, "first_air_date");
        String status = text(detail, "status");
        String state = state(type, status, release);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("mediaType", type);
        out.put("mediaLabel", type.equals("movie") ? "영화" : "TV 프로그램");
        out.put("title", first(text(detail,"title"), text(detail,"name"), text(node,"title"), text(node,"name")));
        out.put("originalTitle", first(text(detail,"original_title"), text(detail,"original_name")));
        out.put("overview", first(text(detail,"overview"), text(node,"overview")));
        out.put("releaseDate", release);
        out.put("year", release.length() >= 4 ? release.substring(0,4) : "");
        out.put("status", state);
        out.put("statusLabel", stateLabel(state));
        out.put("posterUrl", image(text(detail,"poster_path"), "w342"));
        out.put("backdropUrl", image(text(detail,"backdrop_path"), "w780"));
        out.put("rating", detail.path("vote_average").isNumber() ? detail.path("vote_average").asDouble() : 0);
        out.put("genres", nodes(detail,"genres").stream().map(g -> text(g,"name")).filter(s -> !s.isBlank()).toList());
        List<Map<String,Object>> cast = new ArrayList<>();
        for (JsonNode person : nodes(detail.path("credits"),"cast").stream().limit(8).toList()) {
            cast.add(Map.of("name", text(person,"name"), "role", text(person,"character")));
        }
        out.put("cast", cast);
        out.put("providers", providerGroups(kr));
        out.put("providerLink", text(kr,"link"));
        out.put("ottChecked", true);
        return out;
    }

    private List<Map<String,Object>> providerGroups(JsonNode kr) {
        List<Map<String,Object>> out = new ArrayList<>();
        for (String key : List.of("flatrate","free","ads","rent","buy")) {
            String label = switch (key) {
                case "flatrate" -> "구독 스트리밍";
                case "free" -> "무료";
                case "ads" -> "광고 포함";
                case "rent" -> "대여";
                default -> "구매";
            };
            for (JsonNode p : nodes(kr,key)) {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("name", text(p,"provider_name"));
                row.put("logoUrl", image(text(p,"logo_path"),"w92"));
                row.put("category", label);
                out.add(row);
            }
        }
        return out;
    }

    private String state(String type, String status, String date) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate release = null;
        try { if (!date.isBlank()) release = LocalDate.parse(date); } catch (Exception ignored) {}
        if (release != null && release.isAfter(today)) return "upcoming";
        if ("movie".equals(type)) return release == null ? "unknown" : "ended";
        if ("Ended".equals(status) || "Canceled".equals(status)) return "ended";
        if ("Returning Series".equals(status) || "In Production".equals(status) || "Planned".equals(status)) return "airing";
        return release == null ? "unknown" : "ended";
    }

    private JsonNode get(String path, Map<String,String> params) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(BASE + path).queryParam("api_key", apiKey).queryParam("language","ko-KR");
        params.forEach(uri::queryParam);
        return client.get().uri(uri.build().encode().toUri()).retrieve().body(JsonNode.class);
    }

    private void requireKey() {
        if (apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "TMDB_API_KEY 설정이 필요합니다. .env에 발급받은 TMDB API 키를 등록해 주세요.");
    }
    private static List<JsonNode> nodes(JsonNode node, String field) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode arr = node.path(field);
        if (arr.isArray()) arr.forEach(out::add);
        return out;
    }
    private static String text(JsonNode n, String field) { return n.path(field).asText(""); }
    private static String first(String... values) { for (String v : values) if (v != null && !v.isBlank()) return v; return ""; }
    private static String date(JsonNode n) { return first(text(n,"release_date"),text(n,"first_air_date")); }
    private static String image(String path, String size) { return path.isBlank() ? "" : "https://image.tmdb.org/t/p/"+size+path; }
    private static String stateLabel(String state) {
        return switch(state) { case "airing" -> "방영 중"; case "upcoming" -> "방영/개봉 예정"; case "ended" -> "종영/개봉 완료"; default -> "정보 확인 필요"; };
    }
}
