package dev.codearena.submissions.internal;

import java.util.UUID;

/** Internal event: a submission was saved and must be sent to the judge after commit. */
public record SubmissionRequested(UUID submissionId) {
}
