package dev.codexofrealms.realm.application.invitation;

import dev.codexofrealms.realm.application.access.RealmAuthorizationService;
import dev.codexofrealms.realm.application.port.InvitationRepository;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.application.port.UserRepository;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RealmAuthorizationService authorizationService;

    public InvitationService(
        InvitationRepository invitationRepository,
        MembershipRepository membershipRepository,
        UserRepository userRepository,
        RealmAuthorizationService authorizationService
    ) {
        this.invitationRepository = invitationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public InvitationView inviteMember(
        UUID realmId,
        UUID currentUserId,
        String requestedEmail,
        RealmRole role
    ) {
        authorizationService.requireLockedOwner(realmId, currentUserId);
        if (role == RealmRole.OWNER) {
            throw new InvitationException(
                InvitationException.Code.INVALID_ROLE,
                "Invitations can only assign EDITOR or PLAYER roles."
            );
        }
        String email = normalizeEmail(requestedEmail);
        if (invitationRepository.hasPendingInvitation(realmId, email)) {
            throw new InvitationException(
                InvitationException.Code.PENDING_EXISTS,
                "A pending invitation already exists for this email."
            );
        }

        var existingUser = userRepository.findByEmail(email);
        if (existingUser
            .flatMap(user -> membershipRepository.findActiveMembership(realmId, user.id()))
            .isPresent()) {
            throw new InvitationException(
                InvitationException.Code.ALREADY_MEMBER,
                "This user is already an active realm member."
            );
        }

        InvitationView invitation = invitationRepository.createInvitation(
            UUID.randomUUID(), realmId, email, role, currentUserId
        );
        return invitationRepository.findInvitation(realmId, invitation.id())
            .orElseThrow(() -> new InvitationException(
                InvitationException.Code.NOT_FOUND,
                "The requested invitation was not found."
            ));
    }

    @Transactional(readOnly = true)
    public List<InvitationView> listInvitations(UUID realmId, UUID currentUserId) {
        authorizationService.requireOwner(realmId, currentUserId);
        return invitationRepository.listInvitations(realmId);
    }

    @Transactional
    public void revokeInvitation(UUID realmId, UUID invitationId, UUID currentUserId) {
        authorizationService.requireLockedOwner(realmId, currentUserId);
        if (!invitationRepository.revokeInvitation(realmId, invitationId)) {
            throw new InvitationException(
                InvitationException.Code.NOT_FOUND,
                "The requested invitation was not found."
            );
        }
    }

    private static String normalizeEmail(String requestedEmail) {
        if (requestedEmail == null) {
            throw new IllegalArgumentException("Invitation email is required.");
        }
        String email = requestedEmail.strip().toLowerCase(Locale.ROOT);
        if (email.length() < 3 || email.length() > 320 || !email.contains("@")) {
            throw new IllegalArgumentException("Invitation email is invalid.");
        }
        return email;
    }
}
