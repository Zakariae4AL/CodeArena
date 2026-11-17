package dev.codearena.shared;

/** The signed-in user, available in controllers with {@code @AuthenticationPrincipal}. */
public record CurrentUser(long id, String username, Role role) {

    public enum Role { USER, ADMIN }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
