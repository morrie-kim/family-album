package com.familyalbum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FamilyAlbumApplication {
    public static void main(String[] args) {
        SpringApplication.run(FamilyAlbumApplication.class, args);
    }
}
