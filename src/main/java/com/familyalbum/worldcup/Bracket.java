package com.familyalbum.worldcup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 라운드 크기 규칙과 무작위 대진 생성 */
public final class Bracket {

    public static final int MIN_ENTRIES = 4;
    public static final int MAX_ENTRIES = 64;

    private Bracket() {}

    /** 후보 수 n 으로 고를 수 있는 라운드 크기(4, 8, 16, 32, 64 중 n 이하) */
    public static List<Integer> options(int entryCount) {
        List<Integer> out = new ArrayList<>();
        for (int size = MIN_ENTRIES; size <= MAX_ENTRIES && size <= entryCount; size *= 2) out.add(size);
        return out;
    }

    public static boolean isValidRounds(int rounds, int entryCount) {
        return options(entryCount).contains(rounds);
    }

    /** 후보 중 rounds 개를 무작위로 뽑아 섞는다(앞에서부터 2명씩 대진). */
    public static List<Long> sample(List<Long> entryIds, int rounds, Random random) {
        List<Long> copy = new ArrayList<>(entryIds);
        Collections.shuffle(copy, random);
        return new ArrayList<>(copy.subList(0, rounds));
    }

    /** 16 → "16강", 4 → "4강", 2 → "결승" */
    public static String roundLabel(int roundSize) {
        return roundSize == 2 ? "결승" : roundSize + "강";
    }
}
