package com.familyalbum.worldcup;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldcupMatchRepository extends JpaRepository<WorldcupMatch, Long> {

    List<WorldcupMatch> findByPlayIdOrderByIdAsc(Long playId);

    long countByPlayId(Long playId);

    /** [entryId, 승리 수] */
    @Query("select m.winnerId, count(m) from WorldcupMatch m where m.worldcupId = :id group by m.winnerId")
    List<Object[]> winCounts(@Param("id") Long worldcupId);

    /** [entryId, 출전 수] (A 자리) */
    @Query("select m.entryAId, count(m) from WorldcupMatch m where m.worldcupId = :id group by m.entryAId")
    List<Object[]> appearancesAsA(@Param("id") Long worldcupId);

    /** [entryId, 출전 수] (B 자리) */
    @Query("select m.entryBId, count(m) from WorldcupMatch m where m.worldcupId = :id group by m.entryBId")
    List<Object[]> appearancesAsB(@Param("id") Long worldcupId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorldcupMatch m where m.worldcupId = :id")
    void deleteAllByWorldcup(@Param("id") Long worldcupId);
}
