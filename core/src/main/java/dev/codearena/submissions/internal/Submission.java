package dev.codearena.submissions.internal;

import dev.codearena.shared.Language;
import dev.codearena.submissions.SubmissionMode;
import dev.codearena.submissions.SubmissionStatus;
import dev.codearena.submissions.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submissions")
public class Submission {

    /** Random UUID: also used as the WebSocket topic, so it must not be guessable. */
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "problem_id", nullable = false)
    private long problemId;

    @Column(name = "contest_id")
    private Long contestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubmissionMode mode;

    @Column(nullable = false, length = 70_000)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubmissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Verdict verdict;

    @Column(nullable = false)
    private int passed;

    @Column(nullable = false)
    private int total;

    @Column(name = "time_ms", nullable = false)
    private long timeMs;

    @Column(name = "memory_kb", nullable = false)
    private long memoryKb;

    @Column(name = "compile_output", length = 20_000)
    private String compileOutput;

    @Column(length = 500)
    private String message;

    /** Per-test results as JSON. */
    @Column(name = "test_results", length = 200_000)
    private String testResults;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "judged_at")
    private Instant judgedAt;

    protected Submission() {
    }

    public Submission(long userId, long problemId, Long contestId, Language language, SubmissionMode mode,
                      String source, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.problemId = problemId;
        this.contestId = contestId;
        this.language = language;
        this.mode = mode;
        this.source = source;
        this.status = SubmissionStatus.QUEUED;
        this.createdAt = createdAt;
    }

    public void started(int total) {
        if (status == SubmissionStatus.QUEUED) {
            this.status = SubmissionStatus.RUNNING;
            this.total = total;
        }
    }

    public void finished(Verdict verdict, int passed, int total, long timeMs, long memoryKb,
                         String compileOutput, String message, String testResults, Instant judgedAt) {
        this.status = SubmissionStatus.DONE;
        this.verdict = verdict;
        this.passed = passed;
        this.total = total;
        this.timeMs = timeMs;
        this.memoryKb = memoryKb;
        this.compileOutput = compileOutput;
        this.message = message;
        this.testResults = testResults;
        this.judgedAt = judgedAt;
    }

    public UUID getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getProblemId() {
        return problemId;
    }

    public Long getContestId() {
        return contestId;
    }

    public Language getLanguage() {
        return language;
    }

    public SubmissionMode getMode() {
        return mode;
    }

    public String getSource() {
        return source;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public int getPassed() {
        return passed;
    }

    public int getTotal() {
        return total;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public long getMemoryKb() {
        return memoryKb;
    }

    public String getCompileOutput() {
        return compileOutput;
    }

    public String getMessage() {
        return message;
    }

    public String getTestResults() {
        return testResults;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getJudgedAt() {
        return judgedAt;
    }
}
