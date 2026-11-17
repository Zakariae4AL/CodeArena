package dev.codearena.leaderboard.internal;

import dev.codearena.contests.ContestDirectory;
import dev.codearena.leaderboard.ScoreboardUpdated;
import dev.codearena.submissions.SubmissionJudged;
import dev.codearena.submissions.SubmissionMode;
import dev.codearena.submissions.Verdict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the scoreboard tables in sync with judged submissions. Runs asynchronously in
 * its own transaction; failures are retried from the event publication registry.
 */
@Component
class StandingsProjector {

    private final ScoreboardCellRepository cells;
    private final ContestDirectory contests;
    private final ApplicationEventPublisher events;

    StandingsProjector(ScoreboardCellRepository cells, ContestDirectory contests, ApplicationEventPublisher events) {
        this.cells = cells;
        this.contests = contests;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(SubmissionJudged judged) {
        if (judged.contestId() == null || judged.mode() != SubmissionMode.SUBMIT || !judged.verdict().countsAsAttempt()) {
            return;
        }
        boolean inWindow = contests.byId(judged.contestId())
                .map(c -> !judged.submittedAt().isBefore(c.startsAt()) && judged.submittedAt().isBefore(c.endsAt()))
                .orElse(false);
        if (!inWindow) {
            return;
        }
        ScoreboardCell cell = cells.findForUpdate(judged.contestId(), judged.userId(), judged.problemId())
                .orElseGet(() -> new ScoreboardCell(judged.contestId(), judged.userId(), judged.problemId()));
        if (cell.record(judged.verdict() == Verdict.ACCEPTED, judged.submittedAt())) {
            cells.save(cell);
            events.publishEvent(new ScoreboardUpdated(judged.contestId()));
        }
    }
}
