package com.familyalbum.web;

import com.familyalbum.domain.AccessLevel;
import com.familyalbum.domain.Permission;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    @Schema(description = "회원가입 요청. inviteCode 가 있으면 기존 가족에 합류하고, 없으면 familyName 으로 새 가족을 만든다.")
    public record SignupRequest(
            @Schema(description = "로그인 ID(이메일)", example = "dad@example.com")
            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 255, message = "이메일이 너무 깁니다.") String email,
            @Schema(description = "비밀번호(8~72자)", example = "change-me-1234")
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.") String password,
            @Schema(description = "표시 이름", example = "아빠")
            @NotBlank(message = "이름을 입력해 주세요.")
            @Size(max = 50, message = "이름은 50자 이하여야 합니다.") String name,
            @Schema(description = "가족에게 받은 초대 코드(합류 시)", example = "A1B2C3D4", nullable = true)
            @Size(max = 16, message = "초대 코드가 올바르지 않습니다.") String inviteCode,
            @Schema(description = "새로 만들 가족 이름(신규 가족 생성 시)", example = "김씨네", nullable = true)
            @Size(max = 100, message = "가족 이름은 100자 이하여야 합니다.") String familyName) {}

    @Schema(description = "로그인한 사용자 정보")
    public record MeResponse(Long id, String name, String email, Long familyId) {}

    @Schema(description = "가족 구성원. role: ADMIN(가족 관리자) / MEMBER")
    public record MemberResponse(Long id, String name, String email, String role) {}

    @Schema(description = "가족 정보와 구성원")
    public record FamilyResponse(Long id, String name,
                                 @Schema(description = "가족 합류용 초대 코드") String inviteCode,
                                 List<MemberResponse> members) {}

    @Schema(description = "내가 접근할 수 있는 폴더. permission: VIEW/EDIT/MANAGE")
    public record FolderResponse(Long id, String name, boolean defaultFolder, Permission permission, long photoCount) {
        @JsonIgnore @Schema(hidden = true)
        public boolean canEdit() { return permission.atLeast(Permission.EDIT); }
        @JsonIgnore @Schema(hidden = true)
        public boolean canManage() { return permission.atLeast(Permission.MANAGE); }
    }

    @Schema(description = "폴더 설정 화면용: 구성원별 현재 권한")
    public record MemberAccess(Long userId, String name, String email,
                               @Schema(description = "NONE / VIEW / EDIT / MANAGE(관리자·폴더 생성자, 변경 불가)") String level,
                               @Schema(description = "관리자·폴더 생성자라 항상 모든 권한을 갖는지 여부") boolean implicit,
                               String implicitReason) {}

    public record FolderDetail(Long id, String name, boolean defaultFolder, List<MemberAccess> members) {}

    @Schema(description = "계정별 폴더 권한 부여. 목록에 없는 구성원은 접근 불가(NONE)")
    public record AccessGrant(Long userId, AccessLevel level) {}

    public record FolderRequest(
            @Schema(description = "폴더 이름", example = "제주 여행")
            @NotBlank(message = "폴더 이름을 입력해 주세요.")
            @Size(max = 100, message = "폴더 이름은 100자 이하여야 합니다.") String name,
            List<AccessGrant> access) {}

    @Schema(description = "사진 메타데이터")
    public record PhotoResponse(Long id, String originalFilename, String description,
                                Long folderId, String folderName,
                                @Schema(description = "파일 크기(bytes)") long size,
                                Long uploaderId, String uploaderName, Instant createdAt,
                                @Schema(description = "내가 올린 사진인지 여부") boolean mine,
                                @Schema(description = "내가 삭제할 수 있는지 여부") boolean canDelete) {
        @JsonIgnore @Schema(hidden = true)
        public LocalDateTime createdLocal() {
            return LocalDateTime.ofInstant(createdAt, ZoneId.of("Asia/Seoul"));
        }
    }

    @Schema(description = "페이지 응답(page 는 0부터 시작)")
    public record PageResponse<T>(List<T> content, int page, int totalPages, long totalElements) {}

    @Schema(description = "다중 업로드에서 실패한 파일")
    public record UploadFailure(
            @Schema(description = "원본 파일명", example = "IMG_0001.jpg") String filename,
            @Schema(description = "실패 사유", example = "지원하지 않는 이미지 형식입니다.") String reason) {}

    @Schema(description = "다중 업로드 결과. 일부 파일이 실패해도 성공한 파일은 저장된다(파일별 독립 처리).")
    public record UploadResult(List<PhotoResponse> uploaded, List<UploadFailure> failed) {}

    @Schema(description = "구글 포토 가져오기 결과")
    public record GoogleImportResult(int imported, int skipped, List<UploadFailure> failed) {}

    @Schema(description = "구글 포토 사진 선택 세션")
    public record GoogleSession(String sessionId, String pickerUri, long pollIntervalMs, long timeoutMs) {}
}
