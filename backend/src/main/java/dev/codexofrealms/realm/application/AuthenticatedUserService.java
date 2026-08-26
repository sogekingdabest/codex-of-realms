package dev.codexofrealms.realm.application;

import dev.codexofrealms.realm.infrastructure.UserJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedUserService {

    private final UserJdbcRepository userRepository;

    public AuthenticatedUserService(UserJdbcRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthenticatedUser synchronize(ExternalIdentity identity) {
        return userRepository.synchronize(identity);
    }
}
