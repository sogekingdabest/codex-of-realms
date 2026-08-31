package dev.codexofrealms.realm.application.identity;

import java.util.UUID;

public record AuthenticatedUser(
    UUID id,
    String issuer,
    String subject,
    String displayName,
    String email
) {
}
