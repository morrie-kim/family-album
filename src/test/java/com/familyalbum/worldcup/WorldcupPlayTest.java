package com.familyalbum.worldcup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldcupPlayTest {

    private static WorldcupPlay play(Long... ids) {
        return new WorldcupPlay(1L, 2L, List.of(ids));
    }

    @Test
    void eightEntriesAlwaysTakeSevenPicksAndTheFirstOfEachPairWinsToTheEnd() {
        WorldcupPlay p = play(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        List<MatchOutcome> outcomes = new ArrayList<>();
        while (!p.isDone()) {
            outcomes.add(p.pick(p.currentPair()[0])); // 항상 A 자리 선택
        }
        assertEquals(7, outcomes.size());
        assertEquals(1L, p.getChampionEntryId());
        assertEquals(5L, p.getRunnerUpEntryId());
        assertEquals(List.of(8, 8, 8, 8, 4, 4, 2), outcomes.stream().map(MatchOutcome::roundSize).toList());
    }

    @Test
    void roundAdvancesAndKeepsBracketAdjacency() {
        WorldcupPlay p = play(10L, 20L, 30L, 40L);
        p.pick(20L);                       // 10 vs 20 → 20
        assertEquals(1, p.winnerIds().size());
        assertEquals(2, p.matchNumber());
        p.pick(30L);                       // 30 vs 40 → 30, 결승 진입
        assertEquals(2, p.roundSize());
        assertEquals(List.of(20L, 30L), p.bracketIds());
        assertEquals("결승", Bracket.roundLabel(p.roundSize()));
        p.pick(30L);
        assertTrue(p.isDone());
        assertEquals(30L, p.getChampionEntryId());
        assertEquals(20L, p.getRunnerUpEntryId());
    }

    @Test
    void cannotPickEntryOutsideCurrentPairOrAfterFinish() {
        WorldcupPlay p = play(1L, 2L, 3L, 4L);
        assertThrows(IllegalArgumentException.class, () -> p.pick(3L)); // 지금 대진은 1 vs 2
        p.pick(1L); p.pick(3L); p.pick(1L);
        assertTrue(p.isDone());
        assertThrows(IllegalStateException.class, () -> p.pick(1L));
        assertThrows(IllegalStateException.class, p::currentPair);
    }

    @Test
    void duplicatePickOfSameMatchIsRejectedSoDoubleClickCannotSkipAMatch() {
        WorldcupPlay p = play(1L, 2L, 3L, 4L);
        p.pick(1L);                                           // 1 vs 2 처리됨, 이제 3 vs 4
        assertThrows(IllegalArgumentException.class, () -> p.pick(1L)); // 같은 요청이 한 번 더 와도 무시
        assertEquals(2, p.matchNumber());
    }

    @Test
    void requiresPowerOfTwoBracket() {
        assertThrows(IllegalArgumentException.class, () -> play(1L, 2L, 3L));
    }

    @Test
    void bracketOptionsAndSampling() {
        assertEquals(List.of(4, 8), Bracket.options(15));
        assertEquals(List.of(4, 8, 16), Bracket.options(16));
        assertEquals(List.of(4, 8, 16, 32, 64), Bracket.options(100));
        assertEquals(List.of(), Bracket.options(3));
        assertFalse(Bracket.isValidRounds(8, 7));

        List<Long> pool = new ArrayList<>();
        for (long i = 1; i <= 20; i++) pool.add(i);
        List<Long> sample = Bracket.sample(pool, 16, new Random(7));
        assertEquals(16, sample.size());
        Set<Long> unique = new HashSet<>(sample);
        assertEquals(16, unique.size());
        assertTrue(pool.containsAll(sample));
    }

    @Test
    void labelsAreTrimmedDeduplicatedCaseInsensitivelyAndCounted() {
        assertEquals(List.of("짜장면", "pizza", "초밥", "떡볶이"),
                WorldcupService.parseLabels("  짜장면 \n\npizza\nPizza\n초밥\r\n떡볶이\n"));
        assertThrows(com.familyalbum.web.ApiException.class, () -> WorldcupService.parseLabels("a\nb\nc"));
    }
}
