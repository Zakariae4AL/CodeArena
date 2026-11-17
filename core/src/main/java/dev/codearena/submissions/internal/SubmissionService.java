package dev.codearena.submissions.internal;

import dev.codearena.contests.ContestDirectory;
import dev.codearena.contests.ContestDirectory.ContestInfo;
import dev.codearena.problems.ProblemCatalog;
import dev.codearena.problems.ProblemCatalog.ProblemSummary;
import dev.codearena.shared.ApiException;
import dev.codearena.shared.CurrentUser;
import dev.codearena.shared.Language;
import dev.codearena.submissions.SubmissionMode;
import dev.codearena.submissions.TestOutcome;
import dev.codearena.submissions.Verdict;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
class SubmissionService {

    static final int MAX_SOURCE_BYTES = 64 * 1024;

    record SubmitRequest(
            @NotNull Long problemId,
            @Size(max = 60) String contestSlug,
            @NotBlank String language,
            @NotBlank @Size(max = MAX_SOURCE_BYTES) String source,
            @NotNull SubmissionMode mode) {
    }

    /** Full view for the owner (includes the source code). */
    record SubmissionView(UUID id, long problemId, String problemSlug, String problemTitle, Long contestId,
                          String language, SubmissionMode mode, String status, Verdict verdict, int passed,
                          int total, long timeMs, long memoryKb, String compileOutput, String message,
                          List<TestOutcome> tests, String source, Instant createdAt, Instant judgedAt) {
    }

    private final SubmissionRepository submissions;
    private final ProblemCatalog problems;
    private final ContestDirectory contests;
    private final SubmissionRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final Clock clock;

    SubmissionService(SubmissionRepository submissions, ProblemCatalog problems, ContestDirectory contests,
                      SubmissionRateLimiter rateLimiter, ApplicationEventPublisher events, ObjectMapper mapper,
                      Clock clock) {
        this.submissions = submissions;
        this.problems = problems;
        this.contests = contests;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    SubmissionView submit(CurrentUser user, SubmitRequest request) {
        Language language = Language.fromId(request.language())
                .orElseThrow(() -> ApiException.badRequest("Unsupported language."));
        if (request.source().getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
            throw ApiException.badRequest("Source code is limited to 64 KB.");
        }
        ProblemSummary problem = problems.summary(request.problemId())
                .orElseThrow(() -> ApiException.notFound("Problem not found."));
        Instant now = clock.instant();
        Long contestId = contestFor(user, request, problem, now);
        rateLimiter.check(user.id());

        Submission submission = submissions.save(new Submission(user.id(), problem.id(), contestId, language,
                request.mode(), request.source(), now));
        events.publishEvent(new SubmissionRequested(submission.getId()));
        return view(submission, Map.of(problem.id(), problem));
    }

    /**
     * The contest a submission counts for, or null for practice. Contest problems may be
     * unpublished: they are only reachable through a contest that has started.
     */
    private Long contestFor(CurrentUser user, SubmitRequest request, ProblemSummary problem, Instant now) {
        if (request.contestSlug() == null || request.contestSlug().isBlank()) {
            if (!problem.published() && !user.isAdmin()) {
                throw ApiException.notFound("Problem not found.");
            }
            return null;
        }
        ContestInfo contest = contests.bySlug(request.contestSlug())
                .orElseThrow(() -> ApiException.notFound("Contest not found."));
        boolean inContest = contests.problems(contest.id()).stream().anyMatch(p -> p.problemId() == problem.id());
        if (!inContest) {
            throw ApiException.notFound("Problem not found.");
        }
        if (!contest.hasStarted(now) && !user.isAdmin()) {
            throw ApiException.forbidden("This contest has not started yet.");
        }
        // Runs and late submissions never count for the scoreboard.
        return request.mode() == SubmissionMode.SUBMIT && contest.isRunning(now) ? contest.id() : null;
    }

    @Transactional(readOnly = true)
    SubmissionView get(CurrentUser user, UUID id) {
        Submission submission = submissions.findById(id)
                .filter(s -> s.getUserId() == user.id() || user.isAdmin())
                .orElseThrow(() -> ApiException.notFound("Submission not found."));
        return view(submission, problems.summaries(List.of(submission.getProblemId())));
    }

    @Transactional(readOnly = true)
    List<SubmissionView> mine(CurrentUser user, Long problemId, int limit) {
        var page = PageRequest.of(0, Math.clamp(limit, 1, 100));
        List<Submission> list = problemId == null
                ? submissions.findByUserIdAndModeOrderByCreatedAtDesc(user.id(), SubmissionMode.SUBMIT, page)
                : submissions.findByUserIdAndProblemIdAndModeOrderByCreatedAtDesc(user.id(), problemId, SubmissionMode.SUBMIT, page);
        Map<Long, ProblemSummary> summaries = problems.summaries(list.stream().map(Submission::getProblemId).distinct().toList());
        // Lists stay light: no source code and no per-test details.
        return list.stream().map(s -> withoutDetails(view(s, summaries))).toList();
    }

    @Transactional(readOnly = true)
    List<Long> solved(CurrentUser user) {
        return submissions.findProblemIdsByUserAndVerdict(user.id(), Verdict.ACCEPTED, SubmissionMode.SUBMIT);
    }

    private SubmissionView view(Submission s, Map<Long, ProblemSummary> summaries) {
        ProblemSummary p = summaries.get(s.getProblemId());
        List<TestOutcome> tests = s.getTestResults() == null ? List.of()
                : mapper.readValue(s.getTestResults(), new TypeReference<List<TestOutcome>>() { });
        return new SubmissionView(s.getId(), s.getProblemId(), p == null ? null : p.slug(),
                p == null ? "Deleted problem" : p.title(), s.getContestId(), s.getLanguage().id(), s.getMode(),
                s.getStatus().name(), s.getVerdict(), s.getPassed(), s.getTotal(), s.getTimeMs(), s.getMemoryKb(),
                s.getCompileOutput(), s.getMessage(), tests, s.getSource(), s.getCreatedAt(), s.getJudgedAt());
    }

    private static SubmissionView withoutDetails(SubmissionView v) {
        return new SubmissionView(v.id(), v.problemId(), v.problemSlug(), v.problemTitle(), v.contestId(),
                v.language(), v.mode(), v.status(), v.verdict(), v.passed(), v.total(), v.timeMs(), v.memoryKb(),
                null, v.message(), List.of(), null, v.createdAt(), v.judgedAt());
    }
}
