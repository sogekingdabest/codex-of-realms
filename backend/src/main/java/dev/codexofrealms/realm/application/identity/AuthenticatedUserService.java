package dev.codexofrealms.realm.application.identity;

import dev.codexofrealms.realm.application.port.RealmRepository;
import dev.codexofrealms.realm.application.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;
    private final RealmRepository realmRepository;

    public AuthenticatedUserService(
        UserRepository userRepository,
        RealmRepository realmRepository
    ) {
        this.userRepository = userRepository;
        this.realmRepository = realmRepository;
    }

    @Transactional
    public AuthenticatedUser synchronize(ExternalIdentity identity) {
        AuthenticatedUser user = userRepository.synchronize(identity);
        realmRepository.acceptPendingInvitations(user.id(), user.email());
        return user;
    }
}
