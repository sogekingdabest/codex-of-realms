package dev.codexofrealms.realm.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.codexofrealms.realm.application.port.AccessPolicyRepository;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.application.port.RealmLifecycleRepository;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealmLifecycleServiceTest {

    private final RealmLifecycleRepository lifecycleRepository =
        mock(RealmLifecycleRepository.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final AccessPolicyRepository accessPolicyRepository =
        mock(AccessPolicyRepository.class);
    private final RealmLifecycleService service = new RealmLifecycleService(
        lifecycleRepository,
        membershipRepository,
        accessPolicyRepository
    );

    @Test
    void createsRealmWithOwnerAndInitialPolicies() {
        UUID userId = UUID.randomUUID();

        RealmSummary realm = service.createRealm(userId, "  La Frontera  ");

        assertThat(realm.name()).isEqualTo("La Frontera");
        assertThat(realm.role()).isEqualTo(RealmRole.OWNER);
        verify(lifecycleRepository).createRealm(realm.id(), "La Frontera", userId);
        verify(membershipRepository).createOwnerMembership(
            any(UUID.class), eq(realm.id()), eq(userId)
        );
        verify(accessPolicyRepository).createAccessPolicy(
            any(UUID.class), eq(realm.id()), eq(AccessClassification.PUBLIC),
            eq("Público"), any(String.class)
        );
        verify(accessPolicyRepository).createAccessPolicy(
            any(UUID.class), eq(realm.id()), eq(AccessClassification.GM_ONLY),
            eq("Solo dirección"), any(String.class)
        );
    }
}
