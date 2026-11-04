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

    /** 인터넷 이미지 후보: 검색 제공처가 준 https 이미지 주소와 출처(라이선스 표기) */
    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 1000)
    private String sourceUrl;

    @Column(length = 300)
    private String credit;

    @Column(nullable = false)
    private int sortOrder;

    protected WorldcupEntry() {}

    public WorldcupEntry(Long worldcupId, String label, Photo photo, int sortOrder) {
        this.worldcupId = worldcupId;
        this.label = label;
        this.photo = photo;
        this.sortOrder = sortOrder;
    }

    public static WorldcupEntry web(Long worldcupId, String label, String imageUrl, String sourceUrl, String credit, int sortOrder) {
        WorldcupEntry e = new WorldcupEntry(worldcupId, label, null, sortOrder);
        e.imageUrl = imageUrl;
        e.sourceUrl = sourceUrl;
        e.credit = credit;
        return e;
    }

    public Long getId() { return id; }
    public Long getWorldcupId() { return worldcupId; }
    public String getLabel() { return label; }
    public Photo getPhoto() { return photo; }
    public String getImageUrl() { return imageUrl; }
    public String getSourceUrl() { return sourceUrl; }
    public String getCredit() { return credit; }
    public int getSortOrder() { return sortOrder; }
}
