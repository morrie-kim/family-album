package com.familyalbum.images;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.familyalbum.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ImageSearchServiceTest {

    /** 테스트용 시계 */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class FakeProvider implements ImageSearchProvider {
        final String name; final int priority; final boolean available; final List<ImageResult> data;
        int calls = 0;
        FakeProvider(String name, int priority, boolean available, List<ImageResult> data) {
            this.name = name; this.priority = priority; this.available = available; this.data = data;
        }
        @Override public String name() { return name; }
        @Override public int priority() { return priority; }
        @Override public boolean available() { return available; }
        @Override public List<ImageResult> search(String query, int max) { calls++; return new ArrayList<>(data); }
    }

    private static ImageResult r(String id, String url) {
        return new ImageResult(id, "title " + id, url, "Kim", "CC BY 4.0", "https://example.org/" + id, "Openverse");
    }

    private final ImageSearchProperties props = new ImageSearchProperties("", "test", 60);

    @Test
    void dropsNonHttpsUnknownHostsAndDuplicates() {
        FakeProvider p = new FakeProvider("Openverse", 1, true, List.of(
                r("a", "https://api.openverse.org/v1/images/a/thumb/"),
                r("a", "https://api.openverse.org/v1/images/a/thumb/"),                 // 중복 id
                r("b", "http://api.openverse.org/v1/images/b/thumb/"),                  // http
                r("c", "https://evil.example.com/c.jpg"),                               // 허용되지 않은 호스트
                r("d", "https://api.openverse.org.evil.com/d.jpg"),                     // 접미사 위장
                r("e", "https://images.pexels.com/photos/e.jpeg")));
        ImageSearchService service = new ImageSearchService(List.of(p), props, new MutableClock());

        List<ImageResult> results = service.search("  제주   바다 ").results();

        assertEquals(List.of("a", "e"), results.stream().map(ImageResult::id).toList());
    }

    @Test
    void sameQueryIsServedFromCacheUntilTtlExpires() {
        FakeProvider p = new FakeProvider("Openverse", 1, true, List.of(r("a", "https://api.openverse.org/v1/images/a/thumb/")));
        MutableClock clock = new MutableClock();
        ImageSearchService service = new ImageSearchService(List.of(p), props, clock);

        service.search("cat");
        service.search("CAT");                         // 대소문자 무시, 캐시 적중
        assertEquals(1, p.calls);

        clock.now = clock.now.plus(Duration.ofMinutes(31));
        service.search("cat");
        assertEquals(2, p.calls);
    }

    @Test
    void emptyResultsAreNotCachedSoRetryHitsTheProviderAgain() {
        FakeProvider p = new FakeProvider("Openverse", 1, true, List.of());
        ImageSearchService service = new ImageSearchService(List.of(p), props, new MutableClock());

        assertTrue(service.search("nothing").results().isEmpty());
        service.search("nothing");
        assertEquals(2, p.calls);
    }

    @Test
    void prefersAvailableProviderWithHighestPriority() {
        FakeProvider openverse = new FakeProvider("Openverse", 1, true, List.of(r("o", "https://api.openverse.org/v1/images/o/thumb/")));
        FakeProvider pexelsOff = new FakeProvider("Pexels", 2, false, List.of(r("p", "https://images.pexels.com/p.jpeg")));
        assertEquals("Openverse", new ImageSearchService(List.of(openverse, pexelsOff), props, new MutableClock()).search("x").provider());

        FakeProvider pexelsOn = new FakeProvider("Pexels", 2, true, List.of(r("p", "https://images.pexels.com/p.jpeg")));
        assertEquals("Pexels", new ImageSearchService(List.of(openverse, pexelsOn), props, new MutableClock()).search("x").provider());
    }

    @Test
    void resolveKeepsSelectionOrderAndRejectsUnknownIds() {
        FakeProvider p = new FakeProvider("Openverse", 1, true, List.of(
                r("a", "https://api.openverse.org/v1/images/a/thumb/"),
                r("b", "https://api.openverse.org/v1/images/b/thumb/"),
                r("c", "https://api.openverse.org/v1/images/c/thumb/")));
        ImageSearchService service = new ImageSearchService(List.of(p), props, new MutableClock());

        assertEquals(List.of("c", "a"), service.resolve("cat", List.of("c", "a", "c")).stream().map(ImageResult::id).toList());

        ApiException e = assertThrows(ApiException.class, () -> service.resolve("cat", List.of("a", "forged")));
        assertEquals(HttpStatus.BAD_REQUEST, e.status());
    }

    @Test
    void validatesQueryAndMapsProviderFailures() {
        ImageSearchService ok = new ImageSearchService(
                List.of(new FakeProvider("Openverse", 1, true, List.of())), props, new MutableClock());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> ok.search("   ")).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> ok.search("x".repeat(61))).status());

        ImageSearchProvider limited = new FakeProvider("Openverse", 1, true, List.of()) {
            @Override public List<ImageResult> search(String q, int max) { throw new ImageSearchException("잠시 후", true); }
        };
        ApiException e = assertThrows(ApiException.class,
                () -> new ImageSearchService(List.of(limited), props, new MutableClock()).search("cat"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.status());

        ApiException none = assertThrows(ApiException.class,
                () -> new ImageSearchService(List.of(new FakeProvider("Pexels", 2, false, List.of())), props, new MutableClock()).search("cat"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, none.status());
    }

    @Test
    void allowedImageUrlRules() {
        assertTrue(ImageSearchService.allowedImageUrl("https://api.openverse.org/v1/images/x/thumb/"));
        assertTrue(ImageSearchService.allowedImageUrl("https://images.pexels.com/photos/1/a.jpeg"));
        assertFalse(ImageSearchService.allowedImageUrl("http://api.openverse.org/x"));
        assertFalse(ImageSearchService.allowedImageUrl("https://notopenverse.org/x"));
        assertFalse(ImageSearchService.allowedImageUrl("javascript:alert(1)"));
        assertFalse(ImageSearchService.allowedImageUrl(null));
    }
}
