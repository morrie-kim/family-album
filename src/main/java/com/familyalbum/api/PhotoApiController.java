package com.familyalbum.api;

import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.PhotoService;
import com.familyalbum.service.PhotoUploadService;
import com.familyalbum.service.PhotoService.FileContent;
import com.familyalbum.web.Dtos.PageResponse;
import com.familyalbum.web.Dtos.PhotoResponse;
import com.familyalbum.web.Dtos.UploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
@Tag(name = "사진", description = "가족 사진 업로드/조회/삭제")
public class PhotoApiController {

    private final PhotoService photoService;
    private final PhotoUploadService uploadService;

    public PhotoApiController(PhotoService photoService, PhotoUploadService uploadService) {
        this.photoService = photoService;
        this.uploadService = uploadService;
    }

    @Operation(summary = "사진 업로드",
            description = "jpeg/png/webp/gif/heic, 최대 20MB. folderId 폴더에 대한 EDIT 이상 권한이 필요합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse upload(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me,
                                @RequestPart("file") MultipartFile file,
                                @RequestParam(required = false) String description,
                                @RequestParam Long folderId) {
        return photoService.upload(me, file, description, folderId);
    }

    @Operation(summary = "사진 다중 업로드",
            description = "한 요청에 최대 50장(설정값). 파일별로 독립 처리되어 일부가 실패해도 성공한 파일은 저장되며, "
                    + "결과의 uploaded/failed 로 파일별 성공·실패를 확인합니다. 모두 실패해도 HTTP 200 입니다.")
    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult uploadBatch(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me,
                                    @RequestPart("files") List<MultipartFile> files,
                                    @RequestParam(required = false) String description,
                                    @RequestParam Long folderId) {
        return uploadService.uploadAll(me, files, description, folderId);
    }

    @Operation(summary = "사진 목록", description = "내가 접근할 수 있는 폴더의 사진, 최신순. folderId 로 한 폴더만 조회할 수 있습니다.")
    @GetMapping
    public PageResponse<PhotoResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me,
                                            @RequestParam(required = false) Long folderId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "24") int size) {
        return photoService.list(me, folderId, page, size);
    }

    @Operation(summary = "사진 원본 다운로드/표시")
    @ApiResponse(responseCode = "200", description = "이미지 바이트",
            content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "없거나 볼 권한이 없음")
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me,
                                         @PathVariable Long id) {
        FileContent f = photoService.file(me, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(f.filename(), StandardCharsets.UTF_8).build().toString())
                .body(f.resource());
    }

    @Operation(summary = "사진 삭제", description = "폴더 관리자(MANAGE) 또는 EDIT 권한이 있는 업로더 본인만 삭제할 수 있습니다.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) @AuthenticationPrincipal AppPrincipal me, @PathVariable Long id) {
        photoService.delete(me, id);
    }
}
