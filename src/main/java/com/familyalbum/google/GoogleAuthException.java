package com.familyalbum.google;

/** 구글 인증이 더 이상 유효하지 않음(토큰 폐기/만료). 사용자가 다시 연결해야 한다. */
public class GoogleAuthException extends RuntimeException {
    public GoogleAuthException(String message) {
        super(message);
    }
}
