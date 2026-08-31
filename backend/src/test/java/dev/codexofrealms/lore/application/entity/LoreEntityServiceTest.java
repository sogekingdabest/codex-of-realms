package dev.codexofrealms.lore.application.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.lore.application.port.LoreEntityRepository;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.lore.domain.LoreEntity;
import dev.codexofrealms.realm.RealmAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoreEntityServiceTest {

    private final RealmAccess realmAccess = mock(RealmAccess.class);
    private final SourceEvidenceAccess evidenceAccess = mock(SourceEvidenceAccess.class);
    private final LoreEntityRepository repository = mock(LoreEntityRepository.class);
    private final LoreEntityService service = new LoreEntityService(
        realmAccess, evidenceAccess, repository
    );
    private final UUID realmId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();

    @Test
    void createsNormalizedEntityWithResolvedEvidence() {
        UUID chunkId = UUID.randomUUID();
        SourceEvidence evidence = evidence(chunkId);
        when(evidenceAccess.resolveActive(realmId, policyId, userId, List.of(chunkId)))
            .thenReturn(List.of(evidence));
        when(repository.findEntityForEditor(eq(realmId), any(UUID.class)))
            .thenAnswer(invocation -> Optional.of(view(invocation.getArgument(1), CanonStatus.PROPOSED)));

        LoreEntityView created = service.create(
            realmId,
            userId,
            new LoreEntityCommand(
                EntityType.CHARACTER, "  Nara   Vey  ", List.of(" Cartógrafa "),
                "Descripción", policyId, List.of(chunkId)
            )
        );

        assertThat(created.canonStatus()).isEqualTo(CanonStatus.PROPOSED);
        verify(realmAccess).requireEditor(realmId, userId);
        verify(realmAccess).requireEditablePolicy(realmId, policyId, userId);
        ArgumentCaptor<LoreEntity> entity = ArgumentCaptor.forClass(LoreEntity.class);
        verify(repository).createEntity(
            eq(created.id()), eq(realmId), entity.capture(), eq(policyId),
            eq(userId), eq(List.of(evidence))
        );
        assertThat(entity.getValue().displayName()).isEqualTo("Nara Vey");
        assertThat(entity.getValue().aliases()).containsExactly("Cartógrafa");
    }

    @Test
    void hidesUnavailableEntityAfterMembershipCheck() {
        UUID entityId = UUID.randomUUID();
        when(repository.findAccessibleEntity(realmId, entityId, userId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(realmId, entityId, userId))
            .isInstanceOfSatisfying(LoreEntityException.class, exception ->
                assertThat(exception.code()).isEqualTo(LoreEntityException.Code.UNAVAILABLE));
        verify(realmAccess).requireMember(realmId, userId);
    }

    @Test
    void blocksDeletionWhileActiveRelationsExist() {
        UUID entityId = UUID.randomUUID();
        when(repository.findEntityForEditor(realmId, entityId))
            .thenReturn(Optional.of(view(entityId, CanonStatus.PROPOSED)));
        when(repository.hasActiveRelations(realmId, entityId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(realmId, entityId, userId))
            .isInstanceOfSatisfying(LoreEntityException.class, exception ->
                assertThat(exception.code()).isEqualTo(LoreEntityException.Code.ACTIVE_RELATIONS));
        verify(repository, never()).deactivateEntity(any(), any(), any());
    }

    @Test
    void doesNotPromoteAnEntityThatIsAlreadyCanon() {
        UUID entityId = UUID.randomUUID();
        when(repository.findEntityForEditor(realmId, entityId))
            .thenReturn(Optional.of(view(entityId, CanonStatus.CANON)));

        LoreEntityView result = service.promote(realmId, entityId, userId);

        assertThat(result.canonStatus()).isEqualTo(CanonStatus.CANON);
        verify(repository, never()).promoteEntity(any(), any(), any());
    }

    private LoreEntityView view(UUID id, CanonStatus status) {
        return new LoreEntityView(
            id, realmId, EntityType.CHARACTER, "Nara Vey", List.of(), "",
            status, policyId, List.of(), userId, Instant.EPOCH, userId, Instant.EPOCH,
            null, null, List.of()
        );
    }

    private static SourceEvidence evidence(UUID chunkId) {
        return new SourceEvidence(
            UUID.randomUUID(), UUID.randomUUID(), chunkId, "Atlas", "checksum", null, 0, 10
        );
    }
}
