package com.familyalbum.api;

import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.ApiException;
import com.familyalbum.worldcup.WorldcupDtos.*;
import com.familyalbum.worldcup.WorldcupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/worldcups")
public class WorldcupApiController {

    public record CreateRequest(@NotBlank @Size(max=100) String title, String mode, String labels,
                                Long folderId, List<Long> photoIds, String query, List<String> imageIds) {}
    public record StartRequest(int rounds) {}
    public record PickRequest(Long winnerId) {}

    private final WorldcupService service;

    public WorldcupApiController(WorldcupService service) {
        this.service = service;
    }

    @GetMapping
    public List<Summary> list(@AuthenticationPrincipal AppPrincipal me) {
        return service.list(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@AuthenticationPrincipal AppPrincipal me, @Valid @RequestBody CreateRequest r) {
        Long id = switch (r.mode() == null ? "text" : r.mode()) {
            case "photo" -> service.createFromPhotos(me, r.title(), r.folderId(), r.photoIds());
            case "web" -> service.createFromWeb(me, r.title(), r.query(), r.imageIds());
            default -> service.createFromLabels(me, r.title(), r.labels());
        };
        return Map.of("id", id);
    }

    @GetMapping("/{id}")
    public Detail detail(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id) {
        return service.detail(me, id);
    }

    @PostMapping("/{id}/plays")
    public Map<String, Long> start(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id,
                                   @RequestBody StartRequest request) {
        return Map.of("playId", service.startPlay(me, id, request.rounds()));
    }

    @GetMapping("/{id}/plays/{playId}")
    public MatchView current(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id,
                             @PathVariable Long playId) {
        return service.currentMatch(me, id, playId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "월드컵이 끝났어요. 결과를 확인해 주세요."));
    }

    @PostMapping("/{id}/plays/{playId}/pick")
    public Map<String, Boolean> pick(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id,
                                    @PathVariable Long playId, @RequestBody PickRequest request) {
        return Map.of("done", service.pick(me, id, playId, request.winnerId()));
    }

    @GetMapping("/{id}/plays/{playId}/result")
    public PlayResult result(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id,
                             @PathVariable Long playId) {
        return service.playResult(me, id, playId);
    }

    @GetMapping("/{id}/results")
    public Ranking ranking(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id) {
        return service.ranking(me, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id) {
        service.delete(me, id);
    }
}
