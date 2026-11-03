package com.familyalbum.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 가족 구성원 개인의 구글 계정 연결. refresh token 은 AES-GCM 으로 암호화해 저장. */
@Entity
@Table(name = "google_account", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class GoogleAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 2000)
    private String refreshTokenEnc;

    @Column(nullable = false)
    private Instant connectedAt = Instant.now();

    protected GoogleAccount() {}

    public GoogleAccount(AppUser user, String refreshTokenEnc) {
        this.user = user;
        this.refreshTokenEnc = refreshTokenEnc;
    }

    public void replaceToken(String refreshTokenEnc) {
        this.refreshTokenEnc = refreshTokenEnc;
        this.connectedAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getRefreshTokenEnc() { return refreshTokenEnc; }
    public Instant getConnectedAt() { return connectedAt; }
}
