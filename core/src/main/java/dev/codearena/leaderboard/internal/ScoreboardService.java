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
 * over solved problems of the minutes from the start to the accepted submission plus
 * 20 minutes for each earlier wrong try. Unsolved problems never cost penalty.
 */
@Service
class ScoreboardService implements Scoreboards {

    static final int PENALTY_PER_WRONG_TRY = 20;

    private final ScoreboardCellRepository cells;
    private final ContestDirectory contests;
    private final UserDirectory users;
    private final Clock clock;

    ScoreboardService(ScoreboardCellRepository cells, ContestDirectory contests, UserDirectory users, Clock clock) {
        this.cells = cells;
        this.contests = contests;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Scoreboard forContest(long contestId) {
        ContestInfo contest = contests.byId(contestId).orElseThrow();
        List<ContestProblem> problems = contests.problems(contestId);
        List<String> labels = problems.stream().map(ContestProblem::label).toList();
        List<ScoreboardCell> all = cells.findByContestId(contestId);

        Map<Long, Instant> firstSolve = new HashMap<>();
        for (ScoreboardCell c : all) {
            if (c.getSolvedAt() != null) {
                firstSolve.merge(c.getProblemId(), c.getSolvedAt(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }

        Map<Long, List<ScoreboardCell>> byUser = all.stream().collect(Collectors.groupingBy(ScoreboardCell::getUserId));
        Map<Long, String> names = users.usernames(byUser.keySet());

        List<Standing> standings = new ArrayList<>();
        byUser.forEach((userId, userCells) -> {
            Map<Long, ScoreboardCell> byProblem = userCells.stream()
                    .collect(Collectors.toMap(ScoreboardCell::getProblemId, c -> c));
            int solved = 0;
            long penalty = 0;
            Instant lastSolve = Instant.MIN;
            List<Cell> row = new ArrayList<>();
            for (ContestProblem p : problems) {
                ScoreboardCell c = byProblem.get(p.problemId());
                if (c == null) {
                    row.add(new Cell(p.label(), false, 0, null, false));
                    continue;
                }
                Long minute = null;
                if (c.getSolvedAt() != null) {
                    minute = Duration.between(contest.startsAt(), c.getSolvedAt()).toMinutes();
                    solved++;
                    penalty += minute + (long) PENALTY_PER_WRONG_TRY * c.getWrongTries();
                    lastSolve = c.getSolvedAt().isAfter(lastSolve) ? c.getSolvedAt() : lastSolve;
                }
                row.add(new Cell(p.label(), c.getSolvedAt() != null, c.getWrongTries(), minute,
                        c.getSolvedAt() != null && c.getSolvedAt().equals(firstSolve.get(p.problemId()))));
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
            int rank = tie ? previous.rank() : i + 1;
            rows.add(new Row(rank, s.userId(), s.username(), s.solved(), s.penalty(), s.cells()));
        }
        return new Scoreboard(contestId, contest.slug(), labels, rows, clock.instant());
    }

    private record Standing(long userId, String username, int solved, long penalty, Instant lastSolve, List<Cell> cells) {
    }
}
