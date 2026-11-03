package com.familyalbum.service;

import com.familyalbum.domain.AccessLevel;
import com.familyalbum.domain.Folder;
import com.familyalbum.domain.FolderAccess;
import com.familyalbum.domain.Permission;
import com.familyalbum.repository.FolderAccessRepository;
import com.familyalbum.repository.FolderRepository;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폴더 접근 권한 판정의 단일 진입점.
 *  - 다른 가족 → NONE
 *  - 가족 관리자(ADMIN) 또는 폴더를 만든 사람 → MANAGE
 *  - 그 외 → 명시적으로 부여된 VIEW / EDIT, 없으면 NONE
 */
@Service
@Transactional(readOnly = true)
public class FolderAccessService {

    private final FolderRepository folders;
    private final FolderAccessRepository accesses;

    public FolderAccessService(FolderRepository folders, FolderAccessRepository accesses) {
        this.folders = folders;
        this.accesses = accesses;
    }

    public Permission resolve(AppPrincipal me, Folder folder) {
        if (!folder.getFamily().getId().equals(me.familyId())) {
            return Permission.NONE;
        }
        if (me.admin() || folder.getCreatedBy().getId().equals(me.userId())) {
            return Permission.MANAGE;
        }
        return accesses.findByFolderIdAndUserId(folder.getId(), me.userId())
                .map(a -> a.getLevel() == AccessLevel.EDIT ? Permission.EDIT : Permission.VIEW)
                .orElse(Permission.NONE);
    }

    /** 여러 폴더의 권한을 한 번의 조회로 계산 */
    public Map<Long, Permission> permissions(AppPrincipal me, List<Folder> list) {
        Map<Long, AccessLevel> granted = new HashMap<>();
        for (FolderAccess a : accesses.findByUserId(me.userId())) {
            granted.put(a.getFolder().getId(), a.getLevel());
        }
        Map<Long, Permission> result = new HashMap<>();
        for (Folder f : list) {
            Permission p;
            if (!f.getFamily().getId().equals(me.familyId())) {
                p = Permission.NONE;
            } else if (me.admin() || f.getCreatedBy().getId().equals(me.userId())) {
                p = Permission.MANAGE;
            } else {
                AccessLevel level = granted.get(f.getId());
                p = level == null ? Permission.NONE : level == AccessLevel.EDIT ? Permission.EDIT : Permission.VIEW;
            }
            result.put(f.getId(), p);
        }
        return result;
    }

    /** 폴더가 없거나 볼 권한이 없으면 404(존재 여부 비노출), 볼 수는 있으나 부족하면 403 */
    public Folder require(AppPrincipal me, Folder folder, Permission min) {
        Permission p = resolve(me, folder);
        if (!p.atLeast(Permission.VIEW)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "폴더를 찾을 수 없습니다.");
        }
        if (!p.atLeast(min)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "이 폴더에 대한 권한이 없습니다.");
        }
        return folder;
    }

    public Folder requireFolder(AppPrincipal me, Long folderId, Permission min) {
        if (folderId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "폴더를 선택해 주세요.");
        }
        Folder f = folders.findDetailedById(folderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        return require(me, f, min);
    }
}
