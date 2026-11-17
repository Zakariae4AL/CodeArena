package dev.codearena.submissions.internal;

import dev.codearena.problems.ProblemCatalog;
import dev.codearena.problems.ProblemCatalog.JudgeSpec;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends a saved submission to the judge. Runs after the transaction commits; the event is
 * stored in the event publication registry first, so if Redis is down the job is not lost:
 * the publication stays incomplete and is resubmitted (transactional outbox).
 */
@Component
class JudgeDispatcher {

    private final SubmissionRepository submissions;
    private final ProblemCatalog problems;
    private final JudgeQueue queue;

    JudgeDispatcher(SubmissionRepository submissions, ProblemCatalog problems, JudgeQueue queue) {
        this.submissions = submissions;
        this.problems = problems;
        this.queue = queue;
    }

    @ApplicationModuleListener
    void on(SubmissionRequested event) {
        Submission submission = submissions.findById(event.submissionId()).orElse(null);
        if (submission == null) {
            return;
        }
        JudgeSpec spec = problems.judgeSpec(submission.getProblemId())
                .orElseThrow(() -> new IllegalStateException("Problem " + submission.getProblemId() + " is gone"));
        queue.send(new JudgeProtocol.Job(
                submission.getId().toString(),
                submission.getLanguage().id(),
                submission.getMode().name(),
                submission.getSource(),
                spec.timeLimitMs(),
                spec.memoryLimitMb(),
                spec.tests().stream()
                        .map(t -> new JudgeProtocol.TestCase(t.index(), t.input(), t.expected(), t.sample()))
                        .toList()));
    }
}
