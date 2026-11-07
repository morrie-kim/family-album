package com.familyalbum.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** cloudflare 프로파일(휘발성 디스크)에서 데이터가 유지되지 않는 구성이면 기동 시 경고를 남긴다. */
@Component
@Profile("cloudflare")
public class EphemeralStorageWarning implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EphemeralStorageWarning.class);

    private final String dbUrl;
    private final String photoRoot;

    public EphemeralStorageWarning(@Value("${DB_URL:}") String dbUrl, @Value("${app.storage.root}") String photoRoot) {
        this.dbUrl = dbUrl;
        this.photoRoot = photoRoot;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (dbUrl.isBlank()) {
            log.warn("DB_URL 이 설정되지 않아 인메모리 H2 로 동작합니다. 컨테이너가 잠들거나 재시작되면 계정·폴더·월드컵 데이터가 모두 사라집니다.");
        }
        log.warn("사진 파일은 컨테이너 로컬 디스크({})에 저장되며 휘발성입니다. 컨테이너가 잠들면 사진이 사라집니다(R2 등 외부 저장소 연동 필요).", photoRoot);
    }
}
