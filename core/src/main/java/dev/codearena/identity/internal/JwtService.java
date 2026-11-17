package dev.codearena.identity.internal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/** Signs and verifies HMAC-SHA256 tokens whose subject is the user id. */
@Service
public class JwtService {

    static final String DEV_SECRET_PREFIX = "dev-only-";

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(
            @Value("${codearena.jwt.secret}") String secret,
            @Value("${codearena.jwt.ttl}") Duration ttl,
            Clock clock,
            Environment environment) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("codearena.jwt.secret (JWT_SECRET) must be at least 32 bytes long.");
        }
        if (secret.startsWith(DEV_SECRET_PREFIX) && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Set a real JWT_SECRET before running with the 'prod' profile.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** The user id of a valid token; empty for forged, expired, unsigned or malformed tokens. */
    public Optional<Long> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration ttl() {
        return ttl;
    }
}
