package dev.codexofrealms.realm.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.codexofrealms.realm.application.port.RealmRepository;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealmLifecycleServiceTest {

    private final RealmRepository realmRepository = mock(RealmRepository.class);
    private final RealmLifecycleService service = new RealmLifecycleService(realmRepository);

    @Test
    void createsRealmWithOwnerAndInitialPolicies() {
        UUID userId = UUID.randomUUID();

        RealmSummary realm = service.createRealm(userId, "  La Frontera  ");

        assertThat(realm.name()).isEqualTo("La Frontera");
        assertThat(realm.role()).isEqualTo(RealmRole.OWNER);
        verify(realmRepository).createRealm(realm.id(), "La Frontera", userId);
        verify(realmRepository).createOwnerMembership(any(UUID.class), eq(realm.id()), eq(userId));
        verify(realmRepository).createAccessPolicy(
            any(UUID.class), eq(realm.id()), eq(AccessClassification.PUBLIC),
            eq("Público"), any(String.class)
        );
        verify(realmRepository).createAccessPolicy(
            any(UUID.class), eq(realm.id()), eq(AccessClassification.GM_ONLY),
            eq("Solo dirección"), any(String.class)
        );
    }
}
