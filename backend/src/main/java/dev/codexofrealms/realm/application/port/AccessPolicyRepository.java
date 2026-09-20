package dev.codexofrealms.realm.application.port;

import dev.codexofrealms.realm.application.access.AccessPolicyView;
import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.domain.AccessClassification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessPolicyRepository {

    AccessPolicyView createAccessPolicy(
        UUID policyId,
        UUID realmId,
        AccessClassification classification,
        String name,
        String description
    );

    boolean hasActivePolicyName(UUID realmId, String name);

    Optional<AccessPolicyView> findPolicyForEditor(UUID realmId, UUID policyId, UUID userId);

    Optional<AccessPolicyView> findAccessiblePolicy(UUID realmId, UUID policyId, UUID userId);

    List<AccessPolicyView> findAccessiblePolicies(UUID realmId, UUID userId);

    void grantPolicy(UUID realmId, UUID policyId, UUID membershipId);

    void revokePolicy(UUID realmId, UUID policyId, UUID membershipId);

    List<MembershipView> listPolicyGrants(UUID realmId, UUID policyId);
}
