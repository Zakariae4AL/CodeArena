package dev.codearena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.codearena.identity.internal.User;
import dev.codearena.shared.CurrentUser.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class AuthApiTest extends ApiTestBase {

    @Test
    void registersARegularUserWithAHashedPassword() throws Exception {
        String name = unique("sara");
        String body = mvc.perform(post("/api/auth/register", null, """
                        {"username": "%s", "email": "%s@Example.com", "password": "Secret123", "role": "ADMIN"}
                        """.formatted(name, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value(name))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(content().string(not(containsString("Secret123"))))
                .andExpect(content().string(not(containsString("password"))))
                .andReturn().getResponse().getContentAsString();

        User saved = users.findByUsernameIgnoreCase(name).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getEmail()).isEqualTo(name.toLowerCase() + "@example.com");
        assertThat(passwordEncoder.matches("Secret123", saved.getPasswordHash())).isTrue();

        mvc.perform(get("/api/auth/me", "Bearer " + read(body, "$.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name));
    }

    @Test
    void rejectsTakenNamesAndWeakInput() throws Exception {
        User existing = createUser(Role.USER);
        mvc.perform(post("/api/auth/register", null, """
                        {"username": "%s", "email": "new@example.com", "password": "Secret123"}
                        """.formatted(existing.getUsername().toUpperCase())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register", null, """
                        {"username": "a b", "email": "nope", "password": "letters"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginGivesTheSameAnswerForUnknownUsersAndWrongPasswords() throws Exception {
        User user = createUser(Role.USER);
        mvc.perform(post("/api/auth/login", null, """
                        {"username": "%s", "password": "Secret123"}""".formatted(user.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
        mvc.perform(post("/api/auth/login", null, """
                        {"username": "%s", "password": "Wrong1234"}""".formatted(user.getUsername())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
        mvc.perform(post("/api/auth/login", null, """
                        {"username": "nobody_here", "password": "Wrong1234"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void rejectsForgedExpiredAndUnsignedTokens() throws Exception {
        User user = createUser(Role.USER);
        String forged = Jwts.builder().subject(String.valueOf(user.getId())).claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8)))
                .compact();
        String expired = Jwts.builder().subject(String.valueOf(user.getId()))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor("test-secret-that-is-long-enough-for-hmac-sha256-1234".getBytes(StandardCharsets.UTF_8)))
                .compact();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String unsigned = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(("{\"sub\":\"" + user.getId() + "\"}").getBytes(StandardCharsets.UTF_8)) + ".";

        for (String token : new String[] {forged, expired, unsigned, "garbage"}) {
            mvc.perform(get("/api/auth/me", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me", tokenFor(user))).andExpect(status().isOk());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
