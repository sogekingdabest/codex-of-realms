package dev.codexofrealms.content.application.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.content.application.port.SourceRepository;
import dev.codexofrealms.realm.RealmAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceEvidenceServiceTest {
    private final RealmAccess realmAccess = mock(RealmAccess.class);
    private final SourceRepository repository = mock(SourceRepository.class);
    private final SourceEvidenceService service = new SourceEvidenceService(realmAccess, repository);
    private final UUID realmId = UUID.randomUUID();
    private final UUID policyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void deduplicatesWhilePreservingRequestedOrder() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        SourceEvidence first = evidence(firstId, "First");
        SourceEvidence second = evidence(secondId, "Second");
        when(repository.findActiveEvidence(realmId, policyId, firstId)).thenReturn(Optional.of(first));
        when(repository.findActiveEvidence(realmId, policyId, secondId)).thenReturn(Optional.of(second));

        List<SourceEvidence> result = service.resolveActive(
            realmId, policyId, userId, List.of(firstId, secondId, firstId)
        );

        assertThat(result).containsExactly(first, second);
        verify(realmAccess).requireEditor(realmId, userId);
        verify(realmAccess).requireEditablePolicy(realmId, policyId, userId);
    }

    @Test
    void rejectsNullAndMoreThanTwentyDistinctIdentifiers() {
        List<UUID> withNull = new ArrayList<>();
        withNull.add(UUID.randomUUID());
        withNull.add(null);
        assertInvalid(withNull);

        assertInvalid(java.util.stream.IntStream.range(0, 21)
            .mapToObj(ignored -> UUID.randomUUID()).toList());
    }

    @Test
    void hidesMissingEvidenceBehindUnavailable() {
        UUID chunkId = UUID.randomUUID();
        when(repository.findActiveEvidence(realmId, policyId, chunkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveActive(realmId, policyId, userId, List.of(chunkId)))
            .isInstanceOfSatisfying(SourceEvidenceException.class,
                exception -> assertThat(exception.code()).isEqualTo(SourceEvidenceException.Code.UNAVAILABLE));
    }

    private void assertInvalid(List<UUID> ids) {
        assertThatThrownBy(() -> service.resolveActive(realmId, policyId, userId, ids))
            .isInstanceOfSatisfying(SourceEvidenceException.class,
                exception -> assertThat(exception.code()).isEqualTo(SourceEvidenceException.Code.INVALID_SELECTION));
    }

    private static SourceEvidence evidence(UUID chunkId, String title) {
        return new SourceEvidence(
            UUID.randomUUID(), UUID.randomUUID(), chunkId, title, "checksum", null, 0, 10
        );
    }
}
