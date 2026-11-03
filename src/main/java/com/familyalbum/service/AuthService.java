package com.familyalbum.service;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.AccessLevel;
import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.Family;
import com.familyalbum.domain.FamilyRole;
import com.familyalbum.domain.Folder;
import com.familyalbum.domain.FolderAccess;
import com.familyalbum.repository.AppUserRepository;
import com.familyalbum.repository.FamilyRepository;
import com.familyalbum.repository.FolderAccessRepository;
import com.familyalbum.repository.FolderRepository;
import com.familyalbum.web.ApiException;
import com.familyalbum.web.Dtos.SignupRequest;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String DEFAULT_FOLDER_NAME = "가족 앨범";

    private final AppUserRepository users;
    private final FamilyRepository families;
    private final FolderRepository folders;
    private final FolderAccessRepository accesses;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AuthService(AppUserRepository users, FamilyRepository families, FolderRepository folders,
                       FolderAccessRepository accesses, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.families = families;
        this.folders = folders;
        this.accesses = accesses;
        this.encoder = encoder;
        this.props = props;
    }

    /**
     * inviteCode 가 있으면 기존 가족에 합류(MEMBER), 없으면 familyName 으로 새 가족 생성(ADMIN).
     * 새 가족에는 기본 폴더가 만들어지고, 합류자는 기본 폴더에 EDIT 이 자동 부여된다(관리자가 이후 변경 가능).
     */
    @Transactional
    public AppUser signup(SignupRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }

        Family family;
        boolean created = false;
        if (r.inviteCode() != null && !r.inviteCode().isBlank()) {
            family = families.findByInviteCode(r.inviteCode().trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 초대 코드입니다."));
        } else if (r.familyName() != null && !r.familyName().isBlank()) {
            if (!props.signup().allowNewFamily()) {
                throw new ApiException(HttpStatus.FORBIDDEN,
                        "새 가족 만들기는 현재 비활성화되어 있어요. 초대 코드로 가입해 주세요.");
            }
            family = families.save(new Family(r.familyName().trim(), newInviteCode()));
            created = true;
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "가족 이름 또는 초대 코드를 입력해 주세요.");
        }

        AppUser user = users.save(new AppUser(email, encoder.encode(r.password()), r.name().trim(), family,
                created ? FamilyRole.ADMIN : FamilyRole.MEMBER));
        if (created) {
            folders.save(new Folder(family, DEFAULT_FOLDER_NAME, user, true));
        } else {
            folders.findByFamilyIdAndDefaultFolderTrue(family.getId())
                    .ifPresent(f -> accesses.save(new FolderAccess(f, user, AccessLevel.EDIT)));
        }
        return user;
    }

    private static String newInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
