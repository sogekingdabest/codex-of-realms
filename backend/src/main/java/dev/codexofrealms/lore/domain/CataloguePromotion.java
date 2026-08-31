package dev.codexofrealms.lore.domain;

import java.time.Instant;
import java.util.UUID;

public record CataloguePromotion(UUID id, UUID promotedBy, Instant promotedAt) {
}
