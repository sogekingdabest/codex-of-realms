package dev.codexofrealms.lore.application.entity;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.lore.application.port.LoreEntityRepository;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.lore.domain.LoreEntity;
import dev.codexofrealms.realm.RealmAccess;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoreEntityService {

    private static final String COMMAND_REQUIRED = "command";
    private static final String ACCESS_POLICY_REQUIRED = "accessPolicyId";

    private final RealmAccess realmAccess;
    private final SourceEvidenceAccess sourceEvidenceAccess;
    private final LoreEntityRepository repository;

    public LoreEntityService(
        RealmAccess realmAccess,
        SourceEvidenceAccess sourceEvidenceAccess,
        LoreEntityRepository repository
    ) {
        this.realmAccess = realmAccess;
        this.sourceEvidenceAccess = sourceEvidenceAccess;
        this.repository = repository;
    }

    @Transactional
    public LoreEntityView create(UUID realmId, UUID userId, LoreEntityCommand command) {
        Objects.requireNonNull(command, COMMAND_REQUIRED);
        UUID policyId = Objects.requireNonNull(command.accessPolicyId(), ACCESS_POLICY_REQUIRED);
        LoreEntity entity = new LoreEntity(
            command.type(), command.displayName(), command.aliases(), command.description()
        );
        authorizeMutation(realmId, policyId, userId);
        List<SourceEvidence> evidence = sourceEvidenceAccess.resolveActive(
            realmId, policyId, userId, command.evidenceChunkIds()
        );
        UUID id = UUID.randomUUID();
        repository.createEntity(id, realmId, entity, policyId, userId, evidence);
        return requireForEditor(realmId, id);
    }

    @Transactional(readOnly = true)
    public List<LoreEntityView> list(
        UUID realmId,
        UUID userId,
        EntityType type,
        CanonStatus canonStatus
    ) {
        realmAccess.requireMember(realmId, userId);
        return repository.listAccessibleEntities(realmId, userId, type, canonStatus);
    }

    @Transactional(readOnly = true)
    public LoreEntityView get(UUID realmId, UUID entityId, UUID userId) {
        realmAccess.requireMember(realmId, userId);
        return repository.findAccessibleEntity(realmId, entityId, userId)
            .orElseThrow(LoreEntityException::unavailable);
    }

    @Transactional
    public LoreEntityView update(
        UUID realmId,
        UUID entityId,
        UUID userId,
        LoreEntityCommand command
    ) {
        Objects.requireNonNull(command, COMMAND_REQUIRED);
        UUID policyId = Objects.requireNonNull(command.accessPolicyId(), ACCESS_POLICY_REQUIRED);
        LoreEntity entity = new LoreEntity(
            command.type(), command.displayName(), command.aliases(), command.description()
        );
        authorizeMutation(realmId, policyId, userId);
        requireForEditor(realmId, entityId);
        List<SourceEvidence> evidence = sourceEvidenceAccess.resolveActive(
            realmId, policyId, userId, command.evidenceChunkIds()
        );
        if (!repository.updateEntity(entityId, realmId, entity, policyId, userId, evidence)) {
            throw LoreEntityException.unavailable();
        }
        return requireForEditor(realmId, entityId);
    }

    @Transactional
    public LoreEntityView promote(UUID realmId, UUID entityId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        LoreEntityView entity = requireForEditor(realmId, entityId);
        realmAccess.requireEditablePolicy(realmId, entity.accessPolicyId(), userId);
        if (entity.canonStatus() == CanonStatus.PROPOSED) {
            repository.promoteEntity(realmId, entityId, userId);
        }
        return requireForEditor(realmId, entityId);
    }

    @Transactional
    public void delete(UUID realmId, UUID entityId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        LoreEntityView entity = requireForEditor(realmId, entityId);
        realmAccess.requireEditablePolicy(realmId, entity.accessPolicyId(), userId);
        if (repository.hasActiveRelations(realmId, entityId)) {
            throw LoreEntityException.activeRelations();
        }
        if (!repository.deactivateEntity(realmId, entityId, userId)) {
            throw LoreEntityException.unavailable();
        }
    }

    private void authorizeMutation(UUID realmId, UUID policyId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        realmAccess.requireEditablePolicy(realmId, policyId, userId);
    }

    private LoreEntityView requireForEditor(UUID realmId, UUID entityId) {
        return repository.findEntityForEditor(realmId, entityId)
            .orElseThrow(LoreEntityException::unavailable);
    }
}
