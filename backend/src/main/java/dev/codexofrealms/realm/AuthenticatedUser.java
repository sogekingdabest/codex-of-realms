package dev.codexofrealms.realm;

import java.util.UUID;

/** Authenticated identity synchronized with the application's user store. */
public record AuthenticatedUser(
    UUID id,
    String issuer,
    String subject,
    String displayName,
    String email,
    boolean emailVerified
) {
    public AuthenticatedUser(UUID id, String issuer, String subject, String displayName, String email) {
        this(id, issuer, subject, displayName, email, false);
    }
}
