package com.familyalbum.worldcup;

import java.util.List;

public final class WorldcupDtos {
    private WorldcupDtos() {}

    /** 후보(텍스트 또는 사진) */
    public record Entry(Long id, String label, Long photoId, String photoName,
                        String imageUrl, String sourceUrl, String credit) {
        /** 앨범 사진 후보 */
        public boolean hasPhoto() {
            return photoId != null;
        }

        /** 인터넷 이미지 후보 */
        public boolean hasExternal() {
            return imageUrl != null;
        }

        /** 이미지가 있는 후보(앨범 사진 또는 인터넷 이미지) */
        public boolean hasImage() {
            return hasPhoto() || hasExternal();
        }

        /** 표시 이름: label → 사진 파일명(확장자 제거) → 빈 문자열 */
        public String displayName() {
            if (label != null && !label.isBlank()) return label;
            if (photoName == null) return "";
            int dot = photoName.lastIndexOf('.');
            return dot > 0 ? photoName.substring(0, dot) : photoName;
        }
    }

    public record Summary(Long id, String title, int entryCount, long playCount, String folderName,
                          String creatorName, boolean photoBased, boolean webBased, boolean canDelete) {}

    public record Detail(Long id, String title, String creatorName, String folderName, boolean photoBased,
                         boolean webBased, boolean canDelete, long playCount, List<Integer> roundOptions,
                         List<Entry> entries) {}

    public record MatchView(Long worldcupId, String title, Long playId, String roundLabel, int matchNo, int matchTotal, int progressPercent,
                            Entry a, Entry b) {}

    public record EliminatedGroup(String label, List<Entry> entries) {}

    public record PlayResult(Long worldcupId, String title, Long playId, int startSize, Entry champion,
                             Entry runnerUp, List<EliminatedGroup> eliminated) {}

    public record RankRow(int rank, Entry entry, long champions, long wins, long appearances, int winRatePercent) {}

    public record Ranking(Long worldcupId, String title, long playCount, List<RankRow> rows) {}
}
