package com.familyalbum.web;

import com.familyalbum.domain.AppUser;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.AuthService;
import com.familyalbum.web.Dtos.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthWebController {

    private final AuthService authService;

    public AuthWebController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    String login(@AuthenticationPrincipal AppPrincipal me) {
        return me != null ? "redirect:/photos" : "login";
    }

    @GetMapping("/signup")
    String signupForm(@AuthenticationPrincipal AppPrincipal me, Model model) {
        if (me != null) {
            return "redirect:/photos";
        }
        model.addAttribute("form", new SignupRequest(null, null, null, null, null));
        return "signup";
    }

    @PostMapping("/signup")
    String signup(@Valid @ModelAttribute("form") SignupRequest form, BindingResult errors, Model model,
                  HttpServletRequest request, HttpServletResponse response) {
        if (errors.hasErrors()) {
            model.addAttribute("error", errors.getAllErrors().get(0).getDefaultMessage());
            return "signup";
        }
        try {
            AppUser user = authService.signup(form);
            signIn(AppPrincipal.from(user), request, response);
            return "redirect:/photos";
        } catch (ApiException e) {
            model.addAttribute("error", e.getMessage());
            return "signup";
        }
    }

    /** 가입 직후 자동 로그인 (session fixation 방지를 위해 세션 ID 교체) */
    private void signIn(AppPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }
}
