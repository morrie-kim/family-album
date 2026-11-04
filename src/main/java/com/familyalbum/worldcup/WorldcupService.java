package com.familyalbum.worldcup;

import com.familyalbum.domain.AppUser;
import com.familyalbum.domain.Folder;
import com.familyalbum.domain.Permission;
import com.familyalbum.domain.Photo;
import com.familyalbum.repository.AppUserRepository;
import com.familyalbum.images.ImageResult;
import com.familyalbum.images.ImageSearchService;
import com.familyalbum.repository.PhotoRepository;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.FolderAccessService;
import com.familyalbum.web.ApiException;
import com.familyalbum.worldcup.WorldcupDtos.Detail;
import com.familyalbum.worldcup.WorldcupDtos.EliminatedGroup;
import com.familyalbum.worldcup.WorldcupDtos.Entry;
import com.familyalbum.worldcup.WorldcupDtos.MatchView;
import com.familyalbum.worldcup.WorldcupDtos.PlayResult;
import com.familyalbum.worldcup.WorldcupDtos.RankRow;
import com.familyalbum.worldcup.WorldcupDtos.Ranking;
import com.familyalbum.worldcup.WorldcupDtos.Summary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이상형 월드컵.
 * 접근 규칙: 텍스트 월드컵은 가족 전체, 사진 월드컵은 해당 폴더를 볼 수 있는 계정만(폴더 권한과 동일).
 * 플레이 상태는 본인만 조회/진행할 수 있다.
 */
@Service
public class WorldcupService {

    private final WorldcupRepository worldcups;
    private final WorldcupEntryRepository entries;
    private final WorldcupPlayRepository plays;
    private final WorldcupMatchRepository matches;
    private final AppUserRepository users;
    private final PhotoRepository photos;
    private final FolderAccessService access;
    private final ImageSearchService imageSearch;

    public WorldcupService(WorldcupRepository worldcups, WorldcupEntryRepository entries, WorldcupPlayRepository plays,
                           WorldcupMatchRepository matches, AppUserRepository users, PhotoRepository photos,
                           FolderAccessService access, ImageSearchService imageSearch) {
        this.worldcups = worldcups;
        this.entries = entries;
        this.plays = plays;
        this.matches = matches;
        this.users = users;
        this.photos = photos;
        this.access = access;
        this.imageSearch = imageSearch;
    }

    /* ───────── 만들기 ───────── */

    @Transactional
    public Long createFromLabels(AppPrincipal me, String title, String labelsText) {
        String cleanTitle = cleanTitle(title);
        List<String> labels = parseLabels(labelsText);
        AppUser creator = loadUser(me);
        Worldcup w = worldcups.save(new Worldcup(creator.getFamily(), creator, null, cleanTitle));
        int order = 0;
        for (String label : labels) {
            entries.save(new WorldcupEntry(w.getId(), label, null, order++));
        }
        return w.getId();
    }

    /**
     * 인터넷 이미지 월드컵: 주제로 검색한 결과 중 선택한 이미지로 후보를 만든다.
     * 이미지 주소는 클라이언트가 보낸 값이 아니라 검색 서비스의 결과(id → 허용된 https 호스트)에서만 가져온다.
     */
    @Transactional
    public Long createFromWeb(AppPrincipal me, String title, String query, List<String> imageIds) {
        String cleanTitle = cleanTitle(title);
        List<String> ids = imageIds == null ? List.of() : imageIds.stream().filter(Objects::nonNull).distinct().toList();
        requireCount(ids.size());
        List<ImageResult> picked = imageSearch.resolve(query != null && !query.isBlank() ? query : cleanTitle, ids);

        AppUser creator = loadUser(me);
        Worldcup w = worldcups.save(new Worldcup(creator.getFamily(), creator, null, cleanTitle));
        int order = 0;
        for (ImageResult r : picked) {
            String credit = r.credit();
            entries.save(WorldcupEntry.web(w.getId(), r.title(), r.thumbUrl(), r.sourceUrl(),
                    credit.length() > 300 ? credit.substring(0, 300) : credit, order++));
        }
        return w.getId();
    }

    @Transactional
    public Long createFromPhotos(AppPrincipal me, String title, Long folderId, List<Long> photoIds) {
        String cleanTitle = cleanTitle(title);
        Folder folder = access.requireFolder(me, folderId, Permission.VIEW);
        List<Long> ids = photoIds == null ? List.of()
                : photoIds.stream().filter(Objects::nonNull).distinct().toList();
        requireCount(ids.size());

        Map<Long, Photo> byId = photos.findAllById(ids).stream().collect(Collectors.toMap(Photo::getId, p -> p));
        AppUser creator = loadUser(me);
        Worldcup w = worldcups.save(new Worldcup(creator.getFamily(), creator, folder, cleanTitle));
        int order = 0;
        for (Long id : ids) {
            Photo p = byId.get(id);
            if (p == null || !p.getFolder().getId().equals(folder.getId())) {
                // 다른 폴더(권한이 다를 수 있는) 사진이 섞이면 접근 제어가 깨지므로 차단
                throw new ApiException(HttpStatus.BAD_REQUEST, "선택한 폴더의 사진만 후보로 쓸 수 있어요.");
            }
            entries.save(new WorldcupEntry(w.getId(), labelOf(p), p, order++));
        }
        return w.getId();
    }

    /* ───────── 조회 ───────── */

    @Transactional(readOnly = true)
    public List<Summary> list(AppPrincipal me) {
        List<Worldcup> all = worldcups.findByFamilyIdOrderByCreatedAtDesc(me.familyId());
        List<Folder> folders = all.stream().map(Worldcup::getFolder).filter(Objects::nonNull).distinct().toList();
        Map<Long, Permission> perms = access.permissions(me, folders);
        return all.stream()
                .filter(w -> w.getFolder() == null || perms.get(w.getFolder().getId()).atLeast(Permission.VIEW))
                .map(w -> new Summary(w.getId(), w.getTitle(), (int) entries.countByWorldcupId(w.getId()),
                        plays.countByWorldcupId(w.getId()),
                        w.getFolder() == null ? null : w.getFolder().getName(),
                        w.getCreatedBy().getName(), w.getFolder() != null,
                        entries.existsByWorldcupIdAndImageUrlIsNotNull(w.getId()), canDelete(me, w)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Detail detail(AppPrincipal me, Long id) {
        Worldcup w = requireAccessible(me, id);
        List<Entry> list = entries.findByWorldcupIdOrderBySortOrderAsc(id).stream().map(WorldcupService::toEntry).toList();
        return new Detail(w.getId(), w.getTitle(), w.getCreatedBy().getName(),
                w.getFolder() == null ? null : w.getFolder().getName(), w.getFolder() != null,
                list.stream().anyMatch(Entry::hasExternal), canDelete(me, w),
                plays.countByWorldcupId(id), Bracket.options(list.size()), list);
    }

    /* ───────── 플레이 ───────── */

    @Transactional
    public Long startPlay(AppPrincipal me, Long worldcupId, int rounds) {
        requireAccessible(me, worldcupId);
        List<Long> ids = entries.findByWorldcupIdOrderBySortOrderAsc(worldcupId).stream().map(WorldcupEntry::getId).toList();
        if (!Bracket.isValidRounds(rounds, ids.size())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "선택할 수 없는 라운드예요.");
        }
        List<Long> bracket = Bracket.sample(ids, rounds, ThreadLocalRandom.current());
        return plays.save(new WorldcupPlay(worldcupId, me.userId(), bracket)).getId();
    }

    /** 끝난 플레이면 빈 값(→ 결과 화면으로 이동) */
    @Transactional(readOnly = true)
    public Optional<MatchView> currentMatch(AppPrincipal me, Long worldcupId, Long playId) {
        Worldcup w = requireAccessible(me, worldcupId);
        WorldcupPlay play = ownPlay(me, worldcupId, playId);
        if (play.isDone()) {
            return Optional.empty();
        }
        long[] pair = play.currentPair();
        Map<Long, Entry> map = entryMap(List.of(pair[0], pair[1]));
        int total = play.getStartSize() - 1;
        int done = (int) matches.countByPlayId(playId);
        return Optional.of(new MatchView(worldcupId, w.getTitle(), playId, Bracket.roundLabel(play.roundSize()), play.matchNumber(),
                play.matchesInRound(), total <= 0 ? 100 : done * 100 / total, map.get(pair[0]), map.get(pair[1])));
    }

    /** 현재 대진에서 winnerId 선택. 플레이가 끝났으면 true. 이미 처리된 선택(중복 클릭 등)은 409. */
    @Transactional
    public boolean pick(AppPrincipal me, Long worldcupId, Long playId, Long winnerId) {
        requireAccessible(me, worldcupId);
        WorldcupPlay play = ownPlay(me, worldcupId, playId);
        if (play.isDone()) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 끝난 월드컵이에요.");
        }
        MatchOutcome outcome;
        try {
            outcome = play.pick(winnerId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 처리된 선택이에요.");
        }
        matches.save(new WorldcupMatch(worldcupId, playId, outcome));
        return play.isDone();
    }

    @Transactional(readOnly = true)
    public PlayResult playResult(AppPrincipal me, Long worldcupId, Long playId) {
        Worldcup w = requireAccessible(me, worldcupId);
        WorldcupPlay play = ownPlay(me, worldcupId, playId);
        if (!play.isDone()) {
            throw new ApiException(HttpStatus.CONFLICT, "아직 끝나지 않은 월드컵이에요.");
        }
        List<WorldcupMatch> ms = matches.findByPlayIdOrderByIdAsc(playId);
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(play.getChampionEntryId());
        ids.add(play.getRunnerUpEntryId());
        ms.forEach(m -> ids.add(m.loserId()));
        Map<Long, Entry> map = entryMap(ids);

        TreeMap<Integer, List<Entry>> byRound = new TreeMap<>();
        for (WorldcupMatch m : ms) {
            if (m.getRoundSize() > 2) { // 결승 패자는 준우승으로 따로 표시
                byRound.computeIfAbsent(m.getRoundSize(), k -> new ArrayList<>()).add(map.get(m.loserId()));
            }
        }
        List<EliminatedGroup> groups = byRound.entrySet().stream()
                .map(e -> new EliminatedGroup(Bracket.roundLabel(e.getKey()) + " 탈락", e.getValue())).toList();
        return new PlayResult(worldcupId, w.getTitle(), playId, play.getStartSize(),
                map.get(play.getChampionEntryId()), map.get(play.getRunnerUpEntryId()), groups);
    }

    /** 가족 전체 플레이를 합산한 랭킹: 우승 횟수 → 승률 → 승수 순 */
    @Transactional(readOnly = true)
    public Ranking ranking(AppPrincipal me, Long worldcupId) {
        Worldcup w = requireAccessible(me, worldcupId);
        List<WorldcupEntry> es = entries.findByWorldcupIdOrderBySortOrderAsc(worldcupId);
        Map<Long, Long> champs = toMap(plays.championCounts(worldcupId));
        Map<Long, Long> wins = toMap(matches.winCounts(worldcupId));
        Map<Long, Long> appear = new HashMap<>(toMap(matches.appearancesAsA(worldcupId)));
        toMap(matches.appearancesAsB(worldcupId)).forEach((k, v) -> appear.merge(k, v, Long::sum));

        record Raw(WorldcupEntry e, long champions, long wins, long appearances, double rate) {}
        List<Raw> raws = new ArrayList<>();
        for (WorldcupEntry e : es) {
            long win = wins.getOrDefault(e.getId(), 0L);
            long app = appear.getOrDefault(e.getId(), 0L);
            raws.add(new Raw(e, champs.getOrDefault(e.getId(), 0L), win, app, app == 0 ? 0.0 : (double) win / app));
        }
        raws.sort(Comparator.comparingLong(Raw::champions).reversed()
                .thenComparing(Comparator.comparingDouble(Raw::rate).reversed())
                .thenComparing(Comparator.comparingLong(Raw::wins).reversed())
                .thenComparingInt(r -> r.e().getSortOrder()));

        List<RankRow> rows = new ArrayList<>();
        Raw prev = null;
        int rank = 0;
        for (int i = 0; i < raws.size(); i++) {
            Raw r = raws.get(i);
            boolean tie = prev != null && prev.champions() == r.champions() && prev.wins() == r.wins()
                    && Double.compare(prev.rate(), r.rate()) == 0;
            if (!tie) rank = i + 1; // 동률은 같은 순위
            rows.add(new RankRow(rank, toEntry(r.e()), r.champions(), r.wins(), r.appearances(),
                    (int) Math.round(r.rate() * 100)));
            prev = r;
        }
        return new Ranking(worldcupId, w.getTitle(), plays.countByWorldcupId(worldcupId), rows);
    }

    /* ───────── 삭제 ───────── */

    @Transactional
    public void delete(AppPrincipal me, Long id) {
        Worldcup w = requireAccessible(me, id);
        if (!canDelete(me, w)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "만든 사람이나 가족 관리자만 삭제할 수 있어요.");
        }
        matches.deleteAllByWorldcup(id);
        plays.deleteAllByWorldcup(id);
        entries.deleteAllByWorldcup(id);
        worldcups.deleteById(id);
    }

    /* ───────── 내부 ───────── */

    /** 다른 가족/볼 수 없는 폴더의 월드컵은 존재 자체를 숨긴다(404). */
    private Worldcup requireAccessible(AppPrincipal me, Long id) {
        Worldcup w = worldcups.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "월드컵을 찾을 수 없어요."));
        if (!w.getFamily().getId().equals(me.familyId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "월드컵을 찾을 수 없어요.");
        }
        if (w.getFolder() != null) {
            access.require(me, w.getFolder(), Permission.VIEW);
        }
        return w;
    }

    private WorldcupPlay ownPlay(AppPrincipal me, Long worldcupId, Long playId) {
        return plays.findByIdAndWorldcupIdAndPlayerId(playId, worldcupId, me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "플레이를 찾을 수 없어요."));
    }

    private boolean canDelete(AppPrincipal me, Worldcup w) {
        return me.admin() || w.getCreatedBy().getId().equals(me.userId());
    }

    private AppUser loadUser(AppPrincipal me) {
        return users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }

    private Map<Long, Entry> entryMap(java.util.Collection<Long> ids) {
        Map<Long, Entry> map = new LinkedHashMap<>();
        entries.findByIdIn(ids).forEach(e -> map.put(e.getId(), toEntry(e)));
        return map;
    }

    private static Entry toEntry(WorldcupEntry e) {
        Photo p = e.getPhoto();
        return new Entry(e.getId(), e.getLabel(), p == null ? null : p.getId(), p == null ? null : p.getOriginalFilename(),
                e.getImageUrl(), e.getSourceUrl(), e.getCredit());
    }

    private static String labelOf(Photo p) {
        String d = p.getDescription();
        if (d == null || d.isBlank()) return null;
        d = d.trim();
        return d.length() > 100 ? d.substring(0, 100) : d;
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null) map.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return map;
    }

    private static String cleanTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "주제를 입력해 주세요.");
        if (t.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "주제는 100자 이하여야 해요.");
        return t;
    }

    /** 한 줄에 하나씩. 공백 제거, 대소문자 무시 중복 제거(처음 것 유지) */
    static List<String> parseLabels(String text) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String line : (text == null ? "" : text).split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.length() > 100) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "후보 이름은 100자 이하여야 해요: " + t.substring(0, 20) + "…");
            }
            unique.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
        }
        List<String> labels = new ArrayList<>(unique.values());
        requireCount(labels.size());
        return labels;
    }

    private static void requireCount(int n) {
        if (n < Bracket.MIN_ENTRIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "후보는 최소 " + Bracket.MIN_ENTRIES + "개가 필요해요.");
        }
        if (n > Bracket.MAX_ENTRIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "후보는 최대 " + Bracket.MAX_ENTRIES + "개까지 만들 수 있어요.");
        }
    }
}
