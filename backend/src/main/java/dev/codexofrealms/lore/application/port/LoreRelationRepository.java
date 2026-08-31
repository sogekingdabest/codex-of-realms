package dev.codexofrealms.lore.application.port;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.application.relation.LoreRelationView;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.LoreRelation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoreRelationRepository {

    enum CreateResult { CREATED, DUPLICATE }

    enum UpdateResult { UPDATED, UNAVAILABLE, DUPLICATE }

    boolean bothEntitiesActive(UUID realmId, UUID sourceId, UUID targetId);

    CreateResult createRelation(
        UUID id, UUID realmId, LoreRelation relation, UUID accessPolicyId,
        UUID userId, List<SourceEvidence> evidence
    );

    UpdateResult updateRelation(
        UUID id, UUID realmId, String relationType, String description,
        UUID accessPolicyId, UUID userId, List<SourceEvidence> evidence
    );

    List<LoreRelationView> listAccessibleRelations(
        UUID realmId, UUID userId, UUID entityId, CanonStatus canonStatus
    );

    Optional<LoreRelationView> findAccessibleRelation(UUID realmId, UUID id, UUID userId);

    Optional<LoreRelationView> findRelationForEditor(UUID realmId, UUID id);

    boolean deactivateRelation(UUID realmId, UUID id, UUID userId);

    void promoteRelation(UUID realmId, UUID id, UUID userId);
}
