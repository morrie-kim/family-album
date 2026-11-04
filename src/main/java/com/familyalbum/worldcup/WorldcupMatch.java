package com.familyalbum.worldcup;

import jakarta.persistence.*;

/** 한 판의 기록(통계용). 단순 조회·집계를 위해 연관 대신 id 컬럼을 사용한다. */
@Entity
@Table(name = "worldcup_match")
public class WorldcupMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldcupId;

    @Column(nullable = false)
    private Long playId;

    @Column(nullable = false)
    private int roundSize;

    @Column(nullable = false)
    private Long entryAId;

    @Column(nullable = false)
    private Long entryBId;

    @Column(nullable = false)
    private Long winnerId;

    protected WorldcupMatch() {}

    public WorldcupMatch(Long worldcupId, Long playId, MatchOutcome o) {
        this.worldcupId = worldcupId;
        this.playId = playId;
        this.roundSize = o.roundSize();
        this.entryAId = o.entryAId();
        this.entryBId = o.entryBId();
        this.winnerId = o.winnerId();
    }

    public Long getId() { return id; }
    public Long getWorldcupId() { return worldcupId; }
    public Long getPlayId() { return playId; }
    public int getRoundSize() { return roundSize; }
    public Long getEntryAId() { return entryAId; }
    public Long getEntryBId() { return entryBId; }
    public Long getWinnerId() { return winnerId; }
    public long loserId() { return winnerId.equals(entryAId) ? entryBId : entryAId; }
}
