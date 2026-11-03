package com.familyalbum.service;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.Folder;
import com.familyalbum.domain.Permission;
import com.familyalbum.domain.Photo;
import com.familyalbum.repository.AppUserRepository;
import com.familyalbum.repository.PhotoRepository;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.ApiException;
import com.familyalbum.web.Dtos.PageResponse;
import com.familyalbum.web.Dtos.PhotoResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.InputStreamSource;
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

    /** 업로드(multipart)·외부 가져오기(구글 포토)를 같은 경로로 저장하기 위한 입력 표현 */
    public record IncomingFile(String filename, String contentType, long size, String sourceId,
                               InputStreamSource content) {}

    public record FileContent(Resource resource, String contentType, String filename) {}

    private final PhotoRepository photos;
    private final AppUserRepository users;
    private final FolderAccessService access;
    private final FolderService folderService;
    private final FileStorage storage;
    private final AppProperties props;

    public PhotoService(PhotoRepository photos, AppUserRepository users, FolderAccessService access,
                        FolderService folderService, FileStorage storage, AppProperties props) {
        this.photos = photos;
        this.users = users;
        this.access = access;
        this.folderService = folderService;
        this.storage = storage;
        this.props = props;
    }

    @Transactional
    public PhotoResponse upload(AppPrincipal me, MultipartFile file, String description, Long folderId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        return store(me, new IncomingFile(file.getOriginalFilename(), file.getContentType(), file.getSize(), null, file),
                description, folderId);
    }

    @Transactional
    public PhotoResponse store(AppPrincipal me, IncomingFile in, String description, Long folderId) {
        if (in.size() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }
        String contentType = in.contentType() == null ? null : in.contentType().toLowerCase(Locale.ROOT);
        String ext = contentType == null ? null : ALLOWED.get(contentType);
        if (ext == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식입니다.");
        }
        long maxBytes = props.upload().maxFileSize().toBytes();
        if (in.size() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "파일 크기는 " + (maxBytes / (1024 * 1024)) + "MB 이하여야 합니다.");
        }
        if (description != null && description.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "설명은 500자 이하여야 합니다.");
        }

        Folder folder = access.requireFolder(me, folderId, Permission.EDIT);
        if (in.sourceId() != null && photos.existsByFamilyIdAndSourceId(me.familyId(), in.sourceId())) {
            throw new DuplicateSourceException();
        }
        AppUser uploader = users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        String original = StringUtils.getFilename(StringUtils.cleanPath(in.filename() == null ? "" : in.filename()));
        if (original == null || original.isBlank()) {
            original = "photo" + ext;
        }
        if (original.length() > 255) {
            original = original.substring(original.length() - 255);
        }

        String stored = storage.store(me.familyId(), in.content(), ext);
        try {
            Photo saved = photos.save(new Photo(folder.getFamily(), folder, uploader, original, stored, contentType,
                    in.size(), StringUtils.hasText(description) ? description.trim() : null, in.sourceId()));
            return toResponse(saved, me.userId(), true);
        } catch (RuntimeException e) {
            storage.delete(me.familyId(), stored);
            throw e;
        }
    }

    /** folderId == null 이면 접근 가능한 모든 폴더의 사진 */
    @Transactional(readOnly = true)
    public PageResponse<PhotoResponse> list(AppPrincipal me, Long folderId, int page, int size) {
        List<Long> ids = folderId != null
                ? List.of(access.requireFolder(me, folderId, Permission.VIEW).getId())
                : folderService.accessibleFolderIds(me);
        if (ids.isEmpty()) {
            return new PageResponse<>(List.of(), 0, 0, 0);
        }
        Page<Photo> result = photos.findInFolders(ids,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 60)));

        Map<Long, Folder> distinct = new LinkedHashMap<>();
        result.getContent().forEach(p -> distinct.putIfAbsent(p.getFolder().getId(), p.getFolder()));
        Map<Long, Permission> perms = access.permissions(me, List.copyOf(distinct.values()));

        return new PageResponse<>(
                result.getContent().stream()
                        .map(p -> toResponse(p, me.userId(), canDelete(p, me, perms.get(p.getFolder().getId()))))
                        .toList(),
                result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public FileContent file(AppPrincipal me, Long id) {
        Photo p = findReadable(me, id);
        return new FileContent(storage.load(p.getFamily().getId(), p.getStoredFilename()),
                p.getContentType(), p.getOriginalFilename());
    }

    /** 삭제: 폴더 관리자(MANAGE) 이거나, 내가 올린 사진이면서 폴더 EDIT 권한이 있을 때 */
    @Transactional
    public void delete(AppPrincipal me, Long id) {
        Photo p = findReadable(me, id);
        Permission perm = access.resolve(me, p.getFolder());
        if (!canDelete(p, me, perm)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 사진을 삭제할 권한이 없습니다.");
        }
        photos.delete(p);
        storage.delete(p.getFamily().getId(), p.getStoredFilename());
    }

    private Photo findReadable(AppPrincipal me, Long id) {
        Photo p = photos.findWithUploaderById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."));
        access.require(me, p.getFolder(), Permission.VIEW); // 권한 없으면 404 로 존재 자체를 숨김
        return p;
    }

    private static boolean canDelete(Photo p, AppPrincipal me, Permission perm) {
        if (perm == null) return false;
        return perm.atLeast(Permission.MANAGE)
                || (perm.atLeast(Permission.EDIT) && p.getUploader().getId().equals(me.userId()));
    }

    private PhotoResponse toResponse(Photo p, Long meId, boolean canDelete) {
        return new PhotoResponse(p.getId(), p.getOriginalFilename(), p.getDescription(),
                p.getFolder().getId(), p.getFolder().getName(), p.getFileSize(),
                p.getUploader().getId(), p.getUploader().getName(), p.getCreatedAt(),
                p.getUploader().getId().equals(meId), canDelete);
    }
}
