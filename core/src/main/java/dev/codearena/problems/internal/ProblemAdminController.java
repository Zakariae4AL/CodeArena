package dev.codearena.problems.internal;

import dev.codearena.problems.Difficulty;
import dev.codearena.shared.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Problem editor for admins (the security config restricts /api/admin/** to ADMIN). */
@RestController
@RequestMapping("/api/admin/problems")
class ProblemAdminController {

    record TestInput(@NotNull @Size(max = 1_000_000) String input,
                     @NotNull @Size(max = 1_000_000) String expected,
                     boolean sample) {
    }

    record ProblemRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "lowercase letters, digits and dashes") String slug,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 100_000) String statement,
            @NotNull Difficulty difficulty,
            @NotNull @Min(100) @Max(15_000) Integer timeLimitMs,
            @NotNull @Min(16) @Max(1024) Integer memoryLimitMb,
            boolean published,
            @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull TestInput> tests) {
    }

    record AdminProblemRow(long id, String slug, String title, Difficulty difficulty, boolean published, int tests,
                           Instant createdAt) {
    }

    record AdminProblem(long id, String slug, String title, String statement, Difficulty difficulty,
                        int timeLimitMs, int memoryLimitMb, boolean published, List<TestInput> tests) {
        static AdminProblem of(Problem p) {
            return new AdminProblem(p.getId(), p.getSlug(), p.getTitle(), p.getStatement(), p.getDifficulty(),
                    p.getTimeLimitMs(), p.getMemoryLimitMb(), p.isPublished(),
                    p.getTests().stream().map(t -> new TestInput(t.getInput(), t.getExpected(), t.isSample())).toList());
        }
    }

    private final ProblemRepository problems;
    private final Clock clock;

    ProblemAdminController(ProblemRepository problems, Clock clock) {
        this.problems = problems;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<AdminProblemRow> list() {
        return problems.findAllByOrderByIdAsc().stream()
                .map(p -> new AdminProblemRow(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty(), p.isPublished(),
                        p.getTests().size(), p.getCreatedAt()))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    AdminProblem get(@PathVariable long id) {
        return AdminProblem.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    AdminProblem create(@Valid @RequestBody ProblemRequest request) {
        if (problems.existsBySlug(request.slug())) {
            throw ApiException.conflict("A problem with this slug already exists.");
        }
        Problem problem = new Problem(request.slug(), clock.instant());
        apply(problem, request);
        return AdminProblem.of(problems.save(problem));
    }

    @PutMapping("/{id}")
    @Transactional
    AdminProblem update(@PathVariable long id, @Valid @RequestBody ProblemRequest request) {
        Problem problem = find(id);
        problems.findBySlug(request.slug())
                .filter(other -> !other.getId().equals(problem.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict("A problem with this slug already exists.");
                });
        apply(problem, request);
        return AdminProblem.of(problem);
    }

    private void apply(Problem problem, ProblemRequest request) {
        if (request.tests().stream().noneMatch(TestInput::sample)) {
            throw ApiException.badRequest("Mark at least one test as a sample, so people can run their code on it.");
        }
        problem.update(request.slug(), request.title().trim(), request.statement(), request.difficulty(),
                request.timeLimitMs(), request.memoryLimitMb(), request.published());
        List<TestCase> tests = new ArrayList<>();
        for (int i = 0; i < request.tests().size(); i++) {
            TestInput t = request.tests().get(i);
            tests.add(new TestCase(i + 1, t.input(), t.expected(), t.sample()));
        }
        problem.replaceTests(tests);
    }

    private Problem find(long id) {
        return problems.findWithTestsById(id).orElseThrow(() -> ApiException.notFound("Problem not found."));
    }
}
