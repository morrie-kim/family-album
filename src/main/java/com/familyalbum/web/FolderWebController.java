package com.familyalbum.web;

import com.familyalbum.domain.AccessLevel;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.FolderService;
import com.familyalbum.web.Dtos.FolderDetail;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 폴더 만들기 / 설정(이름, 계정별 접근 권한) / 삭제 화면 */
@Controller
@RequestMapping("/folders")
public class FolderWebController {

    private final FolderService folderService;

    public FolderWebController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping("/new")
    String newForm(@AuthenticationPrincipal AppPrincipal me, Model model) {
        model.addAttribute("folder", folderService.blank(me));
        return "folder-form";
    }

    @PostMapping
    String create(@AuthenticationPrincipal AppPrincipal me, @RequestParam String name,
                  @RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        try {
            FolderDetail created = folderService.create(me, name, parseGrants(params));
            ra.addFlashAttribute("message", "폴더를 만들었어요.");
            return "redirect:/photos?folder=" + created.id();
        } catch (ApiException e) {
            return redisplay(model, folderService.blank(me), name, params, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    String edit(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, Model model) {
        model.addAttribute("folder", folderService.detail(me, id));
        return "folder-form";
    }

    @PostMapping("/{id}")
    String update(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, @RequestParam String name,
                  @RequestParam Map<String, String> params, Model model, RedirectAttributes ra) {
        try {
            folderService.update(me, id, name, parseGrants(params));
            ra.addFlashAttribute("message", "폴더 설정을 저장했어요.");
            return "redirect:/photos?folder=" + id;
        } catch (ApiException e) {
            if (e.status() == HttpStatus.NOT_FOUND || e.status() == HttpStatus.FORBIDDEN) throw e;
            return redisplay(model, folderService.detail(me, id), name, params, e.getMessage());
        }
    }

    @PostMapping("/{id}/delete")
    String delete(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, RedirectAttributes ra) {
        try {
            folderService.delete(me, id);
            ra.addFlashAttribute("message", "폴더를 삭제했어요.");
            return "redirect:/photos";
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/folders/" + id;
        }
    }

    /** 오류 시 사용자가 입력한 이름·권한 선택을 유지한 채 폼을 다시 보여준다. */
    private String redisplay(Model model, FolderDetail base, String name, Map<String, String> params, String error) {
        Map<Long, AccessLevel> chosen = parseGrants(params);
        var members = base.members().stream().map(m -> m.implicit() ? m
                : new Dtos.MemberAccess(m.userId(), m.name(), m.email(),
                        chosen.containsKey(m.userId()) ? chosen.get(m.userId()).name() : "NONE", false, null)).toList();
        model.addAttribute("folder", new FolderDetail(base.id(), name, base.defaultFolder(), members));
        model.addAttribute("error", error);
        return "folder-form";
    }

    /** 폼 필드 access_{userId} = NONE | VIEW | EDIT → 부여 목록(NONE 은 제외) */
    static Map<Long, AccessLevel> parseGrants(Map<String, String> params) {
        Map<Long, AccessLevel> grants = new HashMap<>();
        params.forEach((k, v) -> {
            if (!k.startsWith("access_")) return;
            long userId;
            try {
                userId = Long.parseLong(k.substring("access_".length()));
            } catch (NumberFormatException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
            }
            if ("VIEW".equals(v)) grants.put(userId, AccessLevel.VIEW);
            else if ("EDIT".equals(v)) grants.put(userId, AccessLevel.EDIT);
        });
        return grants;
    }
}
