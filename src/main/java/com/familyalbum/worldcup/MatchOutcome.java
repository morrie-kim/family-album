package com.familyalbum.worldcup;

/** 한 판(대진)의 결과. roundSize: 16강이면 16, 결승이면 2 */
public record MatchOutcome(int roundSize, long entryAId, long entryBId, long winnerId) {
    public long loserId() {
        return winnerId == entryAId ? entryBId : entryAId;
    }
}
