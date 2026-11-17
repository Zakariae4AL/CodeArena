package dev.codearena.identity.internal;

import dev.codearena.shared.CurrentUser.Role;
import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Demo accounts for local runs: admin and alice, password "codearena1". */
@Component
@Order(1)
@ConditionalOnProperty(name = "codearena.seed.demo", havingValue = "true")
class DemoAccounts implements ApplicationRunner {

    static final String PASSWORD = "codearena1";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    DemoAccounts(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String hash = encoder.encode(PASSWORD);
        users.save(new User("admin", "admin@codearena.dev", hash, Role.ADMIN, clock.instant()));
        users.save(new User("alice", "alice@codearena.dev", hash, Role.USER, clock.instant()));
    }
}
