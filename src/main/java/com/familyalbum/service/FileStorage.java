package com.familyalbum.service;

import com.familyalbum.config.AppProperties;
import com.familyalbum.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 로컬 디스크 저장소. 가족(familyId)별 디렉터리로 분리. S3 등으로 교체 시 이 클래스만 바꾸면 됨. */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(AppProperties props) {
        this.root = Path.of(props.storage().root()).toAbsolutePath().normalize();
    }

    public String store(Long familyId, MultipartFile file, String extension) {
        try {
            Path dir = root.resolve(String.valueOf(familyId));
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + extension;
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(name));
            }
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Resource load(Long familyId, String storedName) {
        Path p = root.resolve(String.valueOf(familyId)).resolve(storedName).normalize();
        if (!p.startsWith(root) || !Files.isReadable(p)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        return new FileSystemResource(p);
    }

    public void delete(Long familyId, String storedName) {
        try {
            Path p = root.resolve(String.valueOf(familyId)).resolve(storedName).normalize();
            if (p.startsWith(root)) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // 고아 파일은 별도 정리 작업으로 처리
        }
    }
}
