package dev.codearena.submissions.internal;

import dev.codearena.submissions.SubmissionJudged;
import dev.codearena.submissions.SubmissionStatus;
import dev.codearena.submissions.SubmissionUpdate;
import dev.codearena.submissions.TestOutcome;
import dev.codearena.submissions.Verdict;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies results coming back from the judge. Idempotent: a result for a submission
 * that is already DONE is ignored, so redelivered messages are harmless.
 */
@Component
public class JudgeResultHandler {

    private final SubmissionRepository submissions;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;

    JudgeResultHandler(SubmissionRepository submissions, ApplicationEventPublisher events, TransactionTemplate tx,
                       ObjectMapper mapper, Clock clock) {
        this.submissions = submissions;
        this.events = events;
        this.tx = tx;
        this.mapper = mapper;
        this.clock = clock;
    }

    void handle(JudgeProtocol.Result result) {
        UUID id;
        try {
            id = UUID.fromString(result.submissionId());
        } catch (IllegalArgumentException | NullPointerException e) {
            return;
        }
        Optional<SubmissionUpdate> update = switch (result.type() == null ? "" : result.type()) {
            case JudgeProtocol.STARTED -> tx.execute(status -> started(id, result.total()));
            case JudgeProtocol.PROGRESS -> progress(id, result);
            case JudgeProtocol.RESULT -> tx.execute(status -> finished(id, result));
            default -> Optional.empty();
        };
        // Live updates go out after the transaction, so the browser never sees a state that was rolled back.
        update.ifPresent(events::publishEvent);
    }

    private Optional<SubmissionUpdate> started(UUID id, int total) {
        return submissions.findById(id)
                .filter(s -> s.getStatus() != SubmissionStatus.DONE)
                .map(s -> {
                    s.started(total);
                    return new SubmissionUpdate(id, SubmissionStatus.RUNNING, null, 0, total, 0, 0, null, null, null, null);
                });
    }

    private Optional<SubmissionUpdate> progress(UUID id, JudgeProtocol.Result result) {
        if (result.test() == null) {
            return Optional.empty();
        }
        return Optional.of(new SubmissionUpdate(id, SubmissionStatus.RUNNING, null, 0, result.total(), 0, 0,
                result.test(), null, null, null));
    }

    private Optional<SubmissionUpdate> finished(UUID id, JudgeProtocol.Result r) {
        return submissions.findById(id)
                .filter(s -> s.getStatus() != SubmissionStatus.DONE)
                .map(s -> {
                    Verdict verdict = r.verdict() == null ? Verdict.SYSTEM_ERROR : r.verdict();
                    List<TestOutcome> tests = r.tests() == null ? List.of() : r.tests();
                    s.finished(verdict, r.passed(), r.total(), r.timeMs(), r.memoryKb(),
                            truncate(r.compileOutput(), 20_000), truncate(r.message(), 500),
                            truncate(mapper.writeValueAsString(tests), 200_000), clock.instant());
                    // Inside the transaction: stored in the event publication registry with the update.
                    events.publishEvent(new SubmissionJudged(s.getId(), s.getUserId(), s.getProblemId(),
                            s.getContestId(), s.getMode(), verdict, s.getCreatedAt(), s.getJudgedAt()));
                    return new SubmissionUpdate(id, SubmissionStatus.DONE, verdict, r.passed(), r.total(),
                            r.timeMs(), r.memoryKb(), null, tests, s.getCompileOutput(), s.getMessage());
                });
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
