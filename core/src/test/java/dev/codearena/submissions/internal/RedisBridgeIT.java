package dev.codearena.submissions.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codearena.ApiTestBase;
import dev.codearena.shared.CurrentUser.Role;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Round trip through a real Redis, the way the Go judge talks to the core.
 * Runs when REDIS_HOST is set (CI starts a Redis service).
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
@TestPropertySource(properties = {
        "codearena.judge.queue=redis",
        "management.health.redis.enabled=true",
        "codearena.judge.jobs-stream=it:jobs",
        "codearena.judge.results-stream=it:results"})
class RedisBridgeIT extends ApiTestBase {

    @Autowired
    StringRedisTemplate redis;

    @Test
    void jobsGoOutAndResultsComeBack() throws Exception {
        long problem = createProblem(adminToken(), unique("redis"), true);
        String user = tokenFor(createUser(Role.USER));
        String response = mvc.perform(post("/api/submissions", user, """
                        {"problemId": %d, "language": "python", "mode": "SUBMIT", "source": "print(3)"}""".formatted(problem)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = read(response, "$.id");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                redis.opsForStream().range("it:jobs", Range.unbounded()).stream()
                        .anyMatch(r -> String.valueOf(r.getValue().get("payload")).contains(id))).isTrue());

        String result = """
                {"submissionId":"%s","type":"RESULT","total":3,"verdict":"ACCEPTED","passed":3,"timeMs":12,"memoryKb":4000,"tests":[]}
                """.formatted(id);
        redis.opsForStream().add(StreamRecords.newRecord().in("it:results").ofMap(Map.of("payload", result)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/submissions/" + id, user)).andExpect(jsonPath("$.verdict").value("ACCEPTED")));
    }
}
