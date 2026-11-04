package com.familyalbum.images;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.images.*
 *  - pexelsApiKey: 있으면 Pexels(고화질, 한글 검색 지원)를 우선 사용. 없으면 키 없이 쓰는 Openverse
 *  - maxResults: 한 번의 검색에서 가져올 최대 결과 수
 */
@ConfigurationProperties("app.images")
public record ImageSearchProperties(
        @DefaultValue("") String pexelsApiKey,
        @DefaultValue("family-album/1.0") String userAgent,
        @DefaultValue("60") int maxResults) {
}
