package dev.codexofrealms.realm.application.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.codexofrealms.realm.application.lifecycle.RealmException;
import dev.codexofrealms.realm.application.port.AccessPolicyRepository;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.application.port.RealmLifecycleRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RealmAuthorizationServiceTest {

    private final RealmLifecycleRepository lifecycleRepository =
        mock(RealmLifecycleRepository.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final AccessPolicyRepository accessPolicyRepository =
        mock(AccessPolicyRepository.class);
    private final RealmAuthorizationService service = new RealmAuthorizationService(
        lifecycleRepository,
        membershipRepository,
        accessPolicyRepository
    );

    @Test
    void locksBetweenTwoOwnerChecks() {
        UUID realmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(membershipRepository.isActiveOwner(realmId, userId)).thenReturn(true, true);

        service.requireLockedOwner(realmId, userId);

        InOrder order = inOrder(membershipRepository, lifecycleRepository);
        order.verify(membershipRepository).isActiveOwner(realmId, userId);
        order.verify(lifecycleRepository).lockRealm(realmId);
        order.verify(membershipRepository).isActiveOwner(realmId, userId);
    }

    @Test
    void hidesMissingAndUnauthorizedRealmBehindUnavailable() {
        UUID realmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(membershipRepository.isActiveEditor(realmId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.requireEditor(realmId, userId))
            .isInstanceOfSatisfying(RealmException.class, exception ->
                assertThat(exception.code()).isEqualTo(RealmException.Code.UNAVAILABLE)
            );
    }
}
