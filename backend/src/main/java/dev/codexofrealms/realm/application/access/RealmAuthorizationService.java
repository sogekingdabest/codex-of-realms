package dev.codexofrealms.realm.application.access;

import dev.codexofrealms.realm.application.lifecycle.RealmException;
import dev.codexofrealms.realm.application.port.AccessPolicyRepository;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.application.port.RealmLifecycleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RealmAuthorizationService {

    private final RealmLifecycleRepository lifecycleRepository;
    private final MembershipRepository membershipRepository;
    private final AccessPolicyRepository accessPolicyRepository;

    public RealmAuthorizationService(
        RealmLifecycleRepository lifecycleRepository,
        MembershipRepository membershipRepository,
        AccessPolicyRepository accessPolicyRepository
    ) {
        this.lifecycleRepository = lifecycleRepository;
        this.membershipRepository = membershipRepository;
        this.accessPolicyRepository = accessPolicyRepository;
    }

    @Transactional(readOnly = true)
    public void requireMember(UUID realmId, UUID currentUserId) {
        lifecycleRepository.findActiveRealmForMember(realmId, currentUserId)
            .orElseThrow(RealmException::unavailable);
    }

    @Transactional(readOnly = true)
    public void requireEditor(UUID realmId, UUID currentUserId) {
        if (!membershipRepository.isActiveEditor(realmId, currentUserId)) {
            throw RealmException.unavailable();
        }
    }

    @Transactional(readOnly = true)
    public void requireOwner(UUID realmId, UUID currentUserId) {
        if (!membershipRepository.isActiveOwner(realmId, currentUserId)) {
            throw RealmException.unavailable();
        }
    }

    @Transactional
    public void requireLockedOwner(UUID realmId, UUID currentUserId) {
        requireOwner(realmId, currentUserId);
        lifecycleRepository.lockRealm(realmId);
        requireOwner(realmId, currentUserId);
    }

    @Transactional
    public void requireLockedEditor(UUID realmId, UUID currentUserId) {
        requireEditor(realmId, currentUserId);
        lifecycleRepository.lockRealm(realmId);
        requireEditor(realmId, currentUserId);
    }

    @Transactional(readOnly = true)
    public AccessPolicyView requireEditablePolicy(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        return accessPolicyRepository.findPolicyForEditor(realmId, policyId, currentUserId)
            .orElseThrow(AccessPolicyException::unavailable);
    }

    @Transactional
    public AccessPolicyView requireLockedEditablePolicy(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        requireEditablePolicy(realmId, policyId, currentUserId);
        lifecycleRepository.lockRealm(realmId);
        return requireEditablePolicy(realmId, policyId, currentUserId);
    }
}
