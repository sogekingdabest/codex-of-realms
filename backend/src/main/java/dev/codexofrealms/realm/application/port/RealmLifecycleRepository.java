package dev.codexofrealms.realm.application.port;

import dev.codexofrealms.realm.application.lifecycle.RealmSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RealmLifecycleRepository {

    void createRealm(UUID realmId, String name, UUID creatorUserId);

    List<RealmSummary> findActiveRealmsForUser(UUID userId);

    Optional<RealmSummary> findActiveRealmForMember(UUID realmId, UUID userId);

    void lockRealm(UUID realmId);
}
