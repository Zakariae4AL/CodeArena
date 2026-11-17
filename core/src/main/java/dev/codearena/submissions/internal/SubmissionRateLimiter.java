package dev.codearena.submissions.internal;

import dev.codearena.shared.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Protects the judges from floods: N submissions per minute and per user. */
@Component
class SubmissionRateLimiter {

    private final Map<Long, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final JudgeProperties props;
    private final Clock clock;

    SubmissionRateLimiter(JudgeProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    void check(long userId) {
        Instant now = clock.instant();
        Deque<Instant> times = hits.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                times.pollFirst();
            }
            if (times.size() >= props.submissionsPerMinute()) {
                throw ApiException.tooManyRequests("Slow down: you can submit "
                        + props.submissionsPerMinute() + " times per minute.");
            }
            times.addLast(now);
        }
    }
}
