package com.familyalbum.repository;

import com.familyalbum.domain.Folder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByFamilyIdOrderByDefaultFolderDescNameAsc(Long familyId);

    Optional<Folder> findByFamilyIdAndDefaultFolderTrue(Long familyId);

    boolean existsByFamilyIdAndNameIgnoreCase(Long familyId, String name);

    @EntityGraph(attributePaths = {"family", "createdBy"})
    Optional<Folder> findDetailedById(Long id);
}
