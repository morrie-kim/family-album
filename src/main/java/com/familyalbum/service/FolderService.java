package com.familyalbum.service;

import com.familyalbum.domain.AccessLevel;
import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.FamilyRole;
import com.familyalbum.domain.Folder;
import com.familyalbum.domain.FolderAccess;
import com.familyalbum.domain.Permission;
import com.familyalbum.repository.AppUserRepository;
import com.familyalbum.repository.FolderAccessRepository;
import com.familyalbum.repository.FolderRepository;
import com.familyalbum.repository.PhotoRepository;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.ApiException;
import com.familyalbum.web.Dtos.FolderDetail;
import com.familyalbum.web.Dtos.FolderResponse;
import com.familyalbum.web.Dtos.MemberAccess;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {

    private final FolderRepository folders;
    private final FolderAccessRepository accesses;
    private final AppUserRepository users;
    private final PhotoRepository photos;
    private final FolderAccessService access;

    public FolderService(FolderRepository folders, FolderAccessRepository accesses, AppUserRepository users,
                         PhotoRepository photos, FolderAccessService access) {
        this.folders = folders;
        this.accesses = accesses;
        this.users = users;
        this.photos = photos;
        this.access = access;
    }

    /** 내가 볼 수 있는 폴더 목록(기본 폴더 우선, 이름순) */
    @Transactional(readOnly = true)
    public List<FolderResponse> list(AppPrincipal me) {
        List<Folder> all = folders.findByFamilyIdOrderByDefaultFolderDescNameAsc(me.familyId());
        Map<Long, Permission> perms = access.permissions(me, all);
        return all.stream()
                .filter(f -> perms.get(f.getId()).atLeast(Permission.VIEW))
                .map(f -> new FolderResponse(f.getId(), f.getName(), f.isDefaultFolder(),
                        perms.get(f.getId()), photos.countByFolderId(f.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> accessibleFolderIds(AppPrincipal me) {
        return list(me).stream().map(FolderResponse::id).toList();
    }

    /** 업로드/가져오기 대상으로 고를 수 있는 폴더(EDIT 이상) */
    @Transactional(readOnly = true)
    public List<FolderResponse> uploadable(AppPrincipal me) {
        return list(me).stream().filter(FolderResponse::canEdit).toList();
    }

    @Transactional(readOnly = true)
    public FolderDetail detail(AppPrincipal me, Long folderId) {
        return toDetail(access.requireFolder(me, folderId, Permission.MANAGE));
    }

    /** 새 폴더 폼: 모든 구성원 권한 NONE (관리자·나는 항상 전체 권한) */
    @Transactional(readOnly = true)
    public FolderDetail blank(AppPrincipal me) {
        List<MemberAccess> members = users.findByFamilyIdOrderByIdAsc(me.familyId()).stream()
                .map(u -> {
                    boolean admin = u.getFamilyRole() == FamilyRole.ADMIN;
                    boolean self = u.getId().equals(me.userId());
                    boolean implicit = admin || self;
                    return new MemberAccess(u.getId(), u.getName(), u.getEmail(), implicit ? "MANAGE" : "NONE",
                            implicit, admin ? "가족 관리자" : "폴더를 만드는 사람");
                }).toList();
        return new FolderDetail(null, "", false, members);
    }

    @Transactional
    public FolderDetail create(AppPrincipal me, String name, Map<Long, AccessLevel> grants) {
        String clean = cleanName(name);
        if (folders.existsByFamilyIdAndNameIgnoreCase(me.familyId(), clean)) {
            throw new ApiException(HttpStatus.CONFLICT, "같은 이름의 폴더가 이미 있어요.");
        }
        AppUser creator = users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        Folder folder = folders.save(new Folder(creator.getFamily(), clean, creator, false));
        applyGrants(folder, grants);
        return toDetail(folder);
    }

    @Transactional
    public FolderDetail update(AppPrincipal me, Long folderId, String name, Map<Long, AccessLevel> grants) {
        Folder folder = access.requireFolder(me, folderId, Permission.MANAGE);
        String clean = cleanName(name);
        if (!clean.equalsIgnoreCase(folder.getName())
                && folders.existsByFamilyIdAndNameIgnoreCase(me.familyId(), clean)) {
            throw new ApiException(HttpStatus.CONFLICT, "같은 이름의 폴더가 이미 있어요.");
        }
        folder.rename(clean);
        applyGrants(folder, grants);
        return toDetail(folder);
    }

    @Transactional
    public void delete(AppPrincipal me, Long folderId) {
        Folder folder = access.requireFolder(me, folderId, Permission.MANAGE);
        if (folder.isDefaultFolder()) {
            throw new ApiException(HttpStatus.CONFLICT, "기본 폴더는 삭제할 수 없어요.");
        }
        if (photos.countByFolderId(folder.getId()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "폴더에 사진이 남아 있어요. 사진을 먼저 삭제해 주세요.");
        }
        accesses.deleteByFolderId(folder.getId());
        folders.delete(folder);
    }

    /** 구성원 전체를 대상으로 권한 행을 동기화한다. grants 에 없는 구성원 = 접근 없음. */
    private void applyGrants(Folder folder, Map<Long, AccessLevel> grants) {
        Map<Long, AccessLevel> wanted = grants == null ? Map.of() : grants;
        List<AppUser> members = users.findByFamilyIdOrderByIdAsc(folder.getFamily().getId());
        Set<Long> memberIds = new HashSet<>();
        members.forEach(m -> memberIds.add(m.getId()));
        for (Long id : wanted.keySet()) {
            if (!memberIds.contains(id)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "가족 구성원이 아닌 계정입니다.");
            }
        }
        Map<Long, FolderAccess> existing = new HashMap<>();
        for (FolderAccess a : accesses.findByFolderId(folder.getId())) {
            existing.put(a.getUser().getId(), a);
        }
        for (AppUser m : members) {
            boolean implicit = m.getFamilyRole() == FamilyRole.ADMIN
                    || m.getId().equals(folder.getCreatedBy().getId());
            AccessLevel want = implicit ? null : wanted.get(m.getId()); // 암묵적 MANAGE 는 행을 두지 않는다
            FolderAccess cur = existing.get(m.getId());
            if (want == null) {
                if (cur != null) accesses.delete(cur);
            } else if (cur == null) {
                accesses.save(new FolderAccess(folder, m, want));
            } else {
                cur.setLevel(want);
            }
        }
    }

    private FolderDetail toDetail(Folder folder) {
        Map<Long, AccessLevel> existing = new HashMap<>();
        for (FolderAccess a : accesses.findByFolderId(folder.getId())) {
            existing.put(a.getUser().getId(), a.getLevel());
        }
        List<MemberAccess> members = users.findByFamilyIdOrderByIdAsc(folder.getFamily().getId()).stream()
                .map(u -> {
                    boolean admin = u.getFamilyRole() == FamilyRole.ADMIN;
                    boolean creator = u.getId().equals(folder.getCreatedBy().getId());
                    if (admin || creator) {
                        return new MemberAccess(u.getId(), u.getName(), u.getEmail(), "MANAGE", true,
                                admin ? "가족 관리자" : "폴더를 만든 사람");
                    }
                    AccessLevel level = existing.get(u.getId());
                    return new MemberAccess(u.getId(), u.getName(), u.getEmail(),
                            level == null ? "NONE" : level.name(), false, null);
                }).toList();
        return new FolderDetail(folder.getId(), folder.getName(), folder.isDefaultFolder(), members);
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "폴더 이름을 입력해 주세요.");
        }
        if (n.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "폴더 이름은 100자 이하여야 합니다.");
        }
        return n;
    }
}
