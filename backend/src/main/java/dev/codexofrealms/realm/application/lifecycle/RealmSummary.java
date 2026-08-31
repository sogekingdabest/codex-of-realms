package dev.codexofrealms.realm.application.lifecycle;

import dev.codexofrealms.realm.domain.RealmRole;
import java.util.UUID;

public record RealmSummary(
    UUID id,
    String name,
    RealmRole role
) {
}
