package com.familyalbum.security;

import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.FamilyRole;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 세션에 저장되는 인증 주체. 컨트롤러에서 @AuthenticationPrincipal 로 주입. */
public class AppPrincipal implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final Long familyId;
    private final String email;
    private final String name;
    private final FamilyRole familyRole;
    private String passwordHash;

    private AppPrincipal(Long userId, Long familyId, String email, String name, FamilyRole familyRole, String passwordHash) {
        this.userId = userId;
        this.familyId = familyId;
        this.email = email;
        this.name = name;
        this.familyRole = familyRole;
        this.passwordHash = passwordHash;
    }

    public static AppPrincipal from(AppUser u) {
        return new AppPrincipal(u.getId(), u.getFamily().getId(), u.getEmail(), u.getName(),
                u.getFamilyRole(), u.getPasswordHash());
    }

    public static AppPrincipal of(Long userId, Long familyId, String email, String name, FamilyRole role) {
        return new AppPrincipal(userId, familyId, email, name, role, null);
    }

    public Long userId() { return userId; }
    public Long familyId() { return familyId; }
    public String email() { return email; }
    public String name() { return name; }
    /** 세션 직렬화 이전 버전에는 역할 필드가 없을 수 있어 null 은 MEMBER 로 취급 */
    public boolean admin() { return familyRole == FamilyRole.ADMIN; }
    public FamilyRole familyRole() { return familyRole == null ? FamilyRole.MEMBER : familyRole; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public void eraseCredentials() { this.passwordHash = null; }

    @Override public boolean equals(Object o) {
        return o instanceof AppPrincipal other && Objects.equals(userId, other.userId);
    }
    @Override public int hashCode() { return Objects.hashCode(userId); }
}
