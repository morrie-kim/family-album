package com.familyalbum.service;

import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.Family;
import com.familyalbum.repository.AppUserRepository;
import com.familyalbum.repository.FamilyRepository;
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

    private final AppUserRepository users;
    private final FamilyRepository families;
    private final PasswordEncoder encoder;

    public AuthService(AppUserRepository users, FamilyRepository families, PasswordEncoder encoder) {
        this.users = users;
        this.families = families;
        this.encoder = encoder;
    }

    /** inviteCode 가 있으면 기존 가족에 합류, 없으면 familyName 으로 새 가족 생성 */
    @Transactional
    public AppUser signup(SignupRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }

        Family family;
        if (r.inviteCode() != null && !r.inviteCode().isBlank()) {
            family = families.findByInviteCode(r.inviteCode().trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 초대 코드입니다."));
        } else if (r.familyName() != null && !r.familyName().isBlank()) {
            family = families.save(new Family(r.familyName().trim(), newInviteCode()));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "가족 이름 또는 초대 코드를 입력해 주세요.");
        }

        return users.save(new AppUser(email, encoder.encode(r.password()), r.name().trim(), family));
    }

    private static String newInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
