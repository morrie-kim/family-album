package com.familyalbum.worldcup;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldcupPlayRepository extends JpaRepository<WorldcupPlay, Long> {

    Optional<WorldcupPlay> findByIdAndWorldcupIdAndPlayerId(Long id, Long worldcupId, Long playerId);

    long countByWorldcupId(Long worldcupId);

    /** [championEntryId, 우승 횟수] */
    @Query("select p.championEntryId, count(p) from WorldcupPlay p "
            + "where p.worldcupId = :id and p.status = com.familyalbum.worldcup.PlayStatus.DONE "
            + "group by p.championEntryId")
    List<Object[]> championCounts(@Param("id") Long worldcupId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorldcupPlay p where p.worldcupId = :id")
    void deleteAllByWorldcup(@Param("id") Long worldcupId);
}
