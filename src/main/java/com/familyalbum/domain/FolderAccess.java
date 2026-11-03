package com.familyalbum.domain;

import jakarta.persistence.*;

/** (폴더, 계정) 단위의 명시적 접근 권한. 관리자/폴더 생성자는 행 없이도 항상 MANAGE. */
@Entity
@Table(name = "folder_access", uniqueConstraints = @UniqueConstraint(columnNames = {"folder_id", "user_id"}))
public class FolderAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccessLevel level;

    protected FolderAccess() {}

    public FolderAccess(Folder folder, AppUser user, AccessLevel level) {
        this.folder = folder;
        this.user = user;
        this.level = level;
    }

    public void setLevel(AccessLevel level) { this.level = level; }

    public Long getId() { return id; }
    public Folder getFolder() { return folder; }
    public AppUser getUser() { return user; }
    public AccessLevel getLevel() { return level; }
}
