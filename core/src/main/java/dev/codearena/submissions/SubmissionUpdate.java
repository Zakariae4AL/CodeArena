package dev.codearena.submissions;

import java.util.List;
import java.util.UUID;

/**
 * Live state of a submission, published on every change (queued, started, each test, final).
 * Contains no source code, so it can be pushed to the browser as is.
 */
public record SubmissionUpdate(
        UUID submissionId,
        SubmissionStatus status,
        Verdict verdict,
        int passed,
        int total,
        long timeMs,
        long memoryKb,
        TestOutcome test,
        List<TestOutcome> tests,
        String compileOutput,
        String message) {
}
