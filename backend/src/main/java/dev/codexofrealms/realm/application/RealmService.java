package dev.codexofrealms.realm.application;

import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.AccessPolicy;
import dev.codexofrealms.realm.domain.RealmRole;
import dev.codexofrealms.realm.infrastructure.RealmJdbcRepository;
import dev.codexofrealms.realm.infrastructure.UserJdbcRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RealmService {

    private final RealmJdbcRepository realmRepository;
    private final UserJdbcRepository userRepository;

    public RealmService(
        RealmJdbcRepository realmRepository,
        UserJdbcRepository userRepository
    ) {
        this.realmRepository = realmRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RealmSummary createRealm(UUID currentUserId, String requestedName) {
        String name = normalizeRealmName(requestedName);
        UUID realmId = UUID.randomUUID();

        realmRepository.createRealm(realmId, name, currentUserId);
        realmRepository.createOwnerMembership(UUID.randomUUID(), realmId, currentUserId);

        return new RealmSummary(realmId, name, RealmRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<RealmSummary> listRealms(UUID currentUserId) {
        return realmRepository.findActiveRealmsForUser(currentUserId);
    }

    @Transactional(readOnly = true)
    public RealmSummary getRealm(UUID realmId, UUID currentUserId) {
        return realmRepository.findActiveRealmForMember(realmId, currentUserId)
            .orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional
    public MembershipView upsertMembership(
        UUID realmId,
        UUID currentUserId,
        UUID targetUserId,
        RealmRole role
    ) {
        requireLockedOwner(realmId, currentUserId);
        userRepository.findById(targetUserId)
            .orElseThrow(ResourceNotFoundException::new);

        realmRepository.findActiveMembership(realmId, targetUserId)
            .filter(existing -> existing.role() == RealmRole.OWNER)
            .filter(existing -> role != RealmRole.OWNER)
            .ifPresent(existing -> requireAnotherOwner(realmId));

        return realmRepository.upsertMembership(realmId, targetUserId, role);
    }

    @Transactional
    public void removeMembership(UUID realmId, UUID currentUserId, UUID targetUserId) {
        requireLockedOwner(realmId, currentUserId);
        MembershipView membership = realmRepository.findActiveMembership(realmId, targetUserId)
            .orElseThrow(ResourceNotFoundException::new);

        if (membership.role() == RealmRole.OWNER) {
            requireAnotherOwner(realmId);
        }

        realmRepository.revokeAllGrants(realmId, targetUserId);
        if (!realmRepository.deactivateMembership(realmId, targetUserId)) {
            throw new ResourceNotFoundException();
        }
    }

    @Transactional
    public AccessPolicyView createAccessPolicy(
        UUID realmId,
        UUID currentUserId,
        AccessClassification classification
    ) {
        requireLockedEditor(realmId, currentUserId);
        AccessPolicy policy = new AccessPolicy(classification);

        return realmRepository.createAccessPolicy(
            UUID.randomUUID(),
            realmId,
            policy.classification()
        );
    }

    @Transactional(readOnly = true)
    public AccessPolicyView getAccessiblePolicy(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        return realmRepository.findAccessiblePolicy(realmId, policyId, currentUserId)
            .orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public void requireEditorAccess(UUID realmId, UUID currentUserId) {
        requireEditor(realmId, currentUserId);
    }

    @Transactional(readOnly = true)
    public void requireEditablePolicyAccess(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        realmRepository.findPolicyForEditor(realmId, policyId, currentUserId)
            .orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional
    public void grantSpoilerAccess(
        UUID realmId,
        UUID policyId,
        UUID currentUserId,
        UUID targetUserId
    ) {
        AccessPolicyView policyView = requireEditablePolicy(realmId, policyId, currentUserId);
        AccessPolicy policy = new AccessPolicy(policyView.classification());
        if (!policy.supportsExplicitGrants()) {
            throw new DomainConflictException(
                "Explicit grants can only be applied to SPOILER policies."
            );
        }

        MembershipView membership = realmRepository.findActiveMembership(realmId, targetUserId)
            .orElseThrow(ResourceNotFoundException::new);
        if (membership.role() != RealmRole.PLAYER) {
            throw new DomainConflictException(
                "Explicit spoiler grants can only target active PLAYER memberships."
            );
        }

        UUID membershipId = realmRepository.findActiveMembershipId(realmId, targetUserId)
            .orElseThrow(ResourceNotFoundException::new);
        realmRepository.grantPolicy(realmId, policyId, membershipId);
    }

    @Transactional
    public void revokeSpoilerAccess(
        UUID realmId,
        UUID policyId,
        UUID currentUserId,
        UUID targetUserId
    ) {
        AccessPolicyView policyView = requireEditablePolicy(realmId, policyId, currentUserId);
        AccessPolicy policy = new AccessPolicy(policyView.classification());
        if (!policy.supportsExplicitGrants()) {
            throw new DomainConflictException(
                "Explicit grants can only be removed from SPOILER policies."
            );
        }

        UUID membershipId = realmRepository.findActiveMembershipId(realmId, targetUserId)
            .orElseThrow(ResourceNotFoundException::new);
        realmRepository.revokePolicy(realmId, policyId, membershipId);
    }

    private AccessPolicyView requireEditablePolicy(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        realmRepository.findPolicyForEditor(realmId, policyId, currentUserId)
            .orElseThrow(ResourceNotFoundException::new);
        realmRepository.lockRealm(realmId);
        return realmRepository.findPolicyForEditor(realmId, policyId, currentUserId)
            .orElseThrow(ResourceNotFoundException::new);
    }

    private void requireLockedOwner(UUID realmId, UUID currentUserId) {
        requireOwner(realmId, currentUserId);
        realmRepository.lockRealm(realmId);
        requireOwner(realmId, currentUserId);
    }

    private void requireLockedEditor(UUID realmId, UUID currentUserId) {
        requireEditor(realmId, currentUserId);
        realmRepository.lockRealm(realmId);
        requireEditor(realmId, currentUserId);
    }

    private void requireOwner(UUID realmId, UUID currentUserId) {
        if (!realmRepository.isActiveOwner(realmId, currentUserId)) {
            throw new ResourceNotFoundException();
        }
    }

    private void requireEditor(UUID realmId, UUID currentUserId) {
        if (!realmRepository.isActiveEditor(realmId, currentUserId)) {
            throw new ResourceNotFoundException();
        }
    }

    private void requireAnotherOwner(UUID realmId) {
        if (realmRepository.countActiveOwners(realmId) <= 1) {
            throw new DomainConflictException("A realm must retain at least one active OWNER.");
        }
    }

    private static String normalizeRealmName(String requestedName) {
        if (requestedName == null) {
            throw new IllegalArgumentException("Realm name is required.");
        }

        String name = requestedName.trim();
        if (name.isEmpty() || name.length() > 120) {
            throw new IllegalArgumentException(
                "Realm name must contain between 1 and 120 characters."
            );
        }
        return name;
    }
}
