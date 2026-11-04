package com.familyalbum.images;

/** 이미지 검색 실패. message 는 사용자에게 그대로 보여줄 수 있는 문장이다. */
public class ImageSearchException extends RuntimeException {

    private final boolean rateLimited;

    public ImageSearchException(String message, boolean rateLimited) {
        super(message);
        this.rateLimited = rateLimited;
    }

    public boolean rateLimited() {
        return rateLimited;
    }
}
