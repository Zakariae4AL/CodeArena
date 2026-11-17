package dev.codearena.problems;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Public API of the problems module. */
public interface ProblemCatalog {

    record ProblemSummary(long id, String slug, String title, Difficulty difficulty, boolean published) {
    }

    record Sample(String input, String expected) {
    }

    record ProblemStatement(long id, String slug, String title, Difficulty difficulty, String statement,
                            int timeLimitMs, int memoryLimitMb, List<Sample> samples) {
    }

    record TestData(int index, String input, String expected, boolean sample) {
    }

    /** Everything the judge needs, including hidden tests. Only for the submissions module. */
    record JudgeSpec(long problemId, int timeLimitMs, int memoryLimitMb, List<TestData> tests) {
    }

    Optional<ProblemSummary> summary(long problemId);

    Optional<ProblemSummary> summaryBySlug(String slug);

    Map<Long, ProblemSummary> summaries(Collection<Long> problemIds);

    Optional<ProblemStatement> statement(long problemId);

    Optional<JudgeSpec> judgeSpec(long problemId);
}
