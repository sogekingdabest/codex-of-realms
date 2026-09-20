package dev.codexofrealms.realm.application.lifecycle;

import dev.codexofrealms.realm.application.port.AccessPolicyRepository;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.application.port.RealmLifecycleRepository;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RealmLifecycleService {

    private final RealmLifecycleRepository lifecycleRepository;
    private final MembershipRepository membershipRepository;
    private final AccessPolicyRepository accessPolicyRepository;

    public RealmLifecycleService(
        RealmLifecycleRepository lifecycleRepository,
        MembershipRepository membershipRepository,
        AccessPolicyRepository accessPolicyRepository
    ) {
        this.lifecycleRepository = lifecycleRepository;
        this.membershipRepository = membershipRepository;
        this.accessPolicyRepository = accessPolicyRepository;
    }

    @Transactional
    public RealmSummary createRealm(UUID currentUserId, String requestedName) {
        String name = normalizeRealmName(requestedName);
        UUID realmId = UUID.randomUUID();

        lifecycleRepository.createRealm(realmId, name, currentUserId);
        membershipRepository.createOwnerMembership(UUID.randomUUID(), realmId, currentUserId);
        accessPolicyRepository.createAccessPolicy(
            UUID.randomUUID(), realmId, AccessClassification.PUBLIC,
            "Público", "Conocimiento visible para todos los miembros."
        );
        accessPolicyRepository.createAccessPolicy(
            UUID.randomUUID(), realmId, AccessClassification.GM_ONLY,
            "Solo dirección", "Conocimiento reservado para propietarios y editores."
        );

        return new RealmSummary(realmId, name, RealmRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<RealmSummary> listRealms(UUID currentUserId) {
        return lifecycleRepository.findActiveRealmsForUser(currentUserId);
    }

    @Transactional(readOnly = true)
    public RealmSummary getRealm(UUID realmId, UUID currentUserId) {
        return lifecycleRepository.findActiveRealmForMember(realmId, currentUserId)
            .orElseThrow(RealmException::unavailable);
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
