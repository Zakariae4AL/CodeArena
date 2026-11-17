package dev.codearena.realtime.internal;

import dev.codearena.leaderboard.ScoreboardUpdated;
import dev.codearena.leaderboard.Scoreboards;
import dev.codearena.submissions.SubmissionUpdate;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Forwards module events to WebSocket subscribers. */
@Component
class LiveRelay {

    private final SimpMessagingTemplate messaging;
    private final Scoreboards scoreboards;

    LiveRelay(SimpMessagingTemplate messaging, Scoreboards scoreboards) {
        this.messaging = messaging;
        this.scoreboards = scoreboards;
    }

    /** Progress is best effort and frequent: no need to store it in the publication registry. */
    @EventListener
    void on(SubmissionUpdate update) {
        messaging.convertAndSend("/topic/submissions/" + update.submissionId(), update);
    }

    @ApplicationModuleListener
    void on(ScoreboardUpdated updated) {
        messaging.convertAndSend("/topic/contests/" + updated.contestId() + "/scoreboard",
                scoreboards.forContest(updated.contestId()));
    }
}
