package com.familyalbum.images;

import java.util.List;

/** 이미지 검색 제공처 추상화. 새 제공처는 이 인터페이스를 구현한 @Component 만 추가하면 된다. */
public interface ImageSearchProvider {

    String name();

    /** 값이 클수록 우선. 사용 가능한(available) 제공처 중 가장 높은 것을 쓴다. */
    int priority();

    boolean available();

    /** 최대 maxResults 건. 첫 요청부터 실패하면 ImageSearchException, 일부만 성공하면 받은 만큼 반환 */
    List<ImageResult> search(String query, int maxResults);
}
