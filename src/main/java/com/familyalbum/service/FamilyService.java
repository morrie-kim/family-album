package com.familyalbum.service;

import com.familyalbum.domain.Family;
import com.familyalbum.repository.AppUserRepository;
import com.familyalbum.repository.FamilyRepository;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.Dtos.FamilyResponse;
import com.familyalbum.web.Dtos.MemberResponse;
import com.familyalbum.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyService {

    private final FamilyRepository families;
    private final AppUserRepository users;

    public FamilyService(FamilyRepository families, AppUserRepository users) {
        this.families = families;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public FamilyResponse get(AppPrincipal me) {
        Family f = families.findById(me.familyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "가족 정보를 찾을 수 없습니다."));
        var members = users.findByFamilyIdOrderByIdAsc(f.getId()).stream()
                .map(u -> new MemberResponse(u.getId(), u.getName(), u.getEmail()))
                .toList();
        return new FamilyResponse(f.getId(), f.getName(), f.getInviteCode(), members);
    }
}
