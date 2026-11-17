package dev.codearena.identity.internal;

import dev.codearena.shared.ApiException;
import dev.codearena.shared.CurrentUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

    record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "3 to 20 letters, digits or underscores") String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String password) {
    }

    record LoginRequest(@NotBlank @Size(max = 255) String username, @NotBlank @Size(max = 72) String password) {
    }

    record UserView(long id, String username, CurrentUser.Role role) {
        static UserView of(CurrentUser user) {
            return new UserView(user.id(), user.username(), user.role());
        }
    }

    record TokenResponse(String token, long expiresInSeconds, UserView user) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    // Compared against when the user does not exist, so both cases take as long.
    private final String dummyHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-1");
    }

    @Transactional
    TokenResponse register(RegisterRequest request) {
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw ApiException.conflict("This username is taken.");
        }
        if (users.existsByEmailIgnoreCase(request.email().trim())) {
            throw ApiException.conflict("An account already exists with this email.");
        }
        // The role is never taken from the request: new accounts are always regular users.
        User user = users.save(new User(request.username(), request.email(),
                passwordEncoder.encode(request.password()), CurrentUser.Role.USER, clock.instant()));
        return issue(user);
    }

    @Transactional(readOnly = true)
    TokenResponse login(LoginRequest request) {
        User user = users.findByUsernameIgnoreCase(request.username().trim()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        if (!passwordEncoder.matches(request.password(), hash) || user == null) {
            throw new BadCredentialsException("Invalid username or password.");
        }
        return issue(user);
    }

    private TokenResponse issue(User user) {
        return new TokenResponse(jwtService.issue(user), jwtService.ttl().toSeconds(), UserView.of(user.toCurrentUser()));
    }
}
