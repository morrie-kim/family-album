package com.familyalbum.api;

import com.familyalbum.google.GoogleConnectionService;
import com.familyalbum.google.GoogleImportService;
import com.familyalbum.google.GoogleOAuthClient;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.FolderService;
import com.familyalbum.web.Dtos.GoogleImportResult;
import com.familyalbum.web.Dtos.GoogleSession;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/google")
public class GoogleApiController {
    private static final String STATE_KEY = "googleOAuthState";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Status(boolean enabled, boolean connected, String redirectUri) {}

    private final GoogleConnectionService connections;
    private final GoogleImportService imports;
    private final GoogleOAuthClient oauth;

    public GoogleApiController(GoogleConnectionService connections, GoogleImportService imports,
                               GoogleOAuthClient oauth, FolderService folders) {
        this.connections = connections;
        this.imports = imports;
        this.oauth = oauth;
    }

    @GetMapping("/status")
    public Status status(@AuthenticationPrincipal AppPrincipal me) {
        boolean enabled = connections.enabled();
        return new Status(enabled, enabled && connections.connected(me.userId()), oauth.redirectUri());
    }

    @PostMapping("/connect")
    public Map<String, String> connect(HttpSession session) {
        if (!connections.enabled()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Google Photos 연동이 설정되지 않았습니다.");
        }
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        session.setAttribute(STATE_KEY, state);
        return Map.of("authorizationUrl", oauth.authorizationUrl(state));
    }

    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal AppPrincipal me) {
        connections.disconnect(me.userId());
    }

    @PostMapping("/picker/sessions")
    public GoogleSession startSession(@AuthenticationPrincipal AppPrincipal me, @RequestParam Long folderId) {
        return imports.start(me, folderId);
    }

    @GetMapping("/picker/sessions/{id}")
    public Map<String, Boolean> sessionStatus(@AuthenticationPrincipal AppPrincipal me, @PathVariable String id) {
        return Map.of("ready", imports.isReady(me, id));
    }

    @PostMapping("/picker/sessions/{id}/import")
    public GoogleImportResult importSession(@AuthenticationPrincipal AppPrincipal me, @PathVariable String id,
                                            @RequestParam Long folderId) {
        return imports.importPicked(me, id, folderId);
    }
}
