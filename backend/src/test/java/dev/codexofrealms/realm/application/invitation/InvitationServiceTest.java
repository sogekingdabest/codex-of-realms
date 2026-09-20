package dev.codexofrealms.realm.application.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.application.access.RealmAuthorizationService;
import dev.codexofrealms.realm.application.port.InvitationRepository;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.application.port.UserRepository;
import dev.codexofrealms.realm.domain.InvitationStatus;
import dev.codexofrealms.realm.domain.RealmRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationServiceTest {

    private final InvitationRepository invitationRepository = mock(InvitationRepository.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RealmAuthorizationService authorizationService =
        mock(RealmAuthorizationService.class);
    private final InvitationService service = new InvitationService(
        invitationRepository,
        membershipRepository,
        userRepository,
        authorizationService
    );

    @Test
    void normalizesEmailAndKeepsExistingUserPending() {
        UUID realmId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        String email = "player@example.test";
        AuthenticatedUser user = new AuthenticatedUser(
            invitedUserId, "issuer", "subject", "Player", email
        );
        InvitationView pending = invitation(
            invitationId, realmId, email, InvitationStatus.PENDING, null
        );
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(invitationRepository.createInvitation(
            any(UUID.class),
            org.mockito.ArgumentMatchers.eq(realmId),
            org.mockito.ArgumentMatchers.eq(email),
            org.mockito.ArgumentMatchers.eq(RealmRole.PLAYER),
            org.mockito.ArgumentMatchers.eq(currentUserId)
        )).thenReturn(pending);
        when(invitationRepository.findInvitation(realmId, invitationId))
            .thenReturn(Optional.of(pending));

        InvitationView result = service.inviteMember(
            realmId, currentUserId, "  PLAYER@EXAMPLE.TEST  ", RealmRole.PLAYER
        );

        assertThat(result.status()).isEqualTo(InvitationStatus.PENDING);
        verify(invitationRepository).hasPendingInvitation(realmId, email);
        verify(userRepository).findByEmail(email);
        verify(membershipRepository, org.mockito.Mockito.never()).upsertMembership(
            realmId, invitedUserId, RealmRole.PLAYER
        );
        verify(invitationRepository, org.mockito.Mockito.never()).acceptInvitation(invitationId, invitedUserId);
    }

    @Test
    void rejectsDuplicatePendingInvitation() {
        UUID realmId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        when(invitationRepository.hasPendingInvitation(realmId, "player@example.test"))
            .thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember(
            realmId, currentUserId, "Player@Example.Test", RealmRole.EDITOR
        )).isInstanceOfSatisfying(InvitationException.class, exception ->
            assertThat(exception.code()).isEqualTo(InvitationException.Code.PENDING_EXISTS)
        );
    }

    private static InvitationView invitation(
        UUID id,
        UUID realmId,
        String email,
        InvitationStatus status,
        UUID acceptedUserId
    ) {
        return new InvitationView(
            id, realmId, email, RealmRole.PLAYER, status, acceptedUserId, Instant.now()
        );
    }
}
