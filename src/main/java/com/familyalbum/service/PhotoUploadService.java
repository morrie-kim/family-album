package com.familyalbum.service;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.Visibility;
import com.familyalbum.security.AppPrincipal;
import com.familyalbum.web.ApiException;
import com.familyalbum.web.Dtos.PhotoResponse;
import com.familyalbum.web.Dtos.UploadFailure;
import com.familyalbum.web.Dtos.UploadResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 다중 업로드 오케스트레이터.
 * 이 클래스 자체는 트랜잭션을 열지 않는다. 파일마다 PhotoService#upload(@Transactional)를
 * 프록시를 통해 호출하므로 파일별로 독립 커밋되고, 한 장이 실패해도 나머지는 저장된다.
 */
@Service
public class PhotoUploadService {

    private static final Logger log = LoggerFactory.getLogger(PhotoUploadService.class);

    private final PhotoService photoService;
    private final AppProperties props;

    public PhotoUploadService(PhotoService photoService, AppProperties props) {
        this.photoService = photoService;
        this.props = props;
    }

    public UploadResult uploadAll(AppPrincipal me, List<MultipartFile> files, String description, Visibility visibility) {
        List<MultipartFile> targets = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (targets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "업로드할 사진을 선택해 주세요.");
        }
        int max = props.upload().maxFilesPerRequest();
        if (targets.size() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "한 번에 최대 " + max + "장까지 업로드할 수 있습니다.");
        }
        if (description != null && description.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "설명은 500자 이하여야 합니다.");
        }

        List<PhotoResponse> uploaded = new ArrayList<>();
        List<UploadFailure> failed = new ArrayList<>();
        for (MultipartFile file : targets) {
            String name = file.getOriginalFilename() == null ? "(이름 없음)" : file.getOriginalFilename();
            try {
                uploaded.add(photoService.upload(me, file, description, visibility));
            } catch (ApiException e) {
                failed.add(new UploadFailure(name, e.getMessage()));
            } catch (RuntimeException e) {
                log.error("사진 저장 실패: {}", name, e);
                failed.add(new UploadFailure(name, "저장 중 오류가 발생했습니다."));
            }
        }
        return new UploadResult(uploaded, failed);
    }
}
