package dev.codexofrealms.realm.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.realm.application.port.RealmRepository;
import dev.codexofrealms.realm.application.port.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RealmRepository realmRepository = mock(RealmRepository.class);
    private final AuthenticatedUserService service = new AuthenticatedUserService(
        userRepository,
        realmRepository
    );

    @Test
    void acceptsPendingInvitationsAfterSynchronizingIdentity() {
        ExternalIdentity identity = new ExternalIdentity(
            "issuer", "subject", "Player", "player@example.test"
        );
        AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(), "issuer", "subject", "Player", "player@example.test"
        );
        when(userRepository.synchronize(identity)).thenReturn(user);

        assertThat(service.synchronize(identity)).isEqualTo(user);

        verify(realmRepository).acceptPendingInvitations(user.id(), user.email());
    }
}
