package dev.codearena.contests;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Public API of the contests module. */
public interface ContestDirectory {

    record ContestInfo(long id, String slug, String title, Instant startsAt, Instant endsAt) {

        public boolean hasStarted(Instant now) {
            return !now.isBefore(startsAt);
        }

        public boolean isRunning(Instant now) {
            return hasStarted(now) && now.isBefore(endsAt);
        }
    }

    record ContestProblem(long problemId, String label) {
    }

    Optional<ContestInfo> byId(long contestId);

    Optional<ContestInfo> bySlug(String slug);

    /** Problems of a contest, in order. */
    List<ContestProblem> problems(long contestId);
}
