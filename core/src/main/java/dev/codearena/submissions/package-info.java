/**
 * Submissions and the bridge to the judge workers (Redis Streams). Publishes
 * {@link dev.codearena.submissions.SubmissionUpdate} for live progress and
 * {@link dev.codearena.submissions.SubmissionJudged} once a verdict is final.
 */
@ApplicationModule(displayName = "Submissions")
package dev.codearena.submissions;

import org.springframework.modulith.ApplicationModule;
