package dev.codexofrealms.lore.application;

import java.util.List;
import java.util.UUID;

public record UpdateLoreRelationCommand(
    String relationType,
    String description,
    UUID accessPolicyId,
    List<UUID> evidenceChunkIds
) {
}
