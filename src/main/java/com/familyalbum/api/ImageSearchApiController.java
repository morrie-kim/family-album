package com.familyalbum.api;

import com.familyalbum.images.ImageSearchService;
import com.familyalbum.images.ImageResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
public class ImageSearchApiController {
    private final ImageSearchService search;

    public ImageSearchApiController(ImageSearchService search) {
        this.search = search;
    }

    @GetMapping("/search")
    public ImageSearchService.Outcome search(@RequestParam String q) {
        return search.search(q);
    }
}
