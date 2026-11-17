package dev.codearena.submissions.internal;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codearena.ApiTestBase;
import dev.codearena.identity.internal.User;
import dev.codearena.shared.CurrentUser.Role;
import dev.codearena.submissions.Verdict;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** ICPC scoring, fed by real submissions and simulated verdicts. */
class ScoreboardTest extends ApiTestBase {

    @Autowired
    JudgeResultHandler results;

    String submitAndJudge(String token, long problem, String contest, String mode, Verdict verdict) throws Exception {
        String response = mvc.perform(post("/api/submissions", token, """
                        {"problemId": %d, "contestSlug": "%s", "language": "cpp", "mode": "%s", "source": "int main(){}"}
                        """.formatted(problem, contest, mode)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = read(response, "$.id");
        results.handle(new JudgeProtocol.Result(id, "RESULT", 3, null, verdict, 0, 1, 1, null, List.of(), null));
        return id;
    }

    @Test
    void ranksBySolvedThenPenalty() throws Exception {
        String admin = adminToken();
        long a = createProblem(admin, unique("sa"), true);
        long b = createProblem(admin, unique("sb"), true);
        String contest = unique("icpc");
        createContest(admin, contest, Instant.now().minus(Duration.ofMinutes(10)), Instant.now().plus(Duration.ofHours(1)), a, b);

        User alice = createUser(Role.USER);
        User bob = createUser(Role.USER);
        User chen = createUser(Role.USER);
        String alt = tokenFor(alice);
        String bt = tokenFor(bob);
        String ct = tokenFor(chen);

        // Alice: A wrong, A accepted (one wrong try = +20 min), B accepted.
        submitAndJudge(alt, a, contest, "SUBMIT", Verdict.WRONG_ANSWER);
        submitAndJudge(alt, a, contest, "SUBMIT", Verdict.ACCEPTED);
        submitAndJudge(alt, b, contest, "SUBMIT", Verdict.ACCEPTED);
        // Bob: A accepted first try; a compilation error never costs penalty.
        submitAndJudge(bt, a, contest, "SUBMIT", Verdict.COMPILATION_ERROR);
        submitAndJudge(bt, a, contest, "SUBMIT", Verdict.ACCEPTED);
        // Chen: only "runs", which never count.
        submitAndJudge(ct, a, contest, "RUN", Verdict.ACCEPTED);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/contests/" + contest + "/scoreboard"))
                        .andExpect(jsonPath("$.rows", hasSize(2)))
                        .andExpect(jsonPath("$.rows[0].username").value(alice.getUsername()))
                        .andExpect(jsonPath("$.rows[0].solved").value(2))
                        .andExpect(jsonPath("$.rows[0].cells[0].wrongTries").value(1))
                        .andExpect(jsonPath("$.rows[1].username").value(bob.getUsername()))
                        .andExpect(jsonPath("$.rows[1].solved").value(1))
                        .andExpect(jsonPath("$.rows[1].cells[0].wrongTries").value(0))
                        .andExpect(jsonPath("$.labels[0]").value("A")));

        // Solving again after an accept changes nothing.
        submitAndJudge(alt, a, contest, "SUBMIT", Verdict.WRONG_ANSWER);
        Thread.sleep(300);
        mvc.perform(get("/api/contests/" + contest + "/scoreboard"))
                .andExpect(jsonPath("$.rows[0].cells[0].wrongTries").value(1));
    }

    @Test
    void submissionsBeforeTheStartAreRefusedAndAfterTheEndAreJustPractice() throws Exception {
        String admin = adminToken();
        long p = createProblem(admin, unique("late"), true);
        String user = tokenFor(createUser(Role.USER));

        String future = unique("future");
        createContest(admin, future, Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(2)), p);
        mvc.perform(post("/api/submissions", user, """
                        {"problemId": %d, "contestSlug": "%s", "language": "cpp", "mode": "SUBMIT", "source": "x"}
                        """.formatted(p, future)))
                .andExpect(status().isForbidden());

        String past = unique("past");
        createContest(admin, past, Instant.now().minus(Duration.ofHours(3)), Instant.now().minus(Duration.ofHours(1)), p);
        submitAndJudge(user, p, past, "SUBMIT", Verdict.ACCEPTED);
        Thread.sleep(300);
        mvc.perform(get("/api/contests/" + past + "/scoreboard")).andExpect(jsonPath("$.rows", hasSize(0)));
    }
}
