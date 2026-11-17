package dev.codearena.leaderboard.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreboardAttemptRepository extends JpaRepository<ScoreboardAttempt, UUID> {

    List<ScoreboardAttempt> findByContestId(long contestId);
}
