package dev.codexofrealms.realm.application.port;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.application.identity.ExternalIdentity;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    AuthenticatedUser synchronize(ExternalIdentity identity);

    Optional<AuthenticatedUser> findById(UUID userId);

    Optional<AuthenticatedUser> findByEmail(String email);
}
