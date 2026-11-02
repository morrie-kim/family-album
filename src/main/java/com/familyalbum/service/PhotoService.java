package com.familyalbum.service;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.Photo;
import com.familyalbum.domain.Visibility;
import com.familyalbum.repository.AppUserRepository;
import com.familyalbum.repository.PhotoRepository;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.Dtos.PageResponse;
import com.familyalbum.web.Dtos.PhotoResponse;
import com.familyalbum.web.ApiException;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoService {

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "image/heic", ".heic");

    public record FileContent(Resource resource, String contentType, String filename) {}

    private final PhotoRepository photos;
    private final AppUserRepository users;
    private final FileStorage storage;
    private final AppProperties props;

    public PhotoService(PhotoRepository photos, AppUserRepository users, FileStorage storage, AppProperties props) {
        this.photos = photos;
        this.users = users;
        this.storage = storage;
        this.props = props;
    }

    @Transactional
    public PhotoResponse upload(AppPrincipal me, MultipartFile file, String description, Visibility visibility) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        String contentType = file.getContentType() == null ? null : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = contentType == null ? null : ALLOWED.get(contentType);
        if (ext == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식입니다.");
        }
        long maxBytes = props.upload().maxFileSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "파일 크기는 " + (maxBytes / (1024 * 1024)) + "MB 이하여야 합니다.");
        }
        if (description != null && description.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "설명은 500자 이하여야 합니다.");
        }

        AppUser uploader = users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        String original = StringUtils.getFilename(StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (original == null || original.isBlank()) {
            original = "photo" + ext;
        }
        if (original.length() > 255) {
            original = original.substring(original.length() - 255);
        }

        String stored = storage.store(me.familyId(), file, ext);
        try {
            Photo saved = photos.save(new Photo(
                    uploader.getFamily(), uploader, original, stored, contentType, file.getSize(),
                    StringUtils.hasText(description) ? description.trim() : null,
                    visibility == null ? Visibility.FAMILY : visibility));
            return toResponse(saved, me.userId());
        } catch (RuntimeException e) {
            storage.delete(me.familyId(), stored);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PhotoResponse> list(AppPrincipal me, int page, int size) {
        Page<Photo> result = photos.findVisibleTo(me.familyId(), me.userId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 60)));
        return new PageResponse<>(
                result.getContent().stream().map(p -> toResponse(p, me.userId())).toList(),
                result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public FileContent file(AppPrincipal me, Long id) {
        Photo p = findReadable(me, id);
        return new FileContent(storage.load(p.getFamily().getId(), p.getStoredFilename()),
                p.getContentType(), p.getOriginalFilename());
    }

    @Transactional
    public void delete(AppPrincipal me, Long id) {
        Photo p = findReadable(me, id);
        if (!p.getUploader().getId().equals(me.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "본인이 올린 사진만 삭제할 수 있습니다.");
        }
        photos.delete(p);
        storage.delete(p.getFamily().getId(), p.getStoredFilename());
    }

    /** 다른 가족/타인의 비공개 사진은 존재 여부를 숨기기 위해 404 로 응답 */
    private Photo findReadable(AppPrincipal me, Long id) {
        Photo p = photos.findWithUploaderById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));
        boolean sameFamily = p.getFamily().getId().equals(me.familyId());
        boolean visible = p.getVisibility() == Visibility.FAMILY || p.getUploader().getId().equals(me.userId());
        if (!sameFamily || !visible) {
            throw new ApiException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다.");
        }
        return p;
    }

    private PhotoResponse toResponse(Photo p, Long meId) {
        return new PhotoResponse(p.getId(), p.getOriginalFilename(), p.getDescription(), p.getVisibility(),
                p.getFileSize(), p.getUploader().getId(), p.getUploader().getName(), p.getCreatedAt(),
                p.getUploader().getId().equals(meId));
    }
}
