package dev.codexofrealms.realm.application;

import dev.codexofrealms.realm.infrastructure.UserJdbcRepository;
import dev.codexofrealms.realm.infrastructure.RealmJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedUserService {

    private final UserJdbcRepository userRepository;
    private final RealmJdbcRepository realmRepository;

    public AuthenticatedUserService(
        UserJdbcRepository userRepository,
        RealmJdbcRepository realmRepository
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
