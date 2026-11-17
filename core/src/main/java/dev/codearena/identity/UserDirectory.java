package dev.codearena.identity;

import java.util.Collection;
import java.util.Map;

/** Public API of the identity module for other modules. */
public interface UserDirectory {

    /** Usernames by user id; unknown ids are left out. */
    Map<Long, String> usernames(Collection<Long> ids);
}
