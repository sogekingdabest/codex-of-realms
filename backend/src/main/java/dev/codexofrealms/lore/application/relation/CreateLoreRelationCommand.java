package dev.codexofrealms.lore.application.relation;

import java.util.List;
import java.util.UUID;

public record CreateLoreRelationCommand(
    UUID sourceEntityId,
    UUID targetEntityId,
    String relationType,
    String description,
    UUID accessPolicyId,
    List<UUID> evidenceChunkIds
) {
}
