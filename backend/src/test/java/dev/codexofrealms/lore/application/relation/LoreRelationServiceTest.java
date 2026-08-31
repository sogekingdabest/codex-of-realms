package dev.codexofrealms.lore.application.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.lore.application.port.LoreRelationRepository;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.realm.RealmAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoreRelationServiceTest {

    private final RealmAccess realmAccess = mock(RealmAccess.class);
    private final SourceEvidenceAccess evidenceAccess = mock(SourceEvidenceAccess.class);
    private final LoreRelationRepository repository = mock(LoreRelationRepository.class);
    private final LoreRelationService service = new LoreRelationService(
        realmAccess, evidenceAccess, repository
    );
    private final UUID realmId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @Test
    void rejectsUnavailableEndpointsBeforeResolvingEvidence() {
        when(repository.bothEntitiesActive(realmId, sourceId, targetId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(realmId, userId, createCommand()))
            .isInstanceOfSatisfying(LoreRelationException.class, exception ->
                assertThat(exception.code())
                    .isEqualTo(LoreRelationException.Code.ENDPOINT_UNAVAILABLE));
        verify(evidenceAccess, never()).resolveActive(any(), any(), any(), any());
        verify(repository, never()).createRelation(any(), any(), any(), any(), any(), any());
    }

    @Test
    void translatesDuplicateCreateResultToStableConflict() {
        when(repository.bothEntitiesActive(realmId, sourceId, targetId)).thenReturn(true);
        when(evidenceAccess.resolveActive(realmId, policyId, userId, List.of()))
            .thenReturn(List.of());
        when(repository.createRelation(any(), any(), any(), any(), any(), any()))
            .thenReturn(LoreRelationRepository.CreateResult.DUPLICATE);

        assertThatThrownBy(() -> service.create(realmId, userId, createCommand()))
            .isInstanceOfSatisfying(LoreRelationException.class, exception ->
                assertThat(exception.code()).isEqualTo(LoreRelationException.Code.DUPLICATE));
    }

    @Test
    void preservesEndpointsWhenUpdatingTheClaim() {
        UUID relationId = UUID.randomUUID();
        when(repository.findRelationForEditor(realmId, relationId))
            .thenReturn(Optional.of(view(relationId, CanonStatus.PROPOSED)));
        when(evidenceAccess.resolveActive(realmId, policyId, userId, List.of()))
            .thenReturn(List.of());
        when(repository.updateRelation(
            relationId, realmId, "PROTEGE_A", "Nueva descripción",
            policyId, userId, List.of()
        )).thenReturn(LoreRelationRepository.UpdateResult.UPDATED);

        LoreRelationView updated = service.update(
            realmId, relationId, userId,
            new UpdateLoreRelationCommand("protege a", "Nueva descripción", policyId, List.of())
        );

        assertThat(updated.sourceEntityId()).isEqualTo(sourceId);
        assertThat(updated.targetEntityId()).isEqualTo(targetId);
        verify(repository).updateRelation(
            relationId, realmId, "PROTEGE_A", "Nueva descripción",
            policyId, userId, List.of()
        );
    }

    @Test
    void doesNotPromoteARelationThatIsAlreadyCanon() {
        UUID relationId = UUID.randomUUID();
        when(repository.findRelationForEditor(realmId, relationId))
            .thenReturn(Optional.of(view(relationId, CanonStatus.CANON)));

        LoreRelationView result = service.promote(realmId, relationId, userId);

        assertThat(result.canonStatus()).isEqualTo(CanonStatus.CANON);
        verify(repository, never()).promoteRelation(any(), any(), any());
    }

    private CreateLoreRelationCommand createCommand() {
        return new CreateLoreRelationCommand(
            sourceId, targetId, "protege a", "", policyId, List.of()
        );
    }

    private LoreRelationView view(UUID id, CanonStatus status) {
        return new LoreRelationView(
            id, realmId, sourceId, "Nara", targetId, "Lumbrevela", "PROTEGE_A", "",
            status, policyId, List.of(), userId, Instant.EPOCH, userId, Instant.EPOCH,
            null, null, List.of()
        );
    }
}
