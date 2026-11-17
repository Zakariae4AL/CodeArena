package dev.codearena.leaderboard.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Standing of one contestant on one problem of one contest. */
@Entity
@Table(name = "scoreboard_cells")
@IdClass(ScoreboardCell.Key.class)
public class ScoreboardCell {

    /** Composite primary key (a plain class, as JPA expects for an @IdClass). */
    public static class Key implements Serializable {

        private long contestId;
        private long userId;
        private long problemId;

        public Key() {
        }

        public Key(long contestId, long userId, long problemId) {
            this.contestId = contestId;
            this.userId = userId;
            this.problemId = problemId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.contestId == contestId && k.userId == userId && k.problemId == problemId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(contestId, userId, problemId);
        }
    }

    @Id
    @Column(name = "contest_id")
    private long contestId;

    @Id
    @Column(name = "user_id")
    private long userId;

    @Id
    @Column(name = "problem_id")
    private long problemId;

    @Column(name = "wrong_tries", nullable = false)
    private int wrongTries;

    @Column(name = "solved_at")
    private Instant solvedAt;

    protected ScoreboardCell() {
    }

    public ScoreboardCell(long contestId, long userId, long problemId) {
        this.contestId = contestId;
        this.userId = userId;
        this.problemId = problemId;
    }

    /** Applies one judged attempt. Returns false when nothing changed (already solved). */
    public boolean record(boolean accepted, Instant submittedAt) {
        if (solvedAt != null) {
            return false;
        }
        if (accepted) {
            solvedAt = submittedAt;
        } else {
            wrongTries++;
        }
        return true;
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

    public int getWrongTries() {
        return wrongTries;
    }

    public Instant getSolvedAt() {
        return solvedAt;
    }
}
