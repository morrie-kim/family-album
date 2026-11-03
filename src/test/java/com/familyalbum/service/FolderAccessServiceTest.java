package com.familyalbum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.familyalbum.domain.AccessLevel;
import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.Family;
import com.familyalbum.domain.FamilyRole;
import com.familyalbum.domain.Folder;
import com.familyalbum.domain.FolderAccess;
import com.familyalbum.domain.Permission;
import com.familyalbum.repository.FolderAccessRepository;
import com.familyalbum.repository.FolderRepository;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class FolderAccessServiceTest {

    private final FolderRepository folders = mock(FolderRepository.class);
    private final FolderAccessRepository accesses = mock(FolderAccessRepository.class);
    private final FolderAccessService service = new FolderAccessService(folders, accesses);

    private final Family family = withId(new Family("김씨네", "ABC12345"), 1L);
    private final AppUser creatorUser = withId(new AppUser("mom@x.com", "h", "엄마", family, FamilyRole.MEMBER), 10L);
    private final AppUser memberUser = withId(new AppUser("kid@x.com", "h", "아이", family, FamilyRole.MEMBER), 20L);
    private final Folder folder = withId(new Folder(family, "제주 여행", creatorUser, false), 100L);

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static AppPrincipal principal(long userId, long familyId, FamilyRole role) {
        return AppPrincipal.of(userId, familyId, "u@x.com", "유저", role);
    }

    @Test
    void familyAdminAndFolderCreatorAlwaysManage() {
        assertEquals(Permission.MANAGE, service.resolve(principal(99, 1, FamilyRole.ADMIN), folder));
        assertEquals(Permission.MANAGE, service.resolve(principal(10, 1, FamilyRole.MEMBER), folder));
    }

    @Test
    void explicitGrantsDecideViewOrEdit() {
        when(accesses.findByFolderIdAndUserId(100L, 20L))
                .thenReturn(Optional.of(new FolderAccess(folder, memberUser, AccessLevel.VIEW)));
        assertEquals(Permission.VIEW, service.resolve(principal(20, 1, FamilyRole.MEMBER), folder));

        when(accesses.findByFolderIdAndUserId(100L, 20L))
                .thenReturn(Optional.of(new FolderAccess(folder, memberUser, AccessLevel.EDIT)));
        assertEquals(Permission.EDIT, service.resolve(principal(20, 1, FamilyRole.MEMBER), folder));
    }

    @Test
    void noGrantMeansNoneAndFolderIsHiddenAs404() {
        when(accesses.findByFolderIdAndUserId(100L, 20L)).thenReturn(Optional.empty());
        AppPrincipal me = principal(20, 1, FamilyRole.MEMBER);

        assertEquals(Permission.NONE, service.resolve(me, folder));
        ApiException e = assertThrows(ApiException.class, () -> service.require(me, folder, Permission.VIEW));
        assertEquals(HttpStatus.NOT_FOUND, e.status());
    }

    @Test
    void viewerCannotUploadAndGetsForbidden() {
        when(accesses.findByFolderIdAndUserId(100L, 20L))
                .thenReturn(Optional.of(new FolderAccess(folder, memberUser, AccessLevel.VIEW)));
        AppPrincipal me = principal(20, 1, FamilyRole.MEMBER);

        ApiException e = assertThrows(ApiException.class, () -> service.require(me, folder, Permission.EDIT));
        assertEquals(HttpStatus.FORBIDDEN, e.status());
    }

    @Test
    void otherFamilyNeverGetsAccessEvenAsAdmin() {
        assertEquals(Permission.NONE, service.resolve(principal(77, 2, FamilyRole.ADMIN), folder));
    }

    @Test
    void batchPermissionsUseSingleGrantLookup() {
        when(accesses.findByUserId(20L))
                .thenReturn(List.of(new FolderAccess(folder, memberUser, AccessLevel.EDIT)));

        Map<Long, Permission> perms = service.permissions(principal(20, 1, FamilyRole.MEMBER), List.of(folder));

        assertEquals(Permission.EDIT, perms.get(100L));
    }
}
