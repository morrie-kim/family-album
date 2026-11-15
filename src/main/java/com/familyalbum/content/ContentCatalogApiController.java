package com.familyalbum.content;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contents")
public class ContentCatalogApiController {
    private final ContentCatalogService catalog;
    public ContentCatalogApiController(ContentCatalogService catalog) { this.catalog = catalog; }

    /** 이름·작품명 검색. kind: all, movie, tv, person */
    @GetMapping("/search")
    public Map<String,Object> search(@RequestParam String q,
                                     @RequestParam(defaultValue = "all") String kind) {
        return catalog.search(q, kind);
    }
}
