package dev.codearena.leaderboard.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One judged contest submission, as seen by the scoreboard. */
@Entity
@Table(name = "scoreboard_attempts")
public class ScoreboardAttempt {

    @Id
    @Column(name = "submission_id")
    private UUID submissionId;

    @Column(name = "contest_id", nullable = false)
    private long contestId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "problem_id", nullable = false)
    private long problemId;

    @Column(nullable = false)
    private boolean accepted;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected ScoreboardAttempt() {
    }

    public ScoreboardAttempt(UUID submissionId, long contestId, long userId, long problemId, boolean accepted,
                             Instant submittedAt) {
        this.submissionId = submissionId;
        this.contestId = contestId;
        this.userId = userId;
        this.problemId = problemId;
        this.accepted = accepted;
        this.submittedAt = submittedAt;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public long getContestId() {
        return contestId;
    }

    public long getUserId() {
        return userId;
    }

    public long getProblemId() {
        return problemId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
