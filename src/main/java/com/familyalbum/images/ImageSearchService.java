package com.familyalbum.images;

import com.familyalbum.web.ApiException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 주제(검색어)로 인터넷 이미지를 검색한다.
 *  - 제공처 선택: 사용 가능한 제공처 중 priority 가 가장 높은 것(Pexels 키가 있으면 Pexels, 없으면 Openverse)
 *  - 결과 정제: https + 허용된 이미지 호스트만, id 중복 제거, 최대 건수 제한
 *  - 캐시: 같은 검색어는 30분간 재사용(외부 API 호출 한도 보호, 만들기 요청에서 선택 id 를 검증하는 데에도 사용)
 * 클라이언트가 보낸 이미지 URL 은 신뢰하지 않고, 항상 이 서비스의 검색 결과(id)로만 후보를 만든다.
 */
@Service
public class ImageSearchService {

    public record Outcome(String provider, String query, List<ImageResult> results) {}

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_CACHE = 100;
    static final Set<String> ALLOWED_HOST_SUFFIXES = Set.of("openverse.org", "pexels.com");

    private record Cached(Instant at, Outcome outcome) {}

    private final List<ImageSearchProvider> providers;
    private final ImageSearchProperties props;
    private final Clock clock;
    private final Map<String, Cached> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                    return size() > MAX_CACHE;
                }
            });

    public ImageSearchService(List<ImageSearchProvider> providers, ImageSearchProperties props) {
        this(providers, props, Clock.systemUTC());
    }

    ImageSearchService(List<ImageSearchProvider> providers, ImageSearchProperties props, Clock clock) {
        this.providers = providers;
        this.props = props;
        this.clock = clock;
    }

    public Outcome search(String rawQuery) {
        String query = normalize(rawQuery);
        ImageSearchProvider provider = providers.stream().filter(ImageSearchProvider::available)
                .max(Comparator.comparingInt(ImageSearchProvider::priority))
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "사용할 수 있는 이미지 검색 제공처가 없어요."));

        String key = provider.name() + "|" + query.toLowerCase(Locale.ROOT);
        Cached hit = cache.get(key);
        if (hit != null && Duration.between(hit.at(), clock.instant()).compareTo(TTL) < 0) {
            return hit.outcome();
        }

        List<ImageResult> raw;
        try {
            raw = provider.search(query, props.maxResults());
        } catch (ImageSearchException e) {
            throw new ApiException(e.rateLimited() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, e.getMessage());
        }
        Outcome outcome = new Outcome(provider.name(), query, sanitize(raw, props.maxResults()));
        if (!outcome.results().isEmpty()) {
            cache.put(key, new Cached(clock.instant(), outcome));
        }
        return outcome;
    }

    /** 선택한 id 들을 검색 결과에서 찾아 선택 순서대로 반환. 하나라도 없으면(캐시 만료 등) 400 */
    public List<ImageResult> resolve(String query, Collection<String> ids) {
        Map<String, ImageResult> byId = new LinkedHashMap<>();
        search(query).results().forEach(r -> byId.put(r.id(), r));
        List<ImageResult> picked = new ArrayList<>();
        for (String id : new LinkedHashSet<>(ids == null ? List.<String>of() : ids)) {
            ImageResult r = byId.get(id);
            if (r == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "검색 결과가 바뀌었거나 만료됐어요. 다시 검색해 주세요.");
            }
            picked.add(r);
        }
        return picked;
    }

    static String normalize(String raw) {
        String q = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (q.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "검색어(주제)를 입력해 주세요.");
        }
        if (q.length() > 60) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "검색어는 60자 이하여야 해요.");
        }
        return q;
    }

    static List<ImageResult> sanitize(List<ImageResult> raw, int max) {
        Map<String, ImageResult> unique = new LinkedHashMap<>();
        for (ImageResult r : raw) {
            if (r.id() == null || !allowedImageUrl(r.thumbUrl())) continue;
            String title = r.title() == null ? null : trim(r.title().strip(), 100);
            String source = r.sourceUrl() != null && r.sourceUrl().startsWith("https://") ? trim(r.sourceUrl(), 1000) : null;
            unique.putIfAbsent(r.id(), new ImageResult(r.id(), title, r.thumbUrl(),
                    r.creator() == null ? null : trim(r.creator().strip(), 100), r.license(), source, r.provider()));
            if (unique.size() >= max) break;
        }
        return List.copyOf(unique.values());
    }

    /** https 이고 호스트가 허용 목록(검색 제공처 도메인)일 때만 화면에 노출/저장 */
    static boolean allowedImageUrl(String url) {
        if (url == null) return false;
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            if (!"https".equalsIgnoreCase(u.getScheme()) || host == null) return false;
            String h = host.toLowerCase(Locale.ROOT);
            return ALLOWED_HOST_SUFFIXES.stream().anyMatch(s -> h.equals(s) || h.endsWith("." + s));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
