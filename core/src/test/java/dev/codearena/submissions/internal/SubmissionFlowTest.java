package dev.codearena.submissions.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codearena.ApiTestBase;
import dev.codearena.shared.CurrentUser.Role;
import dev.codearena.submissions.TestOutcome;
import dev.codearena.submissions.Verdict;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The whole submission life cycle, with the judge simulated through JudgeResultHandler. */
class SubmissionFlowTest extends ApiTestBase {

    @Autowired
    InMemoryJudgeQueue queue;

    @Autowired
    JudgeResultHandler results;

    static String submission(long problemId, String language, String mode) {
        return """
                {"problemId": %d, "language": "%s", "mode": "%s", "source": "print(sum(map(int, input().split())))"}
                """.formatted(problemId, language, mode);
    }

    String submit(String token, String body) throws Exception {
        String response = mvc.perform(post("/api/submissions", token, body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        return read(response, "$.id");
    }

    @Test
    void aSubmissionIsQueuedJudgedLiveAndStored() throws Exception {
        long problem = createProblem(adminToken(), unique("flow"), true);
        String user = tokenFor(createUser(Role.USER));
        String id = submit(user, submission(problem, "python", "SUBMIT"));

        // Sent to the judge after commit, with every test.
        await().atMost(Duration.ofSeconds(10)).until(() -> !queue.jobsFor(id).isEmpty());
        assertThat(queue.jobsFor(id)).containsExactly("python:SUBMIT:3");

        results.handle(new JudgeProtocol.Result(id, "STARTED", 3, null, null, 0, 0, 0, null, null, null));
        mvc.perform(get("/api/submissions/" + id, user)).andExpect(jsonPath("$.status").value("RUNNING"));

        var t1 = new TestOutcome(1, Verdict.ACCEPTED, 12, 3000, null, null, null, null);
        var t2 = new TestOutcome(2, Verdict.WRONG_ANSWER, 15, 3100, null, null, null, null);
        results.handle(new JudgeProtocol.Result(id, "PROGRESS", 3, t1, null, 0, 0, 0, null, null, null));
        results.handle(new JudgeProtocol.Result(id, "RESULT", 3, null, Verdict.WRONG_ANSWER, 1, 15, 3100, null, List.of(t1, t2), null));

        mvc.perform(get("/api/submissions/" + id, user))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.verdict").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.passed").value(1))
                .andExpect(jsonPath("$.tests", hasSize(2)))
                .andExpect(jsonPath("$.source").isString());

        // Redelivered results are ignored.
        results.handle(new JudgeProtocol.Result(id, "RESULT", 3, null, Verdict.ACCEPTED, 3, 1, 1, null, List.of(), null));
        mvc.perform(get("/api/submissions/" + id, user)).andExpect(jsonPath("$.verdict").value("WRONG_ANSWER"));
    }

    @Test
    void submissionsArePrivate() throws Exception {
        long problem = createProblem(adminToken(), unique("priv"), true);
        String owner = tokenFor(createUser(Role.USER));
        String id = submit(owner, submission(problem, "cpp", "SUBMIT"));

        mvc.perform(get("/api/submissions/" + id, tokenFor(createUser(Role.USER)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/submissions/" + id, adminToken())).andExpect(status().isOk());
        mvc.perform(get("/api/submissions/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/submissions", null, submission(problem, "cpp", "SUBMIT"))).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBadSubmissions() throws Exception {
        String admin = adminToken();
        long published = createProblem(admin, unique("ok"), true);
        long draft = createProblem(admin, unique("draft"), false);
        String user = tokenFor(createUser(Role.USER));

        mvc.perform(post("/api/submissions", user, submission(published, "cobol", "SUBMIT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/submissions", user, submission(999_999, "cpp", "SUBMIT"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/submissions", user, submission(draft, "cpp", "SUBMIT"))).andExpect(status().isNotFound());
        String huge = "x".repeat(70_000);
        mvc.perform(post("/api/submissions", user, """
                        {"problemId": %d, "language": "cpp", "mode": "SUBMIT", "source": "%s"}""".formatted(published, huge)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsAndSolvedProblemsOnlyCountRealSubmissions() throws Exception {
        long problem = createProblem(adminToken(), unique("solve"), true);
        String user = tokenFor(createUser(Role.USER));
        String run = submit(user, submission(problem, "python", "RUN"));
        String real = submit(user, submission(problem, "python", "SUBMIT"));

        results.handle(new JudgeProtocol.Result(run, "RESULT", 1, null, Verdict.ACCEPTED, 1, 5, 5, null, List.of(), null));
        results.handle(new JudgeProtocol.Result(real, "RESULT", 3, null, Verdict.ACCEPTED, 3, 5, 5, null, List.of(), null));

        mvc.perform(get("/api/submissions/mine", user))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(real))
                .andExpect(jsonPath("$[0].source").value(nullValue()));
        mvc.perform(get("/api/submissions/solved", user)).andExpect(jsonPath("$", hasItem((int) problem)));
    }

    @Test
    void listsTheLanguages() throws Exception {
        mvc.perform(get("/api/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("javascript")));
    }
}
