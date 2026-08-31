package dev.codexofrealms.realm.application.access;

import dev.codexofrealms.realm.application.lifecycle.RealmException;
import dev.codexofrealms.realm.application.port.RealmRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RealmAuthorizationService {

    private final RealmRepository realmRepository;

    public RealmAuthorizationService(RealmRepository realmRepository) {
        this.realmRepository = realmRepository;
    }

    @Transactional(readOnly = true)
    public void requireMember(UUID realmId, UUID currentUserId) {
        realmRepository.findActiveRealmForMember(realmId, currentUserId)
            .orElseThrow(RealmException::unavailable);
    }

    @Transactional(readOnly = true)
    public void requireEditor(UUID realmId, UUID currentUserId) {
        if (!realmRepository.isActiveEditor(realmId, currentUserId)) {
            throw RealmException.unavailable();
        }
    }

    @Transactional(readOnly = true)
    public void requireOwner(UUID realmId, UUID currentUserId) {
        if (!realmRepository.isActiveOwner(realmId, currentUserId)) {
            throw RealmException.unavailable();
        }
    }

    @Transactional
    public void requireLockedOwner(UUID realmId, UUID currentUserId) {
        requireOwner(realmId, currentUserId);
        realmRepository.lockRealm(realmId);
        requireOwner(realmId, currentUserId);
    }

    @Transactional
    public void requireLockedEditor(UUID realmId, UUID currentUserId) {
        requireEditor(realmId, currentUserId);
        realmRepository.lockRealm(realmId);
        requireEditor(realmId, currentUserId);
    }

    @Transactional(readOnly = true)
    public AccessPolicyView requireEditablePolicy(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        return realmRepository.findPolicyForEditor(realmId, policyId, currentUserId)
            .orElseThrow(AccessPolicyException::unavailable);
    }

    @Transactional
    public AccessPolicyView requireLockedEditablePolicy(
        UUID realmId,
        UUID policyId,
        UUID currentUserId
    ) {
        requireEditablePolicy(realmId, policyId, currentUserId);
        realmRepository.lockRealm(realmId);
        return requireEditablePolicy(realmId, policyId, currentUserId);
    }
}
