package dev.codexofrealms.lore.application.relation;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.lore.application.port.LoreRelationRepository;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.LoreRelation;
import dev.codexofrealms.realm.RealmAccess;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoreRelationService {

    private static final String COMMAND_REQUIRED = "command";
    private static final String ACCESS_POLICY_REQUIRED = "accessPolicyId";

    private final RealmAccess realmAccess;
    private final SourceEvidenceAccess sourceEvidenceAccess;
    private final LoreRelationRepository repository;

    public LoreRelationService(
        RealmAccess realmAccess,
        SourceEvidenceAccess sourceEvidenceAccess,
        LoreRelationRepository repository
    ) {
        this.realmAccess = realmAccess;
        this.sourceEvidenceAccess = sourceEvidenceAccess;
        this.repository = repository;
    }

    @Transactional
    public LoreRelationView create(
        UUID realmId,
        UUID userId,
        CreateLoreRelationCommand command
    ) {
        Objects.requireNonNull(command, COMMAND_REQUIRED);
        UUID policyId = Objects.requireNonNull(command.accessPolicyId(), ACCESS_POLICY_REQUIRED);
        LoreRelation relation = new LoreRelation(
            command.sourceEntityId(), command.targetEntityId(),
            command.relationType(), command.description()
        );
        authorizeMutation(realmId, policyId, userId);
        if (!repository.bothEntitiesActive(
            realmId, relation.sourceEntityId(), relation.targetEntityId()
        )) {
            throw LoreRelationException.endpointUnavailable();
        }
        List<SourceEvidence> evidence = sourceEvidenceAccess.resolveActive(
            realmId, policyId, userId, command.evidenceChunkIds()
        );
        UUID id = UUID.randomUUID();
        if (repository.createRelation(id, realmId, relation, policyId, userId, evidence)
            == LoreRelationRepository.CreateResult.DUPLICATE) {
            throw LoreRelationException.duplicate();
        }
        return requireForEditor(realmId, id);
    }

    @Transactional(readOnly = true)
    public List<LoreRelationView> list(
        UUID realmId,
        UUID userId,
        UUID entityId,
        CanonStatus canonStatus
    ) {
        realmAccess.requireMember(realmId, userId);
        return repository.listAccessibleRelations(realmId, userId, entityId, canonStatus);
    }

    @Transactional(readOnly = true)
    public LoreRelationView get(UUID realmId, UUID relationId, UUID userId) {
        realmAccess.requireMember(realmId, userId);
        return repository.findAccessibleRelation(realmId, relationId, userId)
            .orElseThrow(LoreRelationException::unavailable);
    }

    @Transactional
    public LoreRelationView update(
        UUID realmId,
        UUID relationId,
        UUID userId,
        UpdateLoreRelationCommand command
    ) {
        Objects.requireNonNull(command, COMMAND_REQUIRED);
        UUID policyId = Objects.requireNonNull(command.accessPolicyId(), ACCESS_POLICY_REQUIRED);
        authorizeMutation(realmId, policyId, userId);
        LoreRelationView existing = requireForEditor(realmId, relationId);
        LoreRelation relation = new LoreRelation(
            existing.sourceEntityId(), existing.targetEntityId(),
            command.relationType(), command.description()
        );
        List<SourceEvidence> evidence = sourceEvidenceAccess.resolveActive(
            realmId, policyId, userId, command.evidenceChunkIds()
        );
        LoreRelationRepository.UpdateResult result = repository.updateRelation(
            relationId, realmId, relation.relationType(), relation.description(),
            policyId, userId, evidence
        );
        if (result == LoreRelationRepository.UpdateResult.UNAVAILABLE) {
            throw LoreRelationException.unavailable();
        }
        if (result == LoreRelationRepository.UpdateResult.DUPLICATE) {
            throw LoreRelationException.duplicate();
        }
        return requireForEditor(realmId, relationId);
    }

    @Transactional
    public LoreRelationView promote(UUID realmId, UUID relationId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        LoreRelationView relation = requireForEditor(realmId, relationId);
        realmAccess.requireEditablePolicy(realmId, relation.accessPolicyId(), userId);
        if (relation.canonStatus() == CanonStatus.PROPOSED) {
            repository.promoteRelation(realmId, relationId, userId);
        }
        return requireForEditor(realmId, relationId);
    }

    @Transactional
    public void delete(UUID realmId, UUID relationId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        LoreRelationView relation = requireForEditor(realmId, relationId);
        realmAccess.requireEditablePolicy(realmId, relation.accessPolicyId(), userId);
        if (!repository.deactivateRelation(realmId, relationId, userId)) {
            throw LoreRelationException.unavailable();
        }
    }

    private void authorizeMutation(UUID realmId, UUID policyId, UUID userId) {
        realmAccess.requireEditor(realmId, userId);
        realmAccess.requireEditablePolicy(realmId, policyId, userId);
    }

    private LoreRelationView requireForEditor(UUID realmId, UUID relationId) {
        return repository.findRelationForEditor(realmId, relationId)
            .orElseThrow(LoreRelationException::unavailable);
    }
}
