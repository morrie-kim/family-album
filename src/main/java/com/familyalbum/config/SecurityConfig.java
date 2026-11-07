package com.familyalbum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {

    @Value("${springdoc.api-docs.enabled:false}")
    private boolean docsEnabled;

    /**
     * REST API(/api/**): stateless + HTTP Basic. 세션/CSRF 없음.
     * 반드시 HTTPS 뒤에서만 운영할 것(Basic 은 자격증명을 매 요청 전송).
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/auth/signup").permitAll()
                .anyRequest().authenticated())
            // WWW-Authenticate 헤더 없이 401 → 브라우저 기본 로그인 팝업 방지(Swagger UI 에서 중요)
            .httpBasic(b -> b.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /** 화면(Thymeleaf): 세션 폼 로그인 + CSRF(기본 활성) */
    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> {
                a.requestMatchers("/login", "/signup", "/css/**", "/js/**", "/error").permitAll();
                if (docsEnabled) { // 문서가 켜진 환경(dev/stg)에서만 Swagger 경로 허용
                    a.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll();
                }
                a.anyRequest().authenticated();
            })
            .formLogin(f -> f
                .loginPage("/login")
                .defaultSuccessUrl("/photos", true)
                .permitAll())
            .logout(l -> l
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
