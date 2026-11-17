package dev.codearena.leaderboard;

import java.time.Instant;
import java.util.List;

/** Public API of the leaderboard module. */
public interface Scoreboards {

    /** One problem for one contestant. {@code minute} is the solve time from the contest start. */
    record Cell(String label, boolean solved, int wrongTries, Long minute, boolean firstSolve) {
    }

    record Row(int rank, long userId, String username, int solved, long penalty, List<Cell> cells) {
    }

    record Scoreboard(long contestId, String contestSlug, List<String> labels, List<Row> rows, Instant generatedAt) {
    }

    Scoreboard forContest(long contestId);
}
