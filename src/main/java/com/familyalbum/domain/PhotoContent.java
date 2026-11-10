package com.familyalbum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 사진 바이너리 전용 테이블. 사진 목록 조회 시 큰 bytea 컬럼을 함께 읽지 않도록 메타데이터와 분리한다. */
@Entity
@Table(name = "photo_content")
public class PhotoContent {

    @Id
    @Column(name = "stored_filename", nullable = false, length = 100)
    private String storedFilename;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    protected PhotoContent() {}

    public PhotoContent(Long familyId, String storedFilename, byte[] content) {
        this.familyId = familyId;
        this.storedFilename = storedFilename;
        this.content = content;
    }

    public String getStoredFilename() { return storedFilename; }
    public Long getFamilyId() { return familyId; }
    public byte[] getContent() { return content; }
}
