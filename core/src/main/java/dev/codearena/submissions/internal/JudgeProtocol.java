package dev.codearena.submissions.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.codearena.submissions.TestOutcome;
import dev.codearena.submissions.Verdict;
import java.util.List;

/** Messages exchanged with the Go judge workers. Field names match judge/internal/protocol. */
final class JudgeProtocol {

    private JudgeProtocol() {
    }

    record TestCase(int index, String input, String expected, boolean sample) {
    }

    /** Written to the jobs stream. */
    record Job(String submissionId, String language, String mode, String source,
               int timeLimitMs, int memoryLimitMb, List<TestCase> tests) {
    }

    static final String STARTED = "STARTED";
    static final String PROGRESS = "PROGRESS";
    static final String RESULT = "RESULT";

    /** Read from the results stream. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(String submissionId, String type, int total, TestOutcome test, Verdict verdict, int passed,
                  long timeMs, long memoryKb, String compileOutput, List<TestOutcome> tests, String message) {
    }
}
