package com.familyalbum.worldcup;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldcupRepository extends JpaRepository<Worldcup, Long> {

    @EntityGraph(attributePaths = {"family", "createdBy", "folder"})
    List<Worldcup> findByFamilyIdOrderByCreatedAtDesc(Long familyId);

    @EntityGraph(attributePaths = {"family", "createdBy", "folder"})
    Optional<Worldcup> findDetailedById(Long id);
}
