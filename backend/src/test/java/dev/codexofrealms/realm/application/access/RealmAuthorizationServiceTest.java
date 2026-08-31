package dev.codexofrealms.realm.application.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.codexofrealms.realm.application.lifecycle.RealmException;
import dev.codexofrealms.realm.application.port.RealmRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RealmAuthorizationServiceTest {

    private final RealmRepository realmRepository = mock(RealmRepository.class);
    private final RealmAuthorizationService service = new RealmAuthorizationService(realmRepository);

    @Test
    void locksBetweenTwoOwnerChecks() {
        UUID realmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(realmRepository.isActiveOwner(realmId, userId)).thenReturn(true, true);

        service.requireLockedOwner(realmId, userId);

        InOrder order = inOrder(realmRepository);
        order.verify(realmRepository).isActiveOwner(realmId, userId);
        order.verify(realmRepository).lockRealm(realmId);
        order.verify(realmRepository).isActiveOwner(realmId, userId);
    }

    @Test
    void hidesMissingAndUnauthorizedRealmBehindUnavailable() {
        UUID realmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(realmRepository.isActiveEditor(realmId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.requireEditor(realmId, userId))
            .isInstanceOfSatisfying(RealmException.class, exception ->
                assertThat(exception.code()).isEqualTo(RealmException.Code.UNAVAILABLE)
            );
    }
}
