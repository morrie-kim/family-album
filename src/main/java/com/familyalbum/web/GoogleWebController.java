package com.familyalbum.web;

import com.familyalbum.google.GoogleConnectionService;
import com.familyalbum.google.GoogleImportService;
import com.familyalbum.google.GoogleOAuthClient;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.FolderService;
import com.familyalbum.web.Dtos.GoogleImportResult;
import com.familyalbum.web.Dtos.GoogleSession;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 구글 포토 연결(OAuth) 및 Picker 가져오기 */
@Controller
@RequestMapping("/google")
public class GoogleWebController {

    private static final String STATE_KEY = "googleOAuthState";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleConnectionService connections;
    private final GoogleImportService imports;
    private final GoogleOAuthClient oauth;
    private final FolderService folderService;

    public GoogleWebController(GoogleConnectionService connections, GoogleImportService imports,
                               GoogleOAuthClient oauth, FolderService folderService) {
        this.connections = connections;
        this.imports = imports;
        this.oauth = oauth;
        this.folderService = folderService;
    }

    @GetMapping
    String page(@AuthenticationPrincipal AppPrincipal me, Model model) {
        boolean enabled = connections.enabled();
        model.addAttribute("enabled", enabled);
        model.addAttribute("connected", enabled && connections.connected(me.userId()));
        model.addAttribute("uploadFolders", folderService.uploadable(me));
        model.addAttribute("redirectUri", oauth.redirectUri());
        return "google";
    }

    @GetMapping("/connect")
    String connect(HttpSession session, RedirectAttributes ra) {
        if (!connections.enabled()) {
            ra.addFlashAttribute("error", "서버에 구글 연동이 설정되어 있지 않아요.");
            return "redirect:/google";
        }
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        session.setAttribute(STATE_KEY, state); // CSRF 방지: 콜백에서 대조
        return "redirect:" + oauth.authorizationUrl(state);
    }

    @GetMapping("/callback")
    String callback(@AuthenticationPrincipal AppPrincipal me,
                    @RequestParam(required = false) String code,
                    @RequestParam(required = false) String state,
                    @RequestParam(required = false) String error,
                    HttpSession session, RedirectAttributes ra) {
        String expected = (String) session.getAttribute(STATE_KEY);
        session.removeAttribute(STATE_KEY);
        if (error != null) {
            ra.addFlashAttribute("error", "구글 연결이 취소됐어요.");
        } else if (expected == null || state == null || code == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            ra.addFlashAttribute("error", "잘못된 연결 요청이에요. 다시 시도해 주세요.");
        } else {
            try {
                connections.connect(me.userId(), code);
                ra.addFlashAttribute("message", "구글 포토를 연결했어요.");
            } catch (ApiException e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/google";
    }

    @PostMapping("/disconnect")
    String disconnect(@AuthenticationPrincipal AppPrincipal me, RedirectAttributes ra) {
        connections.disconnect(me.userId());
        ra.addFlashAttribute("message", "구글 포토 연결을 해제했어요.");
        return "redirect:/google";
    }

    /* ── JS(fetch)용 JSON 엔드포인트 ── */

    @PostMapping(path = "/picker/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    GoogleSession startSession(@AuthenticationPrincipal AppPrincipal me, @RequestParam Long folderId) {
        return imports.start(me, folderId);
    }

    @GetMapping(path = "/picker/sessions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    Map<String, Boolean> sessionStatus(@AuthenticationPrincipal AppPrincipal me, @PathVariable String id) {
        return Map.of("ready", imports.isReady(me, id));
    }

    @PostMapping(path = "/picker/sessions/{id}/import", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    GoogleImportResult importSession(@AuthenticationPrincipal AppPrincipal me, @PathVariable String id,
                                     @RequestParam Long folderId) {
        return imports.importPicked(me, id, folderId);
    }
}
