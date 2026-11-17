package dev.codearena;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codearena.shared.CurrentUser.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "codearena.security.auth-requests-per-minute=3",
        "codearena.judge.submissions-per-minute=2"})
class RateLimitTest extends ApiTestBase {

    @Test
    void limitsPasswordGuessing() throws Exception {
        String attempt = """
                {"username": "victim", "password": "Guess1234"}""";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login", null, attempt)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login", null, attempt)).andExpect(status().isTooManyRequests());
    }

    @Test
    void limitsSubmissionFloods() throws Exception {
        long problem = createProblem(adminToken(), unique("flood"), true);
        String user = tokenFor(createUser(Role.USER));
        String body = """
                {"problemId": %d, "language": "cpp", "mode": "RUN", "source": "int main(){}"}""".formatted(problem);
        mvc.perform(post("/api/submissions", user, body)).andExpect(status().isAccepted());
        mvc.perform(post("/api/submissions", user, body)).andExpect(status().isAccepted());
        mvc.perform(post("/api/submissions", user, body)).andExpect(status().isTooManyRequests());
    }
}
