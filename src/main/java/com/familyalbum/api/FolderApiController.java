package com.familyalbum.api;

import com.familyalbum.domain.AccessLevel;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.FolderService;
import com.familyalbum.web.Dtos.AccessGrant;
import com.familyalbum.web.Dtos.FolderDetail;
import com.familyalbum.web.Dtos.FolderRequest;
import com.familyalbum.web.Dtos.FolderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/folders")
@Tag(name = "폴더/접근 권한", description = "폴더 관리와 계정별 접근 권한 설정")
public class FolderApiController {

    private final FolderService folderService;

    public FolderApiController(FolderService folderService) {
        this.folderService = folderService;
    }

    @Operation(summary = "내가 접근할 수 있는 폴더 목록",
            description = "permission: VIEW(보기) / EDIT(업로드·내 사진 삭제) / MANAGE(가족 관리자·폴더 생성자)")
    @GetMapping
    public List<FolderResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me) {
        return folderService.list(me);
    }

    @Operation(summary = "폴더 만들기", description = "access 에 담은 계정만 접근할 수 있습니다(가족 관리자·만든 사람은 항상 전체 권한).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderDetail create(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me,
                               @Valid @RequestBody FolderRequest request) {
        return folderService.create(me, request.name(), toMap(request.access()));
    }

    @Operation(summary = "폴더 권한 상세 (MANAGE 필요)")
    @GetMapping("/{id}")
    public FolderDetail detail(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me, @PathVariable Long id) {
        return folderService.detail(me, id);
    }

    @Operation(summary = "폴더 이름·접근 권한 수정 (MANAGE 필요)",
            description = "구성원 전체의 권한을 이 요청의 access 로 교체합니다. 목록에 없으면 접근 불가가 됩니다.")
    @PutMapping("/{id}")
    public FolderDetail update(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me, @PathVariable Long id,
                               @Valid @RequestBody FolderRequest request) {
        return folderService.update(me, id, request.name(), toMap(request.access()));
    }

    @Operation(summary = "폴더 삭제 (MANAGE 필요)", description = "기본 폴더이거나 사진이 남아 있으면 409")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me, @PathVariable Long id) {
        folderService.delete(me, id);
    }

    private static Map<Long, AccessLevel> toMap(List<AccessGrant> grants) {
        Map<Long, AccessLevel> map = new HashMap<>();
        if (grants != null) {
            grants.forEach(g -> {
                if (g.userId() != null && g.level() != null) map.put(g.userId(), g.level());
            });
        }
        return map;
    }
}
