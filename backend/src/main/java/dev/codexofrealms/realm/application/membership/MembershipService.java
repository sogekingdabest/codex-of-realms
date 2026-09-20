package dev.codexofrealms.realm.application.membership;

import dev.codexofrealms.realm.application.access.RealmAuthorizationService;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.application.port.UserRepository;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RealmAuthorizationService authorizationService;

    public MembershipService(
        MembershipRepository membershipRepository,
        UserRepository userRepository,
        RealmAuthorizationService authorizationService
    ) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public MembershipView upsertMembership(
        UUID realmId,
        UUID currentUserId,
        UUID targetUserId,
        RealmRole role
    ) {
        authorizationService.requireLockedOwner(realmId, currentUserId);
        userRepository.findById(targetUserId).orElseThrow(() -> new MembershipException(
            MembershipException.Code.TARGET_USER_UNAVAILABLE,
            "The target user is unavailable."
        ));

        membershipRepository.findActiveMembership(realmId, targetUserId)
            .filter(existing -> existing.role() == RealmRole.OWNER)
            .filter(existing -> role != RealmRole.OWNER)
            .ifPresent(existing -> requireAnotherOwner(realmId));

        return membershipRepository.upsertMembership(realmId, targetUserId, role);
    }

    @Transactional(readOnly = true)
    public List<MembershipView> listMemberships(UUID realmId, UUID currentUserId) {
        authorizationService.requireEditor(realmId, currentUserId);
        return membershipRepository.listActiveMemberships(realmId);
    }

    @Transactional
    public void removeMembership(UUID realmId, UUID currentUserId, UUID targetUserId) {
        authorizationService.requireLockedOwner(realmId, currentUserId);
        MembershipView membership = membershipRepository.findActiveMembership(realmId, targetUserId)
            .orElseThrow(() -> new MembershipException(
                MembershipException.Code.NOT_FOUND,
                "The requested membership was not found."
            ));

        if (membership.role() == RealmRole.OWNER) {
            requireAnotherOwner(realmId);
        }

        membershipRepository.revokeAllGrants(realmId, targetUserId);
        if (!membershipRepository.deactivateMembership(realmId, targetUserId)) {
            throw new MembershipException(
                MembershipException.Code.NOT_FOUND,
                "The requested membership was not found."
            );
        }
    }

    private void requireAnotherOwner(UUID realmId) {
        if (membershipRepository.countActiveOwners(realmId) <= 1) {
            throw new MembershipException(
                MembershipException.Code.LAST_OWNER,
                "A realm must retain at least one active OWNER."
            );
        }
    }
}
