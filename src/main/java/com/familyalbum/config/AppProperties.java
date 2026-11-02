package com.familyalbum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app")
public record AppProperties(Storage storage, Upload upload) {
    public record Storage(String root) {}

    /** maxFilesPerRequest: 한 요청에 담을 수 있는 최대 파일 수, maxFileSize: 파일 1장당 최대 크기 */
    public record Upload(int maxFilesPerRequest, DataSize maxFileSize) {}
}
