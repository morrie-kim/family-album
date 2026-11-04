package com.familyalbum.web;

import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.FolderService;
import com.familyalbum.service.PhotoService;
import com.familyalbum.web.Dtos.FolderResponse;
import com.familyalbum.worldcup.WorldcupDtos.MatchView;
import com.familyalbum.worldcup.WorldcupService;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 이상형 월드컵: 주제 입력 → 후보(텍스트/앨범 사진) 등록 → 라운드 선택 → 진행 → 결과·랭킹 */
@Controller
@RequestMapping("/worldcups")
public class WorldcupWebController {

    private final WorldcupService service;
    private final FolderService folderService;
    private final PhotoService photoService;

    public WorldcupWebController(WorldcupService service, FolderService folderService, PhotoService photoService) {
        this.service = service;
        this.folderService = folderService;
        this.photoService = photoService;
    }

    @GetMapping
    String list(@AuthenticationPrincipal AppPrincipal me, Model model) {
        model.addAttribute("worldcups", service.list(me));
        return "worldcup-list";
    }

    @GetMapping("/new")
    String newForm(@AuthenticationPrincipal AppPrincipal me,
                   @RequestParam(defaultValue = "text") String mode,
                   @RequestParam(required = false) Long folder,
                   @RequestParam(required = false) String title, Model model) {
        prepareNew(model, me, mode, folder, title, "", List.of(), null);
        return "worldcup-new";
    }

    @PostMapping
    String create(@AuthenticationPrincipal AppPrincipal me, @RequestParam String title,
                  @RequestParam(defaultValue = "text") String mode,
                  @RequestParam(required = false) String labels,
                  @RequestParam(required = false) Long folderId,
                  @RequestParam(name = "photoIds", required = false) List<Long> photoIds,
                  Model model, RedirectAttributes ra) {
        try {
            Long id = "photo".equals(mode)
                    ? service.createFromPhotos(me, title, folderId, photoIds)
                    : service.createFromLabels(me, title, labels);
            ra.addFlashAttribute("message", "월드컵을 만들었어요.");
            return "redirect:/worldcups/" + id;
        } catch (ApiException e) {
            prepareNew(model, me, mode, folderId, title, labels, photoIds, e.getMessage());
            return "worldcup-new";
        }
    }

    @GetMapping("/{id}")
    String detail(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("wc", service.detail(me, id));
        return "worldcup-detail";
    }

    @PostMapping("/{id}/plays")
    String start(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id,
                 @RequestParam int rounds, RedirectAttributes ra) {
        try {
            return "redirect:/worldcups/" + id + "/plays/" + service.startPlay(me, id, rounds);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/worldcups/" + id;
        }
    }

    /** 진행 화면. 이미 끝난 플레이면 결과로 이동 */
    @GetMapping("/{id}/plays/{playId}")
    String play(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, @PathVariable Long playId,
                Model model) {
        Optional<MatchView> match = service.currentMatch(me, id, playId);
        if (match.isEmpty()) {
            return "redirect:/worldcups/" + id + "/plays/" + playId + "/result";
        }
        model.addAttribute("match", match.get());
        return "worldcup-play";
    }

    @PostMapping("/{id}/plays/{playId}/pick")
    String pick(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, @PathVariable Long playId,
                @RequestParam Long winnerId) {
        try {
            boolean done = service.pick(me, id, playId, winnerId);
            return "redirect:/worldcups/" + id + "/plays/" + playId + (done ? "/result" : "");
        } catch (ApiException | ObjectOptimisticLockingFailureException e) {
            // 더블 클릭 등으로 이미 처리된 선택은 조용히 현재 대진으로 되돌린다.
            return "redirect:/worldcups/" + id + "/plays/" + playId;
        }
    }

    @GetMapping("/{id}/plays/{playId}/result")
    String result(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, @PathVariable Long playId,
                  Model model) {
        try {
            model.addAttribute("result", service.playResult(me, id, playId));
        } catch (ApiException e) {
            if (e.status() == HttpStatus.CONFLICT) { // 아직 끝나지 않음
                return "redirect:/worldcups/" + id + "/plays/" + playId;
            }
            throw e;
        }
        model.addAttribute("ranking", service.ranking(me, id));
        return "worldcup-result";
    }

    @GetMapping("/{id}/results")
    String ranking(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("ranking", service.ranking(me, id));
        return "worldcup-result";
    }

    @PostMapping("/{id}/delete")
    String delete(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, RedirectAttributes ra) {
        try {
            service.delete(me, id);
            ra.addFlashAttribute("message", "월드컵을 삭제했어요.");
            return "redirect:/worldcups";
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/worldcups/" + id;
        }
    }

    private void prepareNew(Model model, AppPrincipal me, String mode, Long folderId, String title,
                            String labels, List<Long> selected, String error) {
        boolean photoMode = "photo".equals(mode);
        model.addAttribute("mode", photoMode ? "photo" : "text");
        model.addAttribute("title", title == null ? "" : title);
        model.addAttribute("labels", labels == null ? "" : labels);
        model.addAttribute("selectedIds", selected == null ? Set.<Long>of() : new HashSet<>(selected));
        List<FolderResponse> folders = folderService.list(me);
        model.addAttribute("folders", folders);
        if (photoMode && !folders.isEmpty()) {
            FolderResponse current = folders.stream().filter(f -> f.id().equals(folderId)).findFirst()
                    .orElse(folders.get(0));
            model.addAttribute("currentFolder", current);
            // 한 번에 고를 수 있는 사진은 해당 폴더의 최신 60장
            model.addAttribute("photoChoices", photoService.list(me, current.id(), 0, 60).content());
        }
        if (error != null) {
            model.addAttribute("error", error);
        }
    }
}
