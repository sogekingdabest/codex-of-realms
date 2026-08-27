package dev.codexofrealms.realm;

import dev.codexofrealms.realm.application.AuthenticatedUserService;
import dev.codexofrealms.realm.application.ExternalIdentity;
import dev.codexofrealms.realm.application.RealmService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Public authorization facade for other application modules. */
@Component
public class RealmAccess {

    private final AuthenticatedUserService userService;
    private final RealmService realmService;

    public RealmAccess(AuthenticatedUserService userService, RealmService realmService) {
        this.userService = userService;
        this.realmService = realmService;
    }

    public UUID synchronizeIdentity(
        String issuer,
        String subject,
        String displayName,
        String email
    ) {
        return userService.synchronize(
            new ExternalIdentity(issuer, subject, displayName, email)
        ).id();
    }

    public void requireEditor(UUID realmId, UUID userId) {
        realmService.requireEditorAccess(realmId, userId);
    }

    public void requireMember(UUID realmId, UUID userId) {
        realmService.requireMemberAccess(realmId, userId);
    }

    public void requireEditablePolicy(UUID realmId, UUID policyId, UUID userId) {
        realmService.requireEditablePolicyAccess(realmId, policyId, userId);
    }
}
