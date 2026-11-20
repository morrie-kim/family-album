package com.familyalbum.api;

import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.AuthService;
import com.familyalbum.web.Dtos.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final AuthService authService;
    private final SecurityContextRepository contextRepository;

    public AuthApiController(@Qualifier("authenticationManager") AuthenticationManager authenticationManager,
                             @Qualifier("securityContextRepository") SecurityContextRepository contextRepository,
                             AuthService authService) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.authService = authService;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    /** 새 게스트 전용 가족 앨범을 만들고 현재 브라우저 세션에 로그인합니다. */
    @PostMapping("/guest")
    public MeResponse guest(HttpServletRequest servletRequest, HttpServletResponse response) {
        AppPrincipal principal = AppPrincipal.from(authService.createGuest());
        HttpSession existing = servletRequest.getSession(false);
        if (existing != null) servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, servletRequest, response);
        return new MeResponse(principal.userId(), principal.name(), principal.email(), principal.familyId());
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
    }
}
