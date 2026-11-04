package com.familyalbum.images;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderParsingTest {

    @Test
    void openverseResponseIsMappedAndItemsWithoutThumbnailAreSkipped() {
        Map<String, Object> body = Map.of("results", List.of(
                Map.of("id", "8dac0234-5511-43f8-bcc7-0c6c0c41c83d", "title", "Jeju coast",
                        "thumbnail", "https://api.openverse.org/v1/images/8dac0234-5511-43f8-bcc7-0c6c0c41c83d/thumb/",
                        "creator", "Kim", "license", "by-sa", "license_version", "4.0",
                        "foreign_landing_url", "https://www.flickr.com/photos/x/1"),
                Map.of("id", "no-thumb", "title", "skipped"),
                Map.of("id", "cc0-item", "thumbnail", "https://api.openverse.org/v1/images/cc0-item/thumb/",
                        "license", "cc0")));

        List<ImageResult> list = OpenverseProvider.parse(body);

        assertEquals(2, list.size());
        assertEquals("CC BY-SA 4.0", list.get(0).license());
        assertEquals("© Kim · CC BY-SA 4.0 · Openverse", list.get(0).credit());
        assertEquals("CC0", list.get(1).license());
        assertEquals("CC0 · Openverse", list.get(1).credit());
    }

    @Test
    void openverseLicenseLabels() {
        assertEquals("CC BY 2.0", OpenverseProvider.licenseLabel("by", "2.0"));
        assertEquals("CC BY-NC-ND", OpenverseProvider.licenseLabel("by-nc-nd", null));
        assertEquals("Public Domain", OpenverseProvider.licenseLabel("pdm", "1.0"));
        assertEquals("CC", OpenverseProvider.licenseLabel(null, null));
    }

    @Test
    void emptyOrMalformedBodiesGiveEmptyList() {
        assertTrue(OpenverseProvider.parse(null).isEmpty());
        assertTrue(OpenverseProvider.parse(Map.of("results", "oops")).isEmpty());
        assertTrue(PexelsProvider.parse(Map.of()).isEmpty());
    }

    @Test
    void pexelsResponseUsesLargeSourceAndKeepsAttribution() {
        Map<String, Object> body = Map.of("photos", List.of(
                Map.of("id", 123, "alt", "Cat on a sofa", "photographer", "Lee",
                        "url", "https://www.pexels.com/photo/cat-123/",
                        "src", Map.of("large", "https://images.pexels.com/photos/123/pexels-photo-123.jpeg?w=940",
                                "medium", "https://images.pexels.com/photos/123/m.jpeg")),
                Map.of("id", 124, "src", Map.of("medium", "https://images.pexels.com/photos/124/m.jpeg"))));

        List<ImageResult> list = PexelsProvider.parse(body);

        assertEquals(1, list.size()); // large 가 없는 항목은 제외
        assertEquals("pexels-123", list.get(0).id());
        assertEquals("© Lee · Pexels License · Pexels", list.get(0).credit());
        assertEquals("https://www.pexels.com/photo/cat-123/", list.get(0).sourceUrl());
    }
}
