package dev.codexofrealms.realm.application.membership;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.realm.application.access.RealmAuthorizationService;
import dev.codexofrealms.realm.application.port.RealmRepository;
import dev.codexofrealms.realm.application.port.UserRepository;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipServiceTest {

    private final RealmRepository realmRepository = mock(RealmRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RealmAuthorizationService authorizationService =
        mock(RealmAuthorizationService.class);
    private final MembershipService service = new MembershipService(
        realmRepository,
        userRepository,
        authorizationService
    );

    @Test
    void cannotRemoveLastOwner() {
        UUID realmId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(realmRepository.findActiveMembership(realmId, ownerId)).thenReturn(Optional.of(
            new MembershipView(ownerId, "Owner", "owner@example.test", RealmRole.OWNER)
        ));
        when(realmRepository.countActiveOwners(realmId)).thenReturn(1);

        assertThatThrownBy(() -> service.removeMembership(realmId, currentUserId, ownerId))
            .isInstanceOfSatisfying(MembershipException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo(MembershipException.Code.LAST_OWNER)
            );

        verify(authorizationService).requireLockedOwner(realmId, currentUserId);
        verify(realmRepository, never()).revokeAllGrants(realmId, ownerId);
        verify(realmRepository, never()).deactivateMembership(realmId, ownerId);
    }
}
