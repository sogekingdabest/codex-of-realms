package dev.codexofrealms.lore.application.port;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.application.LoreEntityView;
import dev.codexofrealms.lore.application.LoreRelationView;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.lore.domain.LoreEntity;
import dev.codexofrealms.lore.domain.LoreRelation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoreCatalogueRepository {

    void createEntity(
        UUID id,
        UUID realmId,
        LoreEntity entity,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    );

    boolean updateEntity(
        UUID id,
        UUID realmId,
        LoreEntity entity,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    );

    List<LoreEntityView> listAccessibleEntities(
        UUID realmId,
        UUID userId,
        EntityType type,
        CanonStatus canonStatus
    );

    Optional<LoreEntityView> findAccessibleEntity(UUID realmId, UUID id, UUID userId);

    Optional<LoreEntityView> findEntityForEditor(UUID realmId, UUID id);

    boolean bothEntitiesActive(UUID realmId, UUID sourceId, UUID targetId);

    boolean hasActiveRelations(UUID realmId, UUID entityId);

    boolean deactivateEntity(UUID realmId, UUID id, UUID userId);

    void promoteEntity(UUID realmId, UUID id, UUID userId);

    void createRelation(
        UUID id,
        UUID realmId,
        LoreRelation relation,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    );

    boolean updateRelation(
        UUID id,
        UUID realmId,
        String relationType,
        String description,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    );

    List<LoreRelationView> listAccessibleRelations(
        UUID realmId,
        UUID userId,
        UUID entityId,
        CanonStatus canonStatus
    );

    Optional<LoreRelationView> findAccessibleRelation(UUID realmId, UUID id, UUID userId);

    Optional<LoreRelationView> findRelationForEditor(UUID realmId, UUID id);

    boolean deactivateRelation(UUID realmId, UUID id, UUID userId);

    void promoteRelation(UUID realmId, UUID id, UUID userId);
}
