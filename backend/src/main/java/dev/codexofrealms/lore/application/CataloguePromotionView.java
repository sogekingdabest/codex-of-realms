package dev.codexofrealms.lore.application;

import java.time.Instant;
import java.util.UUID;

public record CataloguePromotionView(UUID id, UUID promotedBy, Instant promotedAt) {
}
