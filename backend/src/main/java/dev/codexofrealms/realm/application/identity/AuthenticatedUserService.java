package dev.codexofrealms.realm.application.identity;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.application.port.InvitationRepository;
import dev.codexofrealms.realm.application.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;

    public AuthenticatedUserService(
        UserRepository userRepository,
        InvitationRepository invitationRepository
    ) {
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
    }

    @Transactional
    public AuthenticatedUser synchronize(ExternalIdentity identity) {
        AuthenticatedUser user = userRepository.synchronize(identity);
        // Trust this authenticated request, never a previously cached verification.
        if (identity.emailVerified()) {
            invitationRepository.acceptPendingInvitations(user.id(), identity.email());
        }
        return user;
    }
}
