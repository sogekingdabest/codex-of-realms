package dev.codexofrealms.content.application.source;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.content.application.port.SourceRepository;
import dev.codexofrealms.realm.RealmAccess;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceMetadataCoordinatorTest {
    private final RealmAccess realmAccess = mock(RealmAccess.class);
    private final SourceRepository repository = mock(SourceRepository.class);
    private final SourceMetadataCoordinator coordinator = new SourceMetadataCoordinator(realmAccess, repository);

    @Test
    void hidesMissingOrInaccessibleSourcesBehindUnavailable() {
        UUID realmId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findAccessibleView(realmId, documentId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coordinator.get(realmId, documentId, userId))
            .isInstanceOfSatisfying(SourceException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo(SourceException.Code.UNAVAILABLE));

        verify(realmAccess).requireMember(realmId, userId);
    }

    @Test
    void chunkInspectionRequiresEditorBeforeRepositoryLookup() {
        UUID realmId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> coordinator.chunks(realmId, documentId, userId))
            .isInstanceOf(SourceException.class);

        verify(realmAccess).requireEditor(realmId, userId);
    }
}
