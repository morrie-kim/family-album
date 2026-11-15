package com.familyalbum.api;

import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.Dtos.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import com.familyalbum.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    public record LoginRequest(String email, String password) {}

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    public AuthApiController(AuthenticationManager authenticationManager,
                             SecurityContextRepository contextRepository) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public MeResponse login(@RequestBody LoginRequest request, HttpServletRequest servletRequest,
                            HttpServletResponse response) {
        if (request.email() == null || request.password() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요.");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요.");
        }
        HttpSession existing = servletRequest.getSession(false);
        if (existing != null) servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, servletRequest, response);
        AppPrincipal principal = (AppPrincipal) authentication.getPrincipal();
        return new MeResponse(principal.userId(), principal.name(), principal.email(), principal.familyId());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppPrincipal me) {
        return new MeResponse(me.userId(), me.name(), me.email(), me.familyId());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.addHeader("Set-Cookie", "JSESSIONID=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
    }
}
