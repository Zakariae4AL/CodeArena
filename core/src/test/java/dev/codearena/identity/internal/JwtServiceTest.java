package dev.codearena.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codearena.shared.CurrentUser.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private static JwtService at(Instant now) {
        return new JwtService(SECRET, Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC), new MockEnvironment());
    }

    private static User user() {
        User user = new User("unit", "unit@example.com", "hash", Role.USER, NOW);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    @Test
    void tokensExpire() {
        String token = at(NOW).issue(user());
        assertThat(at(NOW.plusSeconds(3599)).verify(token)).contains(7L);
        assertThat(at(NOW.plusSeconds(3601)).verify(token)).isEmpty();
    }

    @Test
    void refusesWeakAndDefaultSecrets() {
        assertThatThrownBy(() -> new JwtService("short", Duration.ofHours(1), Clock.systemUTC(), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtService("dev-only-secret-change-me-dev-only-secret-change-me",
                Duration.ofHours(1), Clock.systemUTC(), prod))
                .isInstanceOf(IllegalStateException.class);
    }
}
