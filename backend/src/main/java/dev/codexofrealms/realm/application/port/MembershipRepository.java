package dev.codexofrealms.realm.application.port;

import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository {

    void createOwnerMembership(UUID membershipId, UUID realmId, UUID userId);

    boolean isActiveOwner(UUID realmId, UUID userId);

    boolean isActiveEditor(UUID realmId, UUID userId);

    Optional<MembershipView> findActiveMembership(UUID realmId, UUID userId);

    List<MembershipView> listActiveMemberships(UUID realmId);

    MembershipView upsertMembership(UUID realmId, UUID userId, RealmRole role);

    int countActiveOwners(UUID realmId);

    boolean deactivateMembership(UUID realmId, UUID userId);

    void revokeAllGrants(UUID realmId, UUID userId);

    Optional<UUID> findActiveMembershipId(UUID realmId, UUID userId);
}
