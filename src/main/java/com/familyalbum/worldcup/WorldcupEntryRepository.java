package com.familyalbum.worldcup;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldcupEntryRepository extends JpaRepository<WorldcupEntry, Long> {

    @EntityGraph(attributePaths = "photo")
    List<WorldcupEntry> findByWorldcupIdOrderBySortOrderAsc(Long worldcupId);

    @EntityGraph(attributePaths = "photo")
    List<WorldcupEntry> findByIdIn(Collection<Long> ids);

    long countByWorldcupId(Long worldcupId);

    /** 월드컵 후보로 쓰이는 사진은 삭제할 수 없다(PhotoService 에서 사용). */
    boolean existsByPhotoId(Long photoId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorldcupEntry e where e.worldcupId = :worldcupId")
    void deleteAllByWorldcup(@Param("worldcupId") Long worldcupId);
}
