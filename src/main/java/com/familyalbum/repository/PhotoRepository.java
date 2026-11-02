package com.familyalbum.repository;

import com.familyalbum.domain.Photo;
import com.familyalbum.domain.Visibility;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    @Query(value = """
            select p from Photo p join fetch p.uploader
            where p.family.id = :familyId
              and (p.visibility = :shared or p.uploader.id = :userId)
            order by p.createdAt desc, p.id desc
            """,
           countQuery = """
            select count(p) from Photo p
            where p.family.id = :familyId
              and (p.visibility = :shared or p.uploader.id = :userId)
            """)
    Page<Photo> findVisible(@Param("familyId") Long familyId,
                            @Param("userId") Long userId,
                            @Param("shared") Visibility shared,
                            Pageable pageable);

    default Page<Photo> findVisibleTo(Long familyId, Long userId, Pageable pageable) {
        return findVisible(familyId, userId, Visibility.FAMILY, pageable);
    }

    @EntityGraph(attributePaths = {"uploader", "family"})
    Optional<Photo> findWithUploaderById(Long id);
}
