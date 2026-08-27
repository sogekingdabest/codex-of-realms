package dev.codexofrealms.lore.application;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoreEntityView(
    UUID id,
    UUID realmId,
    EntityType type,
    String displayName,
    List<String> aliases,
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
