package com.familyalbum.web;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.Visibility;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.FamilyService;
import com.familyalbum.service.PhotoService;
import com.familyalbum.service.PhotoUploadService;
import com.familyalbum.service.PhotoService.FileContent;
import com.familyalbum.web.Dtos.UploadResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
    private final AppProperties props;

    public AlbumController(PhotoService photoService, PhotoUploadService uploadService,
                           FamilyService familyService, AppProperties props) {
        this.photoService = photoService;
        this.uploadService = uploadService;
        this.familyService = familyService;
        this.props = props;
    }

    @GetMapping("/")
    String root() {
        return "redirect:/photos";
    }

    @GetMapping("/photos")
    String list(@AuthenticationPrincipal AppPrincipal me,
                @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("photoPage", photoService.list(me, page, 24));
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
                            @RequestParam(defaultValue = "FAMILY") Visibility visibility) {
        return uploadService.uploadAll(me, files, description, visibility);
    }

    /** JS 비활성 환경용 폴백: 일반 폼 전송 후 리다이렉트 */
    @PostMapping("/photos")
    String upload(@AuthenticationPrincipal AppPrincipal me,
                  @RequestParam("files") List<MultipartFile> files,
                  @RequestParam(required = false) String description,
                  @RequestParam(defaultValue = "FAMILY") Visibility visibility,
                  RedirectAttributes ra) {
        try {
            UploadResult r = uploadService.uploadAll(me, files, description, visibility);
            if (!r.uploaded().isEmpty()) {
                ra.addFlashAttribute("message", r.uploaded().size() + "장을 올렸어요.");
            }
            if (!r.failed().isEmpty()) {
                String names = r.failed().stream().limit(3)
                        .map(f -> f.filename() + " (" + f.reason() + ")")
                        .collect(java.util.stream.Collectors.joining(", "));
                String more = r.failed().size() > 3 ? " 외 " + (r.failed().size() - 3) + "장" : "";
                ra.addFlashAttribute("error", r.failed().size() + "장은 올리지 못했어요: " + names + more);
            }
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/photos";
    }

    @PostMapping("/photos/{id}/delete")
    String delete(@AuthenticationPrincipal AppPrincipal me, @PathVariable Long id, RedirectAttributes ra) {
        try {
            photoService.delete(me, id);
            ra.addFlashAttribute("message", "사진을 삭제했어요.");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/photos";
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
