package com.familyalbum.worldcup;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 한 사람의 월드컵 1회 플레이(진행 상태 포함).
 *  - bracket: 현재 라운드 참가자 id (앞에서부터 2명씩 대진)
 *  - winners: 현재 라운드에서 이미 이긴 후보 id (순서 유지 → 다음 라운드 대진이 됨)
 * 서버가 상태를 보관하므로 중간에 나가도 이어서 할 수 있고, 대진에 없는 후보는 선택할 수 없다.
 */
@Entity
@Table(name = "worldcup_play")
public class WorldcupPlay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long worldcupId;

    @Column(nullable = false)
    private Long playerId;

    /** 시작 시 라운드 크기(예: 16강이면 16) */
    @Column(nullable = false)
    private int startSize;

    @Column(nullable = false, length = 1000)
    private String bracket;

    /** 빈 문자열이 NULL 로 저장되는 DB(Oracle 등)를 위해 nullable 허용 */
    @Column(length = 1000)
    private String winners = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayStatus status = PlayStatus.IN_PROGRESS;

    private Long championEntryId;
    private Long runnerUpEntryId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    protected WorldcupPlay() {}

    public WorldcupPlay(Long worldcupId, Long playerId, List<Long> startBracket) {
        int n = startBracket.size();
        if (n < 2 || Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("대진 인원은 2의 거듭제곱이어야 합니다.");
        }
        this.worldcupId = worldcupId;
        this.playerId = playerId;
        this.startSize = n;
        this.bracket = join(startBracket);
    }

    public boolean isDone() { return status == PlayStatus.DONE; }

    public List<Long> bracketIds() { return split(bracket); }

    public List<Long> winnerIds() { return split(winners); }

    /** 현재 라운드 크기(16강=16, 결승=2) */
    public int roundSize() { return bracketIds().size(); }

    /** 현재 라운드에서 진행할 대진 번호(1부터) */
    public int matchNumber() { return winnerIds().size() + 1; }

    public int matchesInRound() { return roundSize() / 2; }

    /** 지금 선택해야 하는 두 후보 [A, B]. 끝난 플레이면 IllegalStateException */
    public long[] currentPair() {
        if (isDone()) throw new IllegalStateException("이미 끝난 플레이입니다.");
        List<Long> b = bracketIds();
        int idx = winnerIds().size();
        return new long[] {b.get(2 * idx), b.get(2 * idx + 1)};
    }

    /** 현재 대진에서 winnerId 를 선택. 대진에 없는 후보면 IllegalArgumentException. */
    public MatchOutcome pick(long winnerId) {
        long[] pair = currentPair();
        if (winnerId != pair[0] && winnerId != pair[1]) {
            throw new IllegalArgumentException("현재 대진의 후보가 아닙니다.");
        }
        List<Long> b = bracketIds();
        List<Long> w = winnerIds();
        MatchOutcome outcome = new MatchOutcome(b.size(), pair[0], pair[1], winnerId);
        w.add(winnerId);

        if (w.size() * 2 == b.size()) {            // 라운드 종료
            if (w.size() == 1) {                    // 결승이었다면 플레이 종료
                status = PlayStatus.DONE;
                championEntryId = winnerId;
                runnerUpEntryId = outcome.loserId();
                completedAt = Instant.now();
                winners = join(w);
            } else {                                // 다음 라운드: 승자들이 새 대진
                bracket = join(w);
                winners = "";
            }
        } else {
            winners = join(w);
        }
        return outcome;
    }

    private static String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Long> split(String s) {
        List<Long> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;
        for (String part : s.split(",")) out.add(Long.parseLong(part));
        return out;
    }

    public Long getId() { return id; }
    public Long getWorldcupId() { return worldcupId; }
    public Long getPlayerId() { return playerId; }
    public int getStartSize() { return startSize; }
    public PlayStatus getStatus() { return status; }
    public Long getChampionEntryId() { return championEntryId; }
    public Long getRunnerUpEntryId() { return runnerUpEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
