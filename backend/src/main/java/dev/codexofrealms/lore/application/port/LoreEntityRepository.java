package dev.codexofrealms.lore.application.port;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.application.entity.LoreEntityView;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.lore.domain.LoreEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoreEntityRepository {

    void createEntity(
        UUID id, UUID realmId, LoreEntity entity, UUID accessPolicyId,
        UUID userId, List<SourceEvidence> evidence
    );

    boolean updateEntity(
        UUID id, UUID realmId, LoreEntity entity, UUID accessPolicyId,
        UUID userId, List<SourceEvidence> evidence
    );

    List<LoreEntityView> listAccessibleEntities(
        UUID realmId, UUID userId, EntityType type, CanonStatus canonStatus
    );

    Optional<LoreEntityView> findAccessibleEntity(UUID realmId, UUID id, UUID userId);

    Optional<LoreEntityView> findEntityForEditor(UUID realmId, UUID id);

    boolean hasActiveRelations(UUID realmId, UUID entityId);

    boolean deactivateEntity(UUID realmId, UUID id, UUID userId);

    void promoteEntity(UUID realmId, UUID id, UUID userId);
}
