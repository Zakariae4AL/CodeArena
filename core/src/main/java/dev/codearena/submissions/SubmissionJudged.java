package dev.codearena.submissions;

import java.time.Instant;
import java.util.UUID;

/** Published once, when a submission gets its final verdict. */
public record SubmissionJudged(
        UUID submissionId,
        long userId,
        long problemId,
        Long contestId,
        SubmissionMode mode,
        Verdict verdict,
        Instant submittedAt,
        Instant judgedAt) {
}
