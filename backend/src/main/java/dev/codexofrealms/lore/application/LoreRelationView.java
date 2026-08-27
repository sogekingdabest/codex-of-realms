package dev.codexofrealms.lore.application;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.domain.CanonStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoreRelationView(
    UUID id,
    UUID realmId,
    UUID sourceEntityId,
    String sourceEntityName,
    UUID targetEntityId,
    String targetEntityName,
    String relationType,
    String description,
    CanonStatus canonStatus,
    UUID accessPolicyId,
    List<SourceEvidence> sourceEvidence,
    UUID createdBy,
    Instant createdAt,
    UUID updatedBy,
    Instant updatedAt,
    UUID promotedBy,
    Instant promotedAt,
    List<CataloguePromotionView> promotionHistory
) {
}
