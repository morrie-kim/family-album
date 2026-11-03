package com.familyalbum.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "folder")
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id")
    private Family family;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    /** 가족 생성 시 자동으로 만들어지는 기본 폴더(삭제 불가, 새 구성원에게 EDIT 자동 부여) */
    @Column(nullable = false)
    private boolean defaultFolder;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Folder() {}

    public Folder(Family family, String name, AppUser createdBy, boolean defaultFolder) {
        this.family = family;
        this.name = name;
        this.createdBy = createdBy;
        this.defaultFolder = defaultFolder;
    }

    public void rename(String name) { this.name = name; }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public String getName() { return name; }
    public AppUser getCreatedBy() { return createdBy; }
    public boolean isDefaultFolder() { return defaultFolder; }
    public Instant getCreatedAt() { return createdAt; }
}
