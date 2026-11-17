package dev.codearena.problems.internal;

import dev.codearena.problems.ProblemCatalog;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ProblemCatalogImpl implements ProblemCatalog {

    private final ProblemRepository problems;

    ProblemCatalogImpl(ProblemRepository problems) {
        this.problems = problems;
    }

    static ProblemSummary toSummary(Problem p) {
        return new ProblemSummary(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty(), p.isPublished());
    }

    static ProblemStatement toStatement(Problem p) {
        return new ProblemStatement(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty(), p.getStatement(),
                p.getTimeLimitMs(), p.getMemoryLimitMb(),
                p.getTests().stream().filter(TestCase::isSample).map(t -> new Sample(t.getInput(), t.getExpected())).toList());
    }

    @Override
    public Optional<ProblemSummary> summary(long problemId) {
        return problems.findById(problemId).map(ProblemCatalogImpl::toSummary);
    }

    @Override
    public Optional<ProblemSummary> summaryBySlug(String slug) {
        return problems.findBySlug(slug).map(ProblemCatalogImpl::toSummary);
    }

    @Override
    public Map<Long, ProblemSummary> summaries(Collection<Long> problemIds) {
        return problems.findAllById(problemIds).stream()
                .collect(Collectors.toMap(Problem::getId, ProblemCatalogImpl::toSummary));
    }

    @Override
    public Optional<ProblemStatement> statement(long problemId) {
        return problems.findWithTestsById(problemId).map(ProblemCatalogImpl::toStatement);
    }

    @Override
    public Optional<JudgeSpec> judgeSpec(long problemId) {
        return problems.findWithTestsById(problemId).map(p -> new JudgeSpec(
                p.getId(), p.getTimeLimitMs(), p.getMemoryLimitMb(),
                p.getTests().stream()
                        .map(t -> new TestData(t.getPosition(), t.getInput(), t.getExpected(), t.isSample()))
                        .toList()));
    }
}
