package dev.codexofrealms.realm.application.invitation;

import dev.codexofrealms.realm.domain.InvitationStatus;
import dev.codexofrealms.realm.domain.RealmRole;
import java.time.Instant;
import java.util.UUID;

public record InvitationView(
    UUID id,
    UUID realmId,
    String email,
    RealmRole role,
    InvitationStatus status,
    UUID acceptedUserId,
    Instant createdAt
) {
}
