package dev.codearena;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.codearena.identity.internal.JwtService;
import dev.codearena.identity.internal.User;
import dev.codearena.identity.internal.UserRepository;
import dev.codearena.shared.CurrentUser.Role;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Starts the whole application on H2 with the in-memory judge queue and sends real
 * HTTP requests through the security filters. Tests are not transactional (events are
 * delivered after commit), so every test uses unique names instead of a rollback.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ApiTestBase {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected static String unique(String prefix) {
        return prefix + SEQUENCE.incrementAndGet() + "x" + (System.nanoTime() % 100_000);
    }

    protected User createUser(Role role) {
        String name = unique(role == Role.ADMIN ? "adm" : "usr");
        return users.save(new User(name, name + "@example.com", passwordEncoder.encode("Secret123"), role, Instant.now()));
    }

    protected String tokenFor(User user) {
        return "Bearer " + jwtService.issue(user);
    }

    protected String adminToken() {
        return tokenFor(createUser(Role.ADMIN));
    }

    protected static MockHttpServletRequestBuilder get(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    protected static MockHttpServletRequestBuilder get(String url, String token) {
        return MockMvcRequestBuilders.get(url).header(HttpHeaders.AUTHORIZATION, token);
    }

    protected static MockHttpServletRequestBuilder post(String url, String token, String json) {
        var request = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, token);
    }

    protected static MockHttpServletRequestBuilder put(String url, String token, String json) {
        return MockMvcRequestBuilders.put(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected static <T> T read(String json, String path) {
        return JsonPath.read(json, path);
    }

    /** Creates a problem through the admin API and returns its id. */
    protected long createProblem(String adminToken, String slug, boolean published) throws Exception {
        String body = """
                {"slug": "%s", "title": "Sum %s", "statement": "Add two numbers.", "difficulty": "EASY",
                 "timeLimitMs": 1000, "memoryLimitMb": 256, "published": %s,
                 "tests": [
                   {"input": "1 2\\n", "expected": "3\\n", "sample": true},
                   {"input": "SECRET-INPUT 40 2\\n", "expected": "42\\n", "sample": false},
                   {"input": "5 5\\n", "expected": "10\\n", "sample": false}
                 ]}
                """.formatted(slug, slug, published);
        String response = mvc.perform(post("/api/admin/problems", adminToken, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) read(response, "$.id")).longValue();
    }

    /** Creates a contest through the admin API and returns its id. */
    protected long createContest(String adminToken, String slug, Instant start, Instant end, long... problemIds)
            throws Exception {
        StringBuilder ids = new StringBuilder();
        for (long id : problemIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(id);
        }
        String body = """
                {"slug": "%s", "title": "Contest %s", "description": "", "startsAt": "%s", "endsAt": "%s",
                 "problemIds": [%s]}
                """.formatted(slug, slug, start, end, ids);
        String response = mvc.perform(post("/api/admin/contests", adminToken, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) read(response, "$.id")).longValue();
    }
}
