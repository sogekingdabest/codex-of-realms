package dev.codexofrealms.realm.application;

import dev.codexofrealms.realm.domain.RealmRole;
import java.util.UUID;

public record MembershipView(
    UUID userId,
    String displayName,
    String email,
    RealmRole role
) {
}
