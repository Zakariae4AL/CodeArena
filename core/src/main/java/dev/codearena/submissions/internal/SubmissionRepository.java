package dev.codearena.submissions.internal;

import dev.codearena.submissions.SubmissionMode;
import dev.codearena.submissions.Verdict;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByUserIdAndModeOrderByCreatedAtDesc(long userId, SubmissionMode mode, Pageable page);

    List<Submission> findByUserIdAndProblemIdAndModeOrderByCreatedAtDesc(long userId, long problemId,
                                                                        SubmissionMode mode, Pageable page);

    @Query("select distinct s.problemId from Submission s where s.userId = :userId and s.verdict = :verdict and s.mode = :mode")
    List<Long> findProblemIdsByUserAndVerdict(@Param("userId") long userId, @Param("verdict") Verdict verdict,
                                              @Param("mode") SubmissionMode mode);
}
