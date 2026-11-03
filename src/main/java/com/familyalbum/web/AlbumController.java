package com.familyalbum.web;

import com.familyalbum.config.AppProperties;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.FamilyService;
import com.familyalbum.service.FolderService;
import com.familyalbum.service.PhotoService;
import com.familyalbum.service.PhotoService.FileContent;
import com.familyalbum.service.PhotoUploadService;
import com.familyalbum.web.Dtos.FolderResponse;
import com.familyalbum.web.Dtos.UploadResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AlbumController {

    private final PhotoService photoService;
    private final PhotoUploadService uploadService;
    private final FamilyService familyService;
    private final FolderService folderService;
    private final AppProperties props;

    public AlbumController(PhotoService photoService, PhotoUploadService uploadService, FamilyService familyService,
                           FolderService folderService, AppProperties props) {
        this.photoService = photoService;
        this.uploadService = uploadService;
        this.familyService = familyService;
        this.folderService = folderService;
        this.props = props;
    }

    @GetMapping("/")
    String root() {
        return "redirect:/photos";
    }

    /** folder 파라미터가 없으면 내가 접근 가능한 모든 폴더의 사진 */
    @GetMapping("/photos")
    String list(@AuthenticationPrincipal AppPrincipal me,
                @RequestParam(required = false) Long folder,
                @RequestParam(defaultValue = "0") int page, Model model) {
        List<FolderResponse> folders = folderService.list(me);
        FolderResponse current = null;
        if (folder != null) {
            current = folders.stream().filter(f -> f.id().equals(folder)).findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        }
        model.addAttribute("folders", folders);
        model.addAttribute("currentFolder", current);
        model.addAttribute("uploadFolders", folders.stream().filter(FolderResponse::canEdit).toList());
        model.addAttribute("photoPage", photoService.list(me, folder, page, 24));
        long maxBytes = props.upload().maxFileSize().toBytes();
        model.addAttribute("maxFileBytes", maxBytes);
        model.addAttribute("maxFileMb", maxBytes / (1024 * 1024));
        return "photos";
    }

    /** 화면의 업로드 시트(JS)가 사용하는 Ajax 엔드포인트. 파일별 성공/실패를 JSON 으로 반환. */
    @PostMapping(path = "/photos", headers = "X-Requested-With=XMLHttpRequest",
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    UploadResult uploadAjax(@AuthenticationPrincipal AppPrincipal me,
                            @RequestParam("files") List<MultipartFile> files,
                            @RequestParam(required = false) String description,
                            @RequestParam Long folderId) {
        return uploadService.uploadAll(me, files, description, folderId);
    }

    /** JS 비활성 환경용 폴백: 일반 폼 전송 후 리다이렉트 */
    @PostMapping("/photos")
    String upload(@AuthenticationPrincipal AppPrincipal me,
                  @RequestParam("files") List<MultipartFile> files,
                  @RequestParam(required = false) String description,
                  @RequestParam Long folderId,
                  RedirectAttributes ra) {
        try {
            UploadResult r = uploadService.uploadAll(me, files, description, folderId);
            if (!r.uploaded().isEmpty()) {
                ra.addFlashAttribute("message", r.uploaded().size() + "장을 올렸어요.");
            }
            if (!r.failed().isEmpty()) {
                String names = r.failed().stream().limit(3)
                        .map(f -> f.filename() + " (" + f.reason() + ")")
                        .collect(Collectors.joining(", "));
                String more = r.failed().size() > 3 ? " 외 " + (r.failed().size() - 3) + "장" : "";
                ra.addFlashAttribute("error", r.failed().size() + "장은 올리지 못했어요: " + names + more);
            }
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/photos?folder=" + folderId;
    }

    @PostMapping("/photos/{id}/delete")
    String delete(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id,
                  @RequestParam(required = false) Long folder, RedirectAttributes ra) {
        try {
            photoService.delete(me, id);
            ra.addFlashAttribute("message", "사진을 삭제했어요.");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return folder != null ? "redirect:/photos?folder=" + folder : "redirect:/photos";
    }

    @GetMapping("/photos/{id}/file")
    ResponseEntity<Resource> file(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id) {
        FileContent f = photoService.file(me, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(f.filename(), StandardCharsets.UTF_8).build().toString())
                .body(f.resource());
    }

    @GetMapping("/family")
    String family(@AuthenticationPrincipal AppPrincipal me, Model model) {
        model.addAttribute("family", familyService.get(me));
        return "family";
    }
}
