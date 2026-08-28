package dev.codexofrealms.realm.application;

import dev.codexofrealms.realm.domain.AccessClassification;
import java.util.UUID;

public record AccessPolicyView(
    UUID id,
    UUID realmId,
    AccessClassification classification,
    String name,
    String description
) {
}
