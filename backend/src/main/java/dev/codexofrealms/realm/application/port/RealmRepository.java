package dev.codexofrealms.realm.application.port;

import dev.codexofrealms.realm.application.access.AccessPolicyView;
import dev.codexofrealms.realm.application.invitation.InvitationView;
import dev.codexofrealms.realm.application.lifecycle.RealmSummary;
import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RealmRepository {

    void createRealm(UUID realmId, String name, UUID creatorUserId);

    void createOwnerMembership(UUID membershipId, UUID realmId, UUID userId);

    List<RealmSummary> findActiveRealmsForUser(UUID userId);

    Optional<RealmSummary> findActiveRealmForMember(UUID realmId, UUID userId);

    boolean isActiveOwner(UUID realmId, UUID userId);

    boolean isActiveEditor(UUID realmId, UUID userId);

    void lockRealm(UUID realmId);

    Optional<MembershipView> findActiveMembership(UUID realmId, UUID userId);

    List<MembershipView> listActiveMemberships(UUID realmId);

    MembershipView upsertMembership(UUID realmId, UUID userId, RealmRole role);

    int countActiveOwners(UUID realmId);

    boolean deactivateMembership(UUID realmId, UUID userId);

    void revokeAllGrants(UUID realmId, UUID userId);

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

    Optional<UUID> findActiveMembershipId(UUID realmId, UUID userId);

    List<MembershipView> listPolicyGrants(UUID realmId, UUID policyId);

    boolean hasPendingInvitation(UUID realmId, String email);

    InvitationView createInvitation(
        UUID invitationId,
        UUID realmId,
        String email,
        RealmRole role,
        UUID invitedBy
    );

    Optional<InvitationView> findInvitation(UUID realmId, UUID invitationId);

    List<InvitationView> listInvitations(UUID realmId);

    void acceptInvitation(UUID invitationId, UUID userId);

    boolean revokeInvitation(UUID realmId, UUID invitationId);

    void acceptPendingInvitations(UUID userId, String email);
}
