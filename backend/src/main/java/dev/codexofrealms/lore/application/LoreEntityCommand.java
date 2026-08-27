package dev.codexofrealms.lore.application;

import dev.codexofrealms.lore.domain.EntityType;
import java.util.List;
import java.util.UUID;

public record LoreEntityCommand(
    EntityType type,
    String displayName,
    List<String> aliases,
    String description,
    UUID accessPolicyId,
    List<UUID> evidenceChunkIds
) {
}
