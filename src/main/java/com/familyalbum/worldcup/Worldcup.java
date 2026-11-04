package com.familyalbum.worldcup;

import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.Family;
import com.familyalbum.domain.Folder;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * 이상형 월드컵(주제 + 후보 목록).
 * folder 가 있으면 "사진 월드컵" — 해당 폴더를 볼 수 있는 계정만 접근(후보 사진이 모두 그 폴더에 속함).
 * folder 가 없으면 "텍스트 월드컵" — 가족 전체에 공개.
 */
@Entity
@Table(name = "worldcup")
public class Worldcup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id")
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Worldcup() {}

    public Worldcup(Family family, AppUser createdBy, Folder folder, String title) {
        this.family = family;
        this.createdBy = createdBy;
        this.folder = folder;
        this.title = title;
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public AppUser getCreatedBy() { return createdBy; }
    public Folder getFolder() { return folder; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
}
