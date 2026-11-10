package com.familyalbum.service;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.PhotoContent;
import com.familyalbum.repository.PhotoContentRepository;
import com.familyalbum.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 우선 사진 저장소.
 * Render Free 인스턴스의 로컬 파일시스템은 재시작/슬립 후 초기화되므로 사진 본문은 DB에 저장한다.
 * 기존 VPS에서 사용하던 로컬 파일은 DB에 없을 때 fallback으로 읽고 삭제한다.
 */
@Component
public class FileStorage {

    private final Path legacyRoot;
    private final PhotoContentRepository contents;

    public FileStorage(AppProperties props, PhotoContentRepository contents) {
        this.legacyRoot = Path.of(props.storage().root()).toAbsolutePath().normalize();
        this.contents = contents;
    }

    public String store(Long familyId, InputStreamSource source, String extension) {
        try (InputStream in = source.getInputStream()) {
            String name = UUID.randomUUID() + extension;
            contents.save(new PhotoContent(familyId, name, in.readAllBytes()));
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Resource load(Long familyId, String storedName) {
        return contents.findByFamilyIdAndStoredFilename(familyId, storedName)
                .<Resource>map(content -> new ByteArrayResource(content.getContent()))
                .orElseGet(() -> loadLegacyFile(familyId, storedName));
    }

    private Resource loadLegacyFile(Long familyId, String storedName) {
        Path p = legacyRoot.resolve(String.valueOf(familyId)).resolve(storedName).normalize();
        if (!p.startsWith(legacyRoot) || !Files.isReadable(p)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        return new FileSystemResource(p);
    }

    public void delete(Long familyId, String storedName) {
        contents.deleteByFamilyIdAndStoredFilename(familyId, storedName);
        try {
            Path p = legacyRoot.resolve(String.valueOf(familyId)).resolve(storedName).normalize();
            if (p.startsWith(legacyRoot)) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // 기존 로컬 저장소의 고아 파일은 별도 정리 작업으로 처리
        }
    }
}
