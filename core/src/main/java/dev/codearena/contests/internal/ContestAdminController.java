package dev.codearena.contests.internal;

import dev.codearena.problems.ProblemCatalog;
import dev.codearena.shared.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
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

@RestController
@RequestMapping("/api/admin/contests")
class ContestAdminController {

    record ContestRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "lowercase letters, digits and dashes") String slug,
            @NotBlank @Size(max = 120) String title,
            @NotNull @Size(max = 20_000) String description,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull @Size(min = 1, max = 26) List<@NotNull Long> problemIds) {
    }

    record AdminContest(long id, String slug, String title, String description, Instant startsAt, Instant endsAt,
                        List<Long> problemIds) {
        static AdminContest of(Contest c) {
            return new AdminContest(c.getId(), c.getSlug(), c.getTitle(), c.getDescription(), c.getStartsAt(),
                    c.getEndsAt(), c.getEntries().stream().map(ContestEntry::getProblemId).toList());
        }
    }

    private final ContestRepository contests;
    private final ProblemCatalog problems;
    private final Clock clock;

    ContestAdminController(ContestRepository contests, ProblemCatalog problems, Clock clock) {
        this.contests = contests;
        this.problems = problems;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<AdminContest> list() {
        return contests.findAllByOrderByStartsAtDesc().stream().map(AdminContest::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    AdminContest get(@PathVariable long id) {
        return AdminContest.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    AdminContest create(@Valid @RequestBody ContestRequest request) {
        if (contests.existsBySlug(request.slug())) {
            throw ApiException.conflict("A contest with this slug already exists.");
        }
        Contest contest = new Contest(clock.instant());
        apply(contest, request);
        return AdminContest.of(contests.save(contest));
    }

    @PutMapping("/{id}")
    @Transactional
    AdminContest update(@PathVariable long id, @Valid @RequestBody ContestRequest request) {
        Contest contest = find(id);
        contests.findBySlug(request.slug())
                .filter(other -> !other.getId().equals(contest.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict("A contest with this slug already exists.");
                });
        apply(contest, request);
        return AdminContest.of(contest);
    }

    private void apply(Contest contest, ContestRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("The contest must end after it starts.");
        }
        if (Duration.between(request.startsAt(), request.endsAt()).compareTo(Duration.ofDays(30)) > 0) {
            throw ApiException.badRequest("A contest can last at most 30 days.");
        }
        if (new HashSet<>(request.problemIds()).size() != request.problemIds().size()) {
            throw ApiException.badRequest("A problem can only appear once in a contest.");
        }
        if (problems.summaries(request.problemIds()).size() != request.problemIds().size()) {
            throw ApiException.badRequest("Some problems do not exist.");
        }
        contest.update(request.slug(), request.title().trim(), request.description(), request.startsAt(),
                request.endsAt(), request.problemIds());
    }

    private Contest find(long id) {
        return contests.findById(id).orElseThrow(() -> ApiException.notFound("Contest not found."));
    }
}
