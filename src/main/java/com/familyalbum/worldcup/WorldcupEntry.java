package com.familyalbum.worldcup;

import com.familyalbum.domain.Photo;
import jakarta.persistence.*;

/** 월드컵 후보. 텍스트(label) 또는 앨범 사진(photo). */
@Entity
@Table(name = "worldcup_entry")
public class WorldcupEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldcupId;

    @Column(length = 100)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id")
    private Photo photo;

    @Column(nullable = false)
    private int sortOrder;

    protected WorldcupEntry() {}

    public WorldcupEntry(Long worldcupId, String label, Photo photo, int sortOrder) {
        this.worldcupId = worldcupId;
        this.label = label;
        this.photo = photo;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Long getWorldcupId() { return worldcupId; }
    public String getLabel() { return label; }
    public Photo getPhoto() { return photo; }
    public int getSortOrder() { return sortOrder; }
}
