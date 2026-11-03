package com.familyalbum.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "photo")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id")
    private Family family;

    /** 사진이 속한 폴더. 열람·삭제 권한은 폴더 권한으로 결정된다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id")
    private AppUser uploader;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, unique = true, length = 100)
    private String storedFilename;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(length = 500)
    private String description;

    /** 외부에서 가져온 사진의 원본 식별자(예: google:{userId}:{mediaItemId}). 중복 가져오기 방지용 */
    @Column(length = 255)
    private String sourceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Photo() {}

    public Photo(Family family, Folder folder, AppUser uploader, String originalFilename, String storedFilename,
                 String contentType, long fileSize, String description, String sourceId) {
        this.family = family;
        this.folder = folder;
        this.uploader = uploader;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.description = description;
        this.sourceId = sourceId;
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public Folder getFolder() { return folder; }
    public AppUser getUploader() { return uploader; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getDescription() { return description; }
    public String getSourceId() { return sourceId; }
    public Instant getCreatedAt() { return createdAt; }
}
