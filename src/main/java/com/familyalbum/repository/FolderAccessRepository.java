package com.familyalbum.repository;

import com.familyalbum.domain.FolderAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderAccessRepository extends JpaRepository<FolderAccess, Long> {
    List<FolderAccess> findByUserId(Long userId);
    List<FolderAccess> findByFolderId(Long folderId);
    Optional<FolderAccess> findByFolderIdAndUserId(Long folderId, Long userId);
    void deleteByFolderId(Long folderId);
}
