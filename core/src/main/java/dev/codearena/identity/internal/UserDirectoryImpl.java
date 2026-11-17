package dev.codearena.identity.internal;

import dev.codearena.identity.UserDirectory;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryImpl implements UserDirectory {

    private final UserRepository users;

    UserDirectoryImpl(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> usernames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getUsername));
    }
}
