package com.familyalbum.google;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.Permission;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.service.DuplicateSourceException;
import com.familyalbum.service.FolderAccessService;
import com.familyalbum.service.PhotoService;
import com.familyalbum.service.PhotoService.IncomingFile;
import com.familyalbum.web.ApiException;
import com.familyalbum.web.Dtos.GoogleImportResult;
import com.familyalbum.web.Dtos.GoogleSession;
import com.familyalbum.web.Dtos.UploadFailure;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 구글 포토(Picker API)에서 사용자가 고른 사진을 가족 폴더로 가져온다.
 * 이 클래스는 트랜잭션을 열지 않으며, 사진마다 PhotoService#store(@Transactional)가 독립 커밋한다.
 */
@Service
public class GoogleImportService {

    private static final Logger log = LoggerFactory.getLogger(GoogleImportService.class);

    private final GoogleConnectionService connections;
    private final GooglePickerClient picker;
    private final PhotoService photoService;
    private final FolderAccessService access;
    private final AppProperties props;

    public GoogleImportService(GoogleConnectionService connections, GooglePickerClient picker,
                               PhotoService photoService, FolderAccessService access, AppProperties props) {
        this.connections = connections;
        this.picker = picker;
        this.photoService = photoService;
        this.access = access;
        this.props = props;
    }

    public GoogleSession start(AppPrincipal me, Long folderId) {
        access.requireFolder(me, folderId, Permission.EDIT);
        String token = connections.accessToken(me.userId());
        GooglePickerClient.Session s = guard(() -> picker.createSession(token, props.google().maxPickItems()));
        return new GoogleSession(s.id(), s.pickerUri(), s.pollIntervalMs(), s.timeoutMs());
    }

    public boolean isReady(AppPrincipal me, String sessionId) {
        String token = connections.accessToken(me.userId());
        return guard(() -> picker.getSession(token, sessionId)).mediaItemsSet();
    }

    public GoogleImportResult importPicked(AppPrincipal me, String sessionId, Long folderId) {
        access.requireFolder(me, folderId, Permission.EDIT);
        String token = connections.accessToken(me.userId());

        if (!guard(() -> picker.getSession(token, sessionId)).mediaItemsSet()) {
            throw new ApiException(HttpStatus.CONFLICT, "아직 사진 선택이 끝나지 않았어요.");
        }
        List<GooglePickerClient.PickedItem> items = guard(() -> picker.listMediaItems(token, sessionId));

        int imported = 0;
        int skipped = 0;
        List<UploadFailure> failed = new ArrayList<>();
        try {
            for (GooglePickerClient.PickedItem item : items) {
                String name = item.filename() == null ? "(이름 없음)" : item.filename();
                if (!"PHOTO".equals(item.type())) {
                    failed.add(new UploadFailure(name, "사진만 가져올 수 있어요. (동영상 제외)"));
                    continue;
                }
                try {
                    byte[] bytes = guard(() -> picker.download(token, item.baseUrl()));
                    photoService.store(me, new IncomingFile(name, item.mimeType(), bytes.length,
                            "google:" + me.userId() + ":" + item.id(), new ByteArrayResource(bytes)), null, folderId);
                    imported++;
                } catch (DuplicateSourceException e) {
                    skipped++;
                } catch (ApiException e) {
                    failed.add(new UploadFailure(name, e.getMessage()));
                } catch (RuntimeException e) {
                    log.error("구글 포토 가져오기 실패: {}", name, e);
                    failed.add(new UploadFailure(name, "가져오는 중 오류가 발생했어요."));
                }
            }
        } finally {
            picker.deleteSession(token, sessionId);
        }
        return new GoogleImportResult(imported, skipped, failed);
    }

    private static <T> T guard(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (GoogleAuthException e) {
            throw new ApiException(HttpStatus.CONFLICT, "구글 연결이 만료됐어요. 구글 포토 메뉴에서 다시 연결해 주세요.");
        }
    }
}
