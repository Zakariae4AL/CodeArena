package dev.codearena.leaderboard.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreboardCellRepository extends JpaRepository<ScoreboardCell, ScoreboardCell.Key> {

    List<ScoreboardCell> findByContestId(long contestId);

    /** Locked, so two verdicts for the same cell are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ScoreboardCell c where c.contestId = :contestId and c.userId = :userId and c.problemId = :problemId")
    Optional<ScoreboardCell> findForUpdate(@Param("contestId") long contestId, @Param("userId") long userId,
                                           @Param("problemId") long problemId);
}
