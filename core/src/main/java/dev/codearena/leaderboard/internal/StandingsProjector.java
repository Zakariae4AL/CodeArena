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
 * Records every judged contest submission. Listeners run asynchronously, so verdicts can
 * be applied in any order: the scoreboard is computed from submission times, never from
 * the order in which events arrive. Failures are retried from the event publication registry.
 */
@Component
class StandingsProjector {

    private final ScoreboardAttemptRepository attempts;
    private final ContestDirectory contests;
    private final ApplicationEventPublisher events;

    StandingsProjector(ScoreboardAttemptRepository attempts, ContestDirectory contests, ApplicationEventPublisher events) {
        this.attempts = attempts;
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
        if (!inWindow || attempts.existsById(judged.submissionId())) {
            return;
        }
        attempts.save(new ScoreboardAttempt(judged.submissionId(), judged.contestId(), judged.userId(),
                judged.problemId(), judged.verdict() == Verdict.ACCEPTED, judged.submittedAt()));
        events.publishEvent(new ScoreboardUpdated(judged.contestId()));
    }
}
