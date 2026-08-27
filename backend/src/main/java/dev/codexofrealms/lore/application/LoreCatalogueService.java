package dev.codexofrealms.lore.application;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.lore.domain.LoreEntity;
import dev.codexofrealms.lore.domain.LoreRelation;
import dev.codexofrealms.lore.infrastructure.LoreCatalogueJdbcRepository;
import dev.codexofrealms.realm.RealmAccess;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoreCatalogueService {

    private final RealmAccess realmAccess;
    private final SourceEvidenceAccess sourceEvidenceAccess;
    private final LoreCatalogueJdbcRepository repository;

    public LoreCatalogueService(
        RealmAccess realmAccess,
        SourceEvidenceAccess sourceEvidenceAccess,
        LoreCatalogueJdbcRepository repository
    ) {
        this.realmAccess = realmAccess;
        this.sourceEvidenceAccess = sourceEvidenceAccess;
        this.repository = repository;
    }

    @Transactional
    public LoreEntityView createEntity(UUID realmId, UUID userId, LoreEntityCommand command) {
        Objects.requireNonNull(command, "command");
        UUID policyId = Objects.requireNonNull(command.accessPolicyId(), "accessPolicyId");
        LoreEntity entity = new LoreEntity(
            command.type(), command.displayName(), command.aliases(), command.description()
        );
        authorizeMutation(realmId, policyId, userId);
        List<SourceEvidence> evidence = sourceEvidenceAccess.resolveActive(
            realmId, policyId, userId, command.evidenceChunkIds()
        );
        UUID id = UUID.randomUUID();
        repository.createEntity(id, realmId, entity, policyId, userId, evidence);
        return repository.findEntityForEditor(realmId, id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<LoreEntityView> listEntities(
        UUID realmId,
        UUID userId,
        EntityType type,
        CanonStatus canonStatus
    ) {
        realmAccess.requireMember(realmId, userId);
        return repository.listAccessibleEntities(realmId, userId, type, canonStatus);
    }

    @Transactional(readOnly = true)
    public LoreEntityView getEntity(UUID realmId, UUID entityId, UUID userId) {
        realmAccess.requireMember(realmId, userId);
        return repository.findAccessibleEntity(realmId, entityId, userId)
            .orElseThrow(CatalogueNotFoundException::new);
    }

    @Transactional
    public LoreEntityView updateEntity(
        UUID realmId,
        UUID entityId,
        UUID userId,
        LoreEntityCommand command
    ) {
        Objects.requireNonNull(command, "command");
        UUID policyId = Objects.requireNonNull(command.accessPolicyId(), "accessPolicyId");
        LoreEntity entity = new LoreEntity(
            command.type(), command.displayName(), command.aliases(), command.description()
        );
        authorizeMutation(realmId, policyId, userId);
        requireEntityForEditor(realmId, entityId);
        List<SourceEvidence> evidence = sourceEvidenceAccess.resolveActive(
            realmId, policyId, userId, command.evidenceChunkIds()
        );
        if (!repository.updateEntity(entityId, realmId, entity, policyId, userId, evidence)) {
            throw new CatalogueNotFoundException();
        }
        return requireEntityForEditor(realmId, entityId);
    }

    @Transactional
    public LoreEntityView promoteEntity(UUID realmId, UUID entityId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        LoreEntityView entity = requireEntityForEditor(realmId, entityId);
        realmAccess.requireEditablePolicy(realmId, entity.accessPolicyId(), userId);
        if (entity.canonStatus() == CanonStatus.PROPOSED) {
            repository.promoteEntity(realmId, entityId, userId);
        }
        return requireEntityForEditor(realmId, entityId);
    }

    @Transactional
    public void deleteEntity(UUID realmId, UUID entityId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        LoreEntityView entity = requireEntityForEditor(realmId, entityId);
        realmAccess.requireEditablePolicy(realmId, entity.accessPolicyId(), userId);
        if (repository.hasActiveRelations(realmId, entityId)) {
            throw new CatalogueConflictException(
                "Delete the entity's active relations before deleting the entity."
            );
        }
        if (!repository.deactivateEntity(realmId, entityId, userId)) {
            throw new CatalogueNotFoundException();
        }
    }

    @Transactional
    public LoreRelationView createRelation(
        UUID realmId,
        UUID userId,
        CreateLoreRelationCommand command
    ) {
        Objects.requireNonNull(command, "command");
        UUID policyId = Objects.requireNonNull(command.accessPolicyId(), "accessPolicyId");
        LoreRelation relation = new LoreRelation(
            command.sourceEntityId(), command.targetEntityId(),
            command.relationType(), command.description()
        );
        authorizeMutation(realmId, policyId, userId);
        if (!repository.bothEntitiesActive(
            realmId, relation.sourceEntityId(), relation.targetEntityId()
        )) {
            throw new CatalogueNotFoundException();
        }
        List<SourceEvidence> evidence = sourceEvidenceAccess.resolveActive(
            realmId, policyId, userId, command.evidenceChunkIds()
        );
        UUID id = UUID.randomUUID();
        try {
            repository.createRelation(id, realmId, relation, policyId, userId, evidence);
        } catch (DuplicateKeyException exception) {
            throw new CatalogueConflictException("That active lore relation already exists.");
        }
        return repository.findRelationForEditor(realmId, id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<LoreRelationView> listRelations(
        UUID realmId,
        UUID userId,
        UUID entityId,
        CanonStatus canonStatus
    ) {
        realmAccess.requireMember(realmId, userId);
        return repository.listAccessibleRelations(realmId, userId, entityId, canonStatus);
    }

    @Transactional(readOnly = true)
    public LoreRelationView getRelation(UUID realmId, UUID relationId, UUID userId) {
        realmAccess.requireMember(realmId, userId);
        return repository.findAccessibleRelation(realmId, relationId, userId)
            .orElseThrow(CatalogueNotFoundException::new);
    }

    @Transactional
    public LoreRelationView updateRelation(
        UUID realmId,
        UUID relationId,
        UUID userId,
        UpdateLoreRelationCommand command
    ) {
        Objects.requireNonNull(command, "command");
        UUID policyId = Objects.requireNonNull(command.accessPolicyId(), "accessPolicyId");
        authorizeMutation(realmId, policyId, userId);
        LoreRelationView existing = requireRelationForEditor(realmId, relationId);
        LoreRelation relation = new LoreRelation(
            existing.sourceEntityId(), existing.targetEntityId(),
            command.relationType(), command.description()
        );
        List<SourceEvidence> evidence = sourceEvidenceAccess.resolveActive(
            realmId, policyId, userId, command.evidenceChunkIds()
        );
        try {
            if (!repository.updateRelation(
                relationId, realmId, relation.relationType(), relation.description(),
                policyId, userId, evidence
            )) {
                throw new CatalogueNotFoundException();
            }
        } catch (DuplicateKeyException exception) {
            throw new CatalogueConflictException("That active lore relation already exists.");
        }
        return requireRelationForEditor(realmId, relationId);
    }

    @Transactional
    public LoreRelationView promoteRelation(UUID realmId, UUID relationId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        LoreRelationView relation = requireRelationForEditor(realmId, relationId);
        realmAccess.requireEditablePolicy(realmId, relation.accessPolicyId(), userId);
        if (relation.canonStatus() == CanonStatus.PROPOSED) {
            repository.promoteRelation(realmId, relationId, userId);
        }
        return requireRelationForEditor(realmId, relationId);
    }

    @Transactional
    public void deleteRelation(UUID realmId, UUID relationId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        LoreRelationView relation = requireRelationForEditor(realmId, relationId);
        realmAccess.requireEditablePolicy(realmId, relation.accessPolicyId(), userId);
        if (!repository.deactivateRelation(realmId, relationId, userId)) {
            throw new CatalogueNotFoundException();
        }
    }

    private void authorizeMutation(UUID realmId, UUID policyId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        realmAccess.requireEditablePolicy(realmId, policyId, userId);
    }

    private LoreEntityView requireEntityForEditor(UUID realmId, UUID entityId) {
        return repository.findEntityForEditor(realmId, entityId)
            .orElseThrow(CatalogueNotFoundException::new);
    }

    private LoreRelationView requireRelationForEditor(UUID realmId, UUID relationId) {
        return repository.findRelationForEditor(realmId, relationId)
            .orElseThrow(CatalogueNotFoundException::new);
    }
}
