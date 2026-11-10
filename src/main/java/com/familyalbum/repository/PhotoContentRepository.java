package com.familyalbum.repository;

import com.familyalbum.domain.PhotoContent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoContentRepository extends JpaRepository<PhotoContent, String> {

    Optional<PhotoContent> findByFamilyIdAndStoredFilename(Long familyId, String storedFilename);

    void deleteByFamilyIdAndStoredFilename(Long familyId, String storedFilename);
}
