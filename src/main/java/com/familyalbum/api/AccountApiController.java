package com.familyalbum.api;

import com.familyalbum.domain.AppUser;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.AuthService;
import com.familyalbum.service.FamilyService;
import com.familyalbum.web.Dtos.FamilyResponse;
import com.familyalbum.web.Dtos.MeResponse;
import com.familyalbum.web.Dtos.SignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "계정/가족", description = "회원가입, 내 정보, 가족 구성원 조회")
public class AccountApiController {

    private final AuthService authService;
    private final FamilyService familyService;

    public AccountApiController(AuthService authService, FamilyService familyService) {
        this.authService = authService;
        this.familyService = familyService;
    }

    @Operation(summary = "회원가입",
            description = "초대 코드가 있으면 기존 가족에 합류, 없으면 가족 이름으로 새 가족을 생성합니다. 인증 불필요.")
    @SecurityRequirements // 이 엔드포인트는 인증 없음
    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signup(@Valid @RequestBody SignupRequest request) {
        AppUser u = authService.signup(request);
        return new MeResponse(u.getId(), u.getName(), u.getEmail(), u.getFamily().getId());
    }

    @Operation(summary = "내 정보")
    @GetMapping("/me")
    public MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me) {
        return new MeResponse(me.userId(), me.name(), me.email(), me.familyId());
    }

    @Operation(summary = "내 가족 정보", description = "가족 이름, 초대 코드, 구성원 목록")
    @GetMapping("/family")
    public FamilyResponse family(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me) {
        return familyService.get(me);
    }
}
