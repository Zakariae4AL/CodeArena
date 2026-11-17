package dev.codearena.submissions.internal;

import java.time.Duration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries event listeners that failed (for example Redis was down when a job was sent). */
@Component
class OutboxRetry {

    private final IncompleteEventPublications incomplete;

    OutboxRetry(IncompleteEventPublications incomplete) {
        this.incomplete = incomplete;
    }

    @Scheduled(fixedDelayString = "${codearena.outbox.retry-every:PT30S}", initialDelayString = "PT30S")
    void retry() {
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofSeconds(30));
    }
}
