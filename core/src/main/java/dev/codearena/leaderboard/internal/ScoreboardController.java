package dev.codearena.leaderboard.internal;

import dev.codearena.contests.ContestDirectory;
import dev.codearena.leaderboard.Scoreboards;
import dev.codearena.leaderboard.Scoreboards.Scoreboard;
import dev.codearena.shared.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ScoreboardController {

    private final ContestDirectory contests;
    private final Scoreboards scoreboards;

    ScoreboardController(ContestDirectory contests, Scoreboards scoreboards) {
        this.contests = contests;
        this.scoreboards = scoreboards;
    }

    @GetMapping("/api/contests/{slug}/scoreboard")
    Scoreboard scoreboard(@PathVariable String slug) {
        var contest = contests.bySlug(slug).orElseThrow(() -> ApiException.notFound("Contest not found."));
        return scoreboards.forContest(contest.id());
    }
}
