package dev.codearena.contests.internal;

import dev.codearena.problems.Difficulty;
import dev.codearena.problems.ProblemCatalog;
import dev.codearena.problems.ProblemCatalog.ProblemStatement;
import dev.codearena.problems.ProblemCatalog.ProblemSummary;
import dev.codearena.shared.ApiException;
import dev.codearena.shared.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contests")
class ContestController {

    enum Phase { UPCOMING, RUNNING, ENDED }

    record ContestRow(long id, String slug, String title, Instant startsAt, Instant endsAt, Phase phase, int problems) {
    }

    record ContestProblemView(String label, long problemId, String slug, String title, Difficulty difficulty) {
    }

    record ContestView(long id, String slug, String title, String description, Instant startsAt, Instant endsAt,
                       Phase phase, Instant serverTime, List<ContestProblemView> problems) {
    }

    record ContestProblemStatement(String contestSlug, String label, ProblemStatement problem) {
    }

    private final ContestRepository contests;
    private final ProblemCatalog problems;
    private final Clock clock;

    ContestController(ContestRepository contests, ProblemCatalog problems, Clock clock) {
        this.contests = contests;
        this.problems = problems;
        this.clock = clock;
    }

    static Phase phase(Contest c, Instant now) {
        if (now.isBefore(c.getStartsAt())) {
            return Phase.UPCOMING;
        }
        return now.isBefore(c.getEndsAt()) ? Phase.RUNNING : Phase.ENDED;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<ContestRow> list() {
        Instant now = clock.instant();
        return contests.findAllByOrderByStartsAtDesc().stream()
                .map(c -> new ContestRow(c.getId(), c.getSlug(), c.getTitle(), c.getStartsAt(), c.getEndsAt(),
                        phase(c, now), c.getEntries().size()))
                .toList();
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    ContestView get(@PathVariable String slug, @AuthenticationPrincipal CurrentUser user) {
        Contest contest = find(slug);
        Instant now = clock.instant();
        Phase phase = phase(contest, now);
        List<ContestProblemView> problemViews = List.of();
        // The problem set stays secret until the contest starts (admins can always see it).
        if (phase != Phase.UPCOMING || (user != null && user.isAdmin())) {
            Map<Long, ProblemSummary> summaries = problems.summaries(
                    contest.getEntries().stream().map(ContestEntry::getProblemId).toList());
            problemViews = contest.getEntries().stream()
                    .filter(e -> summaries.containsKey(e.getProblemId()))
                    .map(e -> {
                        ProblemSummary p = summaries.get(e.getProblemId());
                        return new ContestProblemView(e.getLabel(), p.id(), p.slug(), p.title(), p.difficulty());
                    })
                    .toList();
        }
        return new ContestView(contest.getId(), contest.getSlug(), contest.getTitle(), contest.getDescription(),
                contest.getStartsAt(), contest.getEndsAt(), phase, now, problemViews);
    }

    @GetMapping("/{slug}/problems/{label}")
    @Transactional(readOnly = true)
    ContestProblemStatement problem(@PathVariable String slug, @PathVariable String label,
                                    @AuthenticationPrincipal CurrentUser user) {
        Contest contest = find(slug);
        if (phase(contest, clock.instant()) == Phase.UPCOMING && (user == null || !user.isAdmin())) {
            throw ApiException.forbidden("This contest has not started yet.");
        }
        ContestEntry entry = contest.getEntries().stream()
                .filter(e -> e.getLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Problem not found."));
        ProblemStatement statement = problems.statement(entry.getProblemId())
                .orElseThrow(() -> ApiException.notFound("Problem not found."));
        return new ContestProblemStatement(contest.getSlug(), entry.getLabel(), statement);
    }

    private Contest find(String slug) {
        return contests.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Contest not found."));
    }
}
