package com.familyalbum.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Render PostgreSQL 연결 문자열은 postgres:// 또는 postgresql:// 형식으로 제공될 수 있다.
 * JDBC 드라이버가 요구하는 jdbc:postgresql:// 형식으로 정규화한다.
 */
@Configuration
@Profile("prod")
@ConditionalOnProperty(name = "DB_URL")
public class ProductionDataSourceConfig {

    @Bean
    public DataSource dataSource(
            @Value("${DB_URL}") String configuredUrl,
            @Value("${DB_USERNAME:}") String username,
            @Value("${DB_PASSWORD:}") String password) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(toJdbcUrl(configuredUrl));
        if (!username.isBlank()) {
            config.setUsername(username);
        }
        if (!password.isBlank()) {
            config.setPassword(password);
        }
        config.setPoolName("family-album-pool");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(30_000);
        return new HikariDataSource(config);
    }

    static String toJdbcUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DB_URL 환경변수가 비어 있습니다.");
        }

        String url = value.trim();
        String lower = url.toLowerCase(Locale.ROOT);

        if (lower.startsWith("jdbc:postgresql://")) {
            return url;
        }
        if (lower.startsWith("postgresql://")) {
            return "jdbc:" + url;
        }
        if (lower.startsWith("postgres://")) {
            return "jdbc:postgresql://" + url.substring("postgres://".length());
        }

        throw new IllegalArgumentException(
                "지원하지 않는 DB_URL 형식입니다. jdbc:postgresql://, postgresql:// 또는 postgres:// 형식을 사용하세요.");
    }
}
