package com.familyalbum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app")
public record AppProperties(Storage storage, Upload upload, Signup signup, Google google, String publicBaseUrl) {

    public record Storage(String root) {}

    /** maxFilesPerRequest: 한 요청에 담을 수 있는 최대 파일 수, maxFileSize: 파일 1장당 최대 크기 */
    public record Upload(int maxFilesPerRequest, DataSize maxFileSize) {}

    /** allowNewFamily=false 면 신규 가족 생성을 막고 초대 코드 가입만 허용(외부 공개 시 권장) */
    public record Signup(boolean allowNewFamily) {}

    /** 구글 포토(Picker API) 연동. clientId/clientSecret 이 비어 있으면 기능이 비활성화된다. */
    public record Google(String clientId, String clientSecret, String tokenKey, int maxPickItems) {
        public boolean enabled() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
