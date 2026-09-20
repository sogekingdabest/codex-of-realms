package dev.codexofrealms.realm.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.application.port.InvitationRepository;
import dev.codexofrealms.realm.application.port.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final AuthenticatedUserService service = new AuthenticatedUserService(
        userRepository,
        invitationRepository
    );

    @Test
    void acceptsPendingInvitationsAfterSynchronizingIdentity() {
        ExternalIdentity identity = new ExternalIdentity(
            "issuer", "subject", "Player", "player@example.test", true
        );
        AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(), "issuer", "subject", "Player", "player@example.test"
        );
        when(userRepository.synchronize(identity)).thenReturn(user);

        assertThat(service.synchronize(identity)).isEqualTo(user);

        verify(invitationRepository).acceptPendingInvitations(user.id(), user.email());
    }

    @Test
    void cachedVerificationCannotAcceptAnUnverifiedRequest() {
        ExternalIdentity identity = new ExternalIdentity("issuer", "subject", "Player", "player@example.test", false);
        AuthenticatedUser cached = new AuthenticatedUser(UUID.randomUUID(), "issuer", "subject", "Player", "player@example.test", true);
        when(userRepository.synchronize(identity)).thenReturn(cached);
        service.synchronize(identity);
        org.mockito.Mockito.verifyNoInteractions(invitationRepository);
    }
}
