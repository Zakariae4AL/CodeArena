package dev.codearena;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codearena.shared.CurrentUser.Role;
import org.junit.jupiter.api.Test;

class ProblemApiTest extends ApiTestBase {

    @Test
    void publicStatementShowsSamplesButNeverHiddenTests() throws Exception {
        String slug = unique("sum");
        createProblem(adminToken(), slug, true);

        mvc.perform(get("/api/problems/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples", hasSize(1)))
                .andExpect(jsonPath("$.samples[0].expected").value("3\n"))
                .andExpect(content().string(not(containsString("SECRET-INPUT"))));
        mvc.perform(get("/api/problems")).andExpect(jsonPath("$[*].slug", hasItem(slug)));
    }

    @Test
    void draftsAreHiddenFromEveryoneButAdmins() throws Exception {
        String admin = adminToken();
        String slug = unique("draft");
        long id = createProblem(admin, slug, false);

        mvc.perform(get("/api/problems/" + slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/problems")).andExpect(jsonPath("$[*].slug", not(hasItem(slug))));
        mvc.perform(get("/api/admin/problems/" + id, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tests", hasSize(3)));
    }

    @Test
    void onlyAdminsUseTheEditor() throws Exception {
        String user = tokenFor(createUser(Role.USER));
        mvc.perform(get("/api/admin/problems", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/problems")).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesProblems() throws Exception {
        String admin = adminToken();
        mvc.perform(post("/api/admin/problems", admin, """
                        {"slug": "Bad Slug", "title": "", "statement": "x", "difficulty": "EASY",
                         "timeLimitMs": 50, "memoryLimitMb": 4096, "published": true, "tests": []}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").exists())
                .andExpect(jsonPath("$.errors.timeLimitMs").exists())
                .andExpect(jsonPath("$.errors.memoryLimitMb").exists())
                .andExpect(jsonPath("$.errors.tests").exists());
        mvc.perform(post("/api/admin/problems", admin, """
                        {"slug": "%s", "title": "T", "statement": "x", "difficulty": "EASY",
                         "timeLimitMs": 1000, "memoryLimitMb": 256, "published": true,
                         "tests": [{"input": "1", "expected": "1", "sample": false}]}""".formatted(unique("nosample"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("sample")));

        String slug = unique("dup");
        createProblem(admin, slug, true);
        mvc.perform(post("/api/admin/problems", admin, """
                        {"slug": "%s", "title": "T", "statement": "x", "difficulty": "EASY",
                         "timeLimitMs": 1000, "memoryLimitMb": 256, "published": true,
                         "tests": [{"input": "1", "expected": "1", "sample": true}]}""".formatted(slug)))
                .andExpect(status().isConflict());
    }

    @Test
    void updatesReplaceTheTests() throws Exception {
        String admin = adminToken();
        String slug = unique("upd");
        long id = createProblem(admin, slug, true);
        mvc.perform(put("/api/admin/problems/" + id, admin, """
                        {"slug": "%s", "title": "Renamed", "statement": "New", "difficulty": "HARD",
                         "timeLimitMs": 2000, "memoryLimitMb": 128, "published": true,
                         "tests": [{"input": "9", "expected": "9", "sample": true}]}""".formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.tests", hasSize(1)));
    }
}
