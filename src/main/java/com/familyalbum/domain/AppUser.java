package com.familyalbum.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id")
    private Family family;

    /** 기존 DB 에 컬럼이 추가되어도 ddl-auto=update 가 깨지지 않도록 DB 기본값 지정 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'MEMBER'")
    private FamilyRole familyRole = FamilyRole.MEMBER;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {}

    public AppUser(String email, String passwordHash, String name, Family family, FamilyRole familyRole) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.family = family;
        this.familyRole = familyRole;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public Family getFamily() { return family; }
    public FamilyRole getFamilyRole() { return familyRole; }
    public Instant getCreatedAt() { return createdAt; }
}
