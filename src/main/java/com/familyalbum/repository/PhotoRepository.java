package com.familyalbum.repository;

import com.familyalbum.domain.Photo;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    /** 접근 가능한 폴더들의 사진(최신순). 폴더 접근 권한 계산은 서비스 계층에서 수행한다. */
    @Query(value = """
            select p from Photo p join fetch p.uploader join fetch p.folder
            where p.folder.id in :folderIds
            order by p.createdAt desc, p.id desc
            """,
           countQuery = "select count(p) from Photo p where p.folder.id in :folderIds")
    Page<Photo> findInFolders(@Param("folderIds") Collection<Long> folderIds, Pageable pageable);

    @EntityGraph(attributePaths = {"uploader", "folder"})
    Optional<Photo> findWithUploaderById(Long id);

    long countByFolderId(Long folderId);

    boolean existsByFamilyIdAndSourceId(Long familyId, String sourceId);
}
