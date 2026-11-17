package dev.codearena.leaderboard;

/** Published when a contest's standings changed. */
public record ScoreboardUpdated(long contestId) {
}
