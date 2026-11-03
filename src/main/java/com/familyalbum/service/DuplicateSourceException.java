package com.familyalbum.service;

import com.familyalbum.web.ApiException;
import org.springframework.http.HttpStatus;

/** 이미 가져온 외부 사진(sourceId 중복) */
public class DuplicateSourceException extends ApiException {
    public DuplicateSourceException() {
        super(HttpStatus.CONFLICT, "이미 가져온 사진입니다.");
    }
}
