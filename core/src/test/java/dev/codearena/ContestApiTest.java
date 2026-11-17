package dev.codearena;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContestApiTest extends ApiTestBase {

    @Test
    void theProblemSetIsSecretUntilTheStart() throws Exception {
        String admin = adminToken();
        long p = createProblem(admin, unique("secret"), false);
        String slug = unique("future");
        createContest(admin, slug, Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(3)), p);

        mvc.perform(get("/api/contests/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("UPCOMING"))
                .andExpect(jsonPath("$.problems", hasSize(0)));
        mvc.perform(get("/api/contests/" + slug + "/problems/A")).andExpect(status().isForbidden());
        mvc.perform(get("/api/contests/" + slug, admin)).andExpect(jsonPath("$.problems", hasSize(1)));
    }

    @Test
    void aRunningContestShowsItsProblemsEvenUnpublishedOnes() throws Exception {
        String admin = adminToken();
        long a = createProblem(admin, unique("ca"), false);
        long b = createProblem(admin, unique("cb"), true);
        String slug = unique("live");
        createContest(admin, slug, Instant.now().minus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofHours(1)), a, b);

        mvc.perform(get("/api/contests/" + slug))
                .andExpect(jsonPath("$.phase").value("RUNNING"))
                .andExpect(jsonPath("$.problems[0].label").value("A"))
                .andExpect(jsonPath("$.problems[1].label").value("B"));
        mvc.perform(get("/api/contests/" + slug + "/problems/a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problem.id").value(a))
                .andExpect(jsonPath("$.problem.samples", hasSize(1)));
    }

    @Test
    void validatesContests() throws Exception {
        String admin = adminToken();
        long p = createProblem(admin, unique("vp"), true);
        Instant now = Instant.now();
        String base = """
                {"slug": "%s", "title": "T", "description": "", "startsAt": "%s", "endsAt": "%s", "problemIds": %s}""";
        mvc.perform(post("/api/admin/contests", admin, base.formatted(unique("c"), now, now.minusSeconds(60), "[" + p + "]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/contests", admin, base.formatted(unique("c"), now, now.plusSeconds(60), "[" + p + "," + p + "]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/contests", admin, base.formatted(unique("c"), now, now.plusSeconds(60), "[999999]")))
                .andExpect(status().isBadRequest());
    }
}
