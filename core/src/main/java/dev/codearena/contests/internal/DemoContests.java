package dev.codearena.contests.internal;

import dev.codearena.problems.ProblemCatalog;
import dev.codearena.problems.ProblemCatalog.ProblemSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A running, an upcoming and a finished contest, relative to the start-up time. */
@Component
@Order(3)
@ConditionalOnProperty(name = "codearena.seed.demo", havingValue = "true")
class DemoContests implements ApplicationRunner {

    private final ContestRepository contests;
    private final ProblemCatalog problems;
    private final Clock clock;

    DemoContests(ContestRepository contests, ProblemCatalog problems, Clock clock) {
        this.contests = contests;
        this.problems = problems;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (contests.count() > 0) {
            return;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        create("weekly-1", "CodeArena Weekly #1", "Four problems, three hours. ICPC rules: 20 minutes of penalty per wrong try.",
                now.minus(Duration.ofMinutes(30)), now.plus(Duration.ofHours(2).plusMinutes(30)),
                "a-plus-b", "balanced-brackets", "count-primes", "longest-increasing-subsequence");
        create("spring-sprint", "Spring Sprint", "A short round to warm up before the weekly contest.",
                now.plus(Duration.ofDays(2)), now.plus(Duration.ofDays(2).plusHours(2)),
                "reverse-words", "pairs-with-sum");
        create("warm-up", "Warm-up Round", "Our first round. The problems are open for practice.",
                now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(3)).plus(Duration.ofHours(2)),
                "a-plus-b", "reverse-words");
    }

    private void create(String slug, String title, String description, Instant start, Instant end, String... problemSlugs) {
        List<Long> ids = Stream.of(problemSlugs)
                .map(problems::summaryBySlug)
                .flatMap(java.util.Optional::stream)
                .map(ProblemSummary::id)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Contest contest = new Contest(clock.instant());
        contest.update(slug, title, description, start, end, ids);
        contests.save(contest);
    }
}
