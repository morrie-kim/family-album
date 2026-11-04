package com.familyalbum.images;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 인터넷 이미지 검색 결과 1건. thumbUrl 은 화면/대진에 그대로 쓰는 https 이미지 주소. */
public record ImageResult(String id, String title, String thumbUrl, String creator, String license,
                          String sourceUrl, String provider) {

    /** 출처 표기용 한 줄: "© 작성자 · 라이선스 · 제공처" */
    public String credit() {
        return Stream.of(creator == null || creator.isBlank() ? null : "© " + creator.trim(), license, provider)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" · "));
    }
}
