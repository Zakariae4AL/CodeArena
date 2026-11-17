package dev.codearena.leaderboard.internal;

import dev.codearena.contests.ContestDirectory;
import dev.codearena.contests.ContestDirectory.ContestInfo;
import dev.codearena.contests.ContestDirectory.ContestProblem;
import dev.codearena.identity.UserDirectory;
import dev.codearena.leaderboard.Scoreboards;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ICPC rules: more problems solved ranks higher; ties are broken by penalty time, the sum
 * over solved problems of the minutes from the start to the first accepted submission plus
 * 20 minutes for each wrong try submitted before it. Unsolved problems never cost penalty.
 */
@Service
class ScoreboardService implements Scoreboards {

    static final int PENALTY_PER_WRONG_TRY = 20;

    private final ScoreboardAttemptRepository attempts;
    private final ContestDirectory contests;
    private final UserDirectory users;
    private final Clock clock;

    ScoreboardService(ScoreboardAttemptRepository attempts, ContestDirectory contests, UserDirectory users, Clock clock) {
        this.attempts = attempts;
        this.contests = contests;
        this.users = users;
        this.clock = clock;
    }

    /** Standing of one contestant on one problem, derived from their attempts. */
    record Result(Instant solvedAt, int wrongTries) {

        static Result of(List<ScoreboardAttempt> list) {
            Instant solvedAt = list.stream().filter(ScoreboardAttempt::isAccepted)
                    .map(ScoreboardAttempt::getSubmittedAt).min(Comparator.naturalOrder()).orElse(null);
            int wrong = (int) list.stream()
                    .filter(a -> !a.isAccepted())
                    .filter(a -> solvedAt == null || a.getSubmittedAt().isBefore(solvedAt))
                    .count();
            return new Result(solvedAt, wrong);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Scoreboard forContest(long contestId) {
        ContestInfo contest = contests.byId(contestId).orElseThrow();
        List<ContestProblem> problems = contests.problems(contestId);
        List<String> labels = problems.stream().map(ContestProblem::label).toList();

        // user -> problem -> result
        Map<Long, Map<Long, Result>> results = new HashMap<>();
        attempts.findByContestId(contestId).stream()
                .collect(Collectors.groupingBy(ScoreboardAttempt::getUserId,
                        Collectors.groupingBy(ScoreboardAttempt::getProblemId)))
                .forEach((userId, byProblem) -> {
                    Map<Long, Result> row = new HashMap<>();
                    byProblem.forEach((problemId, list) -> row.put(problemId, Result.of(list)));
                    results.put(userId, row);
                });

        Map<Long, Instant> firstSolve = new HashMap<>();
        results.values().forEach(row -> row.forEach((problemId, r) -> {
            if (r.solvedAt() != null) {
                firstSolve.merge(problemId, r.solvedAt(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }));
        Map<Long, String> names = users.usernames(results.keySet());

        List<Standing> standings = new ArrayList<>();
        results.forEach((userId, byProblem) -> {
            int solved = 0;
            long penalty = 0;
            Instant lastSolve = Instant.MIN;
            List<Cell> row = new ArrayList<>();
            for (ContestProblem p : problems) {
                Result r = byProblem.get(p.problemId());
                if (r == null) {
                    row.add(new Cell(p.label(), false, 0, null, false));
                    continue;
                }
                Long minute = null;
                if (r.solvedAt() != null) {
                    minute = Duration.between(contest.startsAt(), r.solvedAt()).toMinutes();
                    solved++;
                    penalty += minute + (long) PENALTY_PER_WRONG_TRY * r.wrongTries();
                    lastSolve = r.solvedAt().isAfter(lastSolve) ? r.solvedAt() : lastSolve;
                }
                row.add(new Cell(p.label(), r.solvedAt() != null, r.wrongTries(), minute,
                        r.solvedAt() != null && r.solvedAt().equals(firstSolve.get(p.problemId()))));
            }
            standings.add(new Standing(userId, names.getOrDefault(userId, "user" + userId), solved, penalty, lastSolve, row));
        });

        standings.sort(Comparator.comparingInt(Standing::solved).reversed()
                .thenComparingLong(Standing::penalty)
                .thenComparing(Standing::lastSolve)
                .thenComparing(Standing::username));

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < standings.size(); i++) {
            Standing s = standings.get(i);
            Row previous = rows.isEmpty() ? null : rows.getLast();
            boolean tie = previous != null && previous.solved() == s.solved() && previous.penalty() == s.penalty();
            rows.add(new Row(tie ? previous.rank() : i + 1, s.userId(), s.username(), s.solved(), s.penalty(), s.cells()));
        }
        return new Scoreboard(contestId, contest.slug(), labels, rows, clock.instant());
    }

    private record Standing(long userId, String username, int solved, long penalty, Instant lastSolve, List<Cell> cells) {
    }
}
