package dev.codexofrealms.realm.application.access;

import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.application.port.AccessPolicyRepository;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.AccessPolicy;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessPolicyService {

    private final AccessPolicyRepository accessPolicyRepository;
    private final MembershipRepository membershipRepository;
    private final RealmAuthorizationService authorizationService;

    public AccessPolicyService(
        AccessPolicyRepository accessPolicyRepository,
        MembershipRepository membershipRepository,
        RealmAuthorizationService authorizationService
    ) {
        this.accessPolicyRepository = accessPolicyRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public AccessPolicyView createAccessPolicy(
        UUID realmId,
        UUID currentUserId,
        AccessClassification classification,
        String requestedName,
        String requestedDescription
    ) {
        authorizationService.requireLockedEditor(realmId, currentUserId);
        AccessPolicy policy = new AccessPolicy(classification);
        String baseName = requestedName == null || requestedName.isBlank()
            ? defaultPolicyName(classification)
            : normalizePolicyName(requestedName);
        String name = uniquePolicyName(realmId, baseName);
        String description = normalizeDescription(requestedDescription);

        return accessPolicyRepository.createAccessPolicy(
            UUID.randomUUID(),
            realmId,
            policy.classification(),
            name,
            description
        );
    }

    @Transactional(readOnly = true)
    public List<MembershipView> listPolicyGrants(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        AccessPolicyView policy = authorizationService.requireEditablePolicy(
            realmId,
            policyId,
            currentUserId
        );
        if (policy.classification() != AccessClassification.SPOILER) {
            return List.of();
        }
        return accessPolicyRepository.listPolicyGrants(realmId, policyId);
    }

    @Transactional(readOnly = true)
    public AccessPolicyView getAccessiblePolicy(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        return accessPolicyRepository.findAccessiblePolicy(realmId, policyId, currentUserId)
            .orElseThrow(AccessPolicyException::unavailable);
    }

    @Transactional(readOnly = true)
    public List<AccessPolicyView> listAccessiblePolicies(
        UUID realmId,
        UUID currentUserId
    ) {
        authorizationService.requireMember(realmId, currentUserId);
        return accessPolicyRepository.findAccessiblePolicies(realmId, currentUserId);
    }

    @Transactional
    public void grantSpoilerAccess(
        UUID realmId,
        UUID policyId,
        UUID currentUserId,
        UUID targetUserId
    ) {
        AccessPolicy policy = requireGrantablePolicy(realmId, policyId, currentUserId);
        if (!policy.supportsExplicitGrants()) {
            throw new AccessPolicyException(
                AccessPolicyException.Code.GRANTS_UNSUPPORTED,
                "Explicit grants can only be applied to SPOILER policies."
            );
        }

        MembershipView membership = membershipRepository.findActiveMembership(realmId, targetUserId)
            .orElseThrow(() -> new AccessPolicyException(
                AccessPolicyException.Code.GRANTEE_UNAVAILABLE,
                "The requested grant target is unavailable."
            ));
        if (membership.role() != RealmRole.PLAYER) {
            throw new AccessPolicyException(
                AccessPolicyException.Code.INVALID_GRANTEE,
                "Explicit spoiler grants can only target active PLAYER memberships."
            );
        }

        UUID membershipId = membershipRepository.findActiveMembershipId(realmId, targetUserId)
            .orElseThrow(() -> new AccessPolicyException(
                AccessPolicyException.Code.GRANTEE_UNAVAILABLE,
                "The requested grant target is unavailable."
            ));
        accessPolicyRepository.grantPolicy(realmId, policyId, membershipId);
    }

    @Transactional
    public void revokeSpoilerAccess(
        UUID realmId,
        UUID policyId,
        UUID currentUserId,
        UUID targetUserId
    ) {
        AccessPolicy policy = requireGrantablePolicy(realmId, policyId, currentUserId);
        if (!policy.supportsExplicitGrants()) {
            throw new AccessPolicyException(
                AccessPolicyException.Code.GRANTS_UNSUPPORTED,
                "Explicit grants can only be removed from SPOILER policies."
            );
        }

        UUID membershipId = membershipRepository.findActiveMembershipId(realmId, targetUserId)
            .orElseThrow(() -> new AccessPolicyException(
                AccessPolicyException.Code.GRANTEE_UNAVAILABLE,
                "The requested grant target is unavailable."
            ));
        accessPolicyRepository.revokePolicy(realmId, policyId, membershipId);
    }

    private AccessPolicy requireGrantablePolicy(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        AccessPolicyView policyView = authorizationService.requireLockedEditablePolicy(
            realmId,
            policyId,
            currentUserId
        );
        return new AccessPolicy(policyView.classification());
    }

    private String uniquePolicyName(UUID realmId, String baseName) {
        if (!accessPolicyRepository.hasActivePolicyName(realmId, baseName)) {
            return baseName;
        }
        for (int suffix = 2; suffix <= 999; suffix++) {
            String candidate = baseName + " " + suffix;
            if (candidate.length() <= 120 && !accessPolicyRepository.hasActivePolicyName(realmId, candidate)) {
                return candidate;
            }
        }
        throw new AccessPolicyException(
            AccessPolicyException.Code.NAME_EXHAUSTED,
            "No unique access-policy name could be generated."
        );
    }

    private static String defaultPolicyName(AccessClassification classification) {
        return switch (classification) {
            case PUBLIC -> "Público";
            case GM_ONLY -> "Solo dirección";
            case SPOILER -> "Spoiler";
        };
    }

    private static String normalizePolicyName(String requestedName) {
        String name = requestedName.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 120) {
            throw new IllegalArgumentException("Policy name must contain between 1 and 120 characters.");
        }
        return name;
    }

    private static String normalizeDescription(String requestedDescription) {
        if (requestedDescription == null || requestedDescription.isBlank()) {
            return null;
        }
        String description = requestedDescription.strip().replaceAll("\\s+", " ");
        if (description.length() > 300) {
            throw new IllegalArgumentException("Policy description cannot exceed 300 characters.");
        }
        return description;
    }
}
