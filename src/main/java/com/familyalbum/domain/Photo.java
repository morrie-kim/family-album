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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Photo() {}

    public Photo(Family family, AppUser uploader, String originalFilename, String storedFilename,
                 String contentType, long fileSize, String description, Visibility visibility) {
        this.family = family;
        this.uploader = uploader;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.description = description;
        this.visibility = visibility;
    }

    public Long getId() { return id; }
    public Family getFamily() { return family; }
    public AppUser getUploader() { return uploader; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getDescription() { return description; }
    public Visibility getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
}
