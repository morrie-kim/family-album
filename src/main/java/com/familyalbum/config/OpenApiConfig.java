package com.familyalbum.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** API 문서 메타데이터. 운영(prod) 프로파일에서는 로드되지 않는다. */
@Configuration
@Profile("!prod")
public class OpenApiConfig {

    @Bean
    OpenAPI familyAlbumOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("가족앨범 API")
                        .version("v1")
                        .description("가족 단위 사진 공유 REST API. 인증: HTTP Basic(이메일/비밀번호). "
                                + "우측 상단 Authorize 에서 로그인 후 Try it out 을 사용하세요."))
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
