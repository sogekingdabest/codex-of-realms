package dev.codexofrealms.realm;

import dev.codexofrealms.realm.application.access.RealmAuthorizationService;
import dev.codexofrealms.realm.application.identity.AuthenticatedUserService;
import dev.codexofrealms.realm.application.identity.ExternalIdentity;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Public authorization facade for other application modules. */
@Component
public class RealmAccess {

    private final AuthenticatedUserService userService;
    private final RealmAuthorizationService authorizationService;

    public RealmAccess(
        AuthenticatedUserService userService,
        RealmAuthorizationService authorizationService
    ) {
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    public AuthenticatedUser synchronizeIdentity(
        String issuer,
        String subject,
        String displayName,
        String email
    ) {
        return synchronizeIdentity(issuer, subject, displayName, email, false);
    }

    public AuthenticatedUser synchronizeIdentity(
        String issuer, String subject, String displayName, String email, boolean emailVerified
    ) {
        return userService.synchronize(
            new ExternalIdentity(issuer, subject, displayName, email, emailVerified)
        );
    }

    public void requireEditor(UUID realmId, UUID userId) {
        authorizationService.requireEditor(realmId, userId);
    }

    public void requireMember(UUID realmId, UUID userId) {
        authorizationService.requireMember(realmId, userId);
    }

    public void requireEditablePolicy(UUID realmId, UUID policyId, UUID userId) {
        authorizationService.requireEditablePolicy(realmId, policyId, userId);
    }
}
