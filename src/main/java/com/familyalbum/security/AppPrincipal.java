package com.familyalbum.security;

import com.familyalbum.domain.AppUser;
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
    private String passwordHash;

    private AppPrincipal(Long userId, Long familyId, String email, String name, String passwordHash) {
        this.userId = userId;
        this.familyId = familyId;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
    }

    public static AppPrincipal from(AppUser u) {
        return new AppPrincipal(u.getId(), u.getFamily().getId(), u.getEmail(), u.getName(), u.getPasswordHash());
    }

    public Long userId() { return userId; }
    public Long familyId() { return familyId; }
    public String email() { return email; }
    public String name() { return name; }

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
