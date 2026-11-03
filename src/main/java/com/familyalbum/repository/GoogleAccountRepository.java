package com.familyalbum.repository;

import com.familyalbum.domain.GoogleAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleAccountRepository extends JpaRepository<GoogleAccount, Long> {
    Optional<GoogleAccount> findByUserId(Long userId);
}
