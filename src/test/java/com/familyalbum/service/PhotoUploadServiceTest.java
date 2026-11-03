package com.familyalbum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.Permission;
import com.familyalbum.web.ApiException;
import com.familyalbum.web.Dtos.PhotoResponse;
import com.familyalbum.web.Dtos.UploadResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

class PhotoUploadServiceTest {

    private final PhotoService photoService = mock(PhotoService.class);
    private final FolderAccessService access = mock(FolderAccessService.class);
    private final PhotoUploadService service = new PhotoUploadService(photoService, access,
            new AppProperties(new AppProperties.Storage("build/x"),
                    new AppProperties.Upload(3, DataSize.ofMegabytes(20)), null, null, null));

    private static MockMultipartFile file(String name, String type) {
        return new MockMultipartFile("files", name, type, new byte[] {1, 2, 3});
    }

    @Test
    void failureOfOneFileDoesNotAffectOthers() {
        PhotoResponse ok = new PhotoResponse(1L, "a.jpg", null, 7L, "가족 앨범", 3, 1L, "아빠", Instant.now(), true, true);
        doReturn(ok).when(photoService)
                .upload(any(), argThat(f -> f != null && "a.jpg".equals(f.getOriginalFilename())), any(), any());
        doThrow(new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식입니다."))
                .when(photoService)
                .upload(any(), argThat(f -> f != null && "b.txt".equals(f.getOriginalFilename())), any(), any());
        doThrow(new IllegalStateException("disk full")).when(photoService)
                .upload(any(), argThat(f -> f != null && "c.jpg".equals(f.getOriginalFilename())), any(), any());

        UploadResult r = service.uploadAll(null,
                List.of(file("a.jpg", "image/jpeg"), file("b.txt", "text/plain"), file("c.jpg", "image/jpeg")),
                null, 7L);

        assertEquals(1, r.uploaded().size());
        assertEquals(2, r.failed().size());
        assertEquals("b.txt", r.failed().get(0).filename());
        assertEquals("저장 중 오류가 발생했습니다.", r.failed().get(1).reason()); // 내부 예외 메시지는 노출하지 않음
    }

    @Test
    void rejectsEmptyAndTooManyFiles() {
        assertThrows(ApiException.class, () -> service.uploadAll(null, List.of(), null, 7L));
        List<MultipartFile> four = List.of(file("1.jpg", "image/jpeg"), file("2.jpg", "image/jpeg"),
                file("3.jpg", "image/jpeg"), file("4.jpg", "image/jpeg"));
        ApiException e = assertThrows(ApiException.class, () -> service.uploadAll(null, four, null, 7L));
        assertEquals(HttpStatus.BAD_REQUEST, e.status());
    }

    @Test
    void noFolderPermissionStopsBeforeAnyFileIsProcessed() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "이 폴더에 대한 권한이 없습니다."))
                .when(access).requireFolder(any(), any(), any(Permission.class));

        ApiException e = assertThrows(ApiException.class,
                () -> service.uploadAll(null, List.of(file("a.jpg", "image/jpeg")), null, 7L));

        assertEquals(HttpStatus.FORBIDDEN, e.status());
        verify(photoService, never()).upload(any(), any(), any(), any());
    }
}
