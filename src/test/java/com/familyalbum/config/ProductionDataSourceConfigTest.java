package com.familyalbum.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductionDataSourceConfigTest {

    @Test
    void acceptsJdbcPostgresUrlWithoutModification() {
        String url = "jdbc:postgresql://host:5432/album";
        assertEquals(url, ProductionDataSourceConfig.toJdbcUrl(url));
    }

    @Test
    void convertsPostgresqlUrlToJdbcUrl() {
        assertEquals("jdbc:postgresql://host:5432/album?sslmode=require",
                ProductionDataSourceConfig.toJdbcUrl("postgresql://host:5432/album?sslmode=require"));
    }

    @Test
    void convertsRenderPostgresUrlToJdbcUrl() {
        assertEquals("jdbc:postgresql://host:5432/album",
                ProductionDataSourceConfig.toJdbcUrl("postgres://host:5432/album"));
    }

    @Test
    void preservesJdbcH2UrlForProdProfileTests() {
        String url = "jdbc:h2:mem:swagger-test;MODE=PostgreSQL";
        assertEquals(url, ProductionDataSourceConfig.toJdbcUrl(url));
    }

    @Test
    void rejectsUnsupportedUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> ProductionDataSourceConfig.toJdbcUrl("mysql://host/album"));
    }
}
