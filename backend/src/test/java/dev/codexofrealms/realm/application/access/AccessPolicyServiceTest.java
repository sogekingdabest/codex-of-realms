package dev.codexofrealms.realm.application.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.application.port.AccessPolicyRepository;
import dev.codexofrealms.realm.application.port.MembershipRepository;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessPolicyServiceTest {

    private final AccessPolicyRepository accessPolicyRepository =
        mock(AccessPolicyRepository.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final RealmAuthorizationService authorizationService =
        mock(RealmAuthorizationService.class);
    private final AccessPolicyService service = new AccessPolicyService(
        accessPolicyRepository,
        membershipRepository,
        authorizationService
    );

    @Test
    void grantsSpoilerAccessToActivePlayer() {
        UUID realmId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(authorizationService.requireLockedEditablePolicy(realmId, policyId, editorId))
            .thenReturn(policy(policyId, realmId, AccessClassification.SPOILER));
        when(membershipRepository.findActiveMembership(realmId, playerId)).thenReturn(Optional.of(
            new MembershipView(playerId, "Player", "player@example.test", RealmRole.PLAYER)
        ));
        when(membershipRepository.findActiveMembershipId(realmId, playerId))
            .thenReturn(Optional.of(membershipId));

        service.grantSpoilerAccess(realmId, policyId, editorId, playerId);

        verify(accessPolicyRepository).grantPolicy(realmId, policyId, membershipId);
    }

    @Test
    void rejectsExplicitGrantForNonSpoilerPolicy() {
        UUID realmId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        when(authorizationService.requireLockedEditablePolicy(realmId, policyId, editorId))
            .thenReturn(policy(policyId, realmId, AccessClassification.PUBLIC));

        assertThatThrownBy(() -> service.grantSpoilerAccess(
            realmId, policyId, editorId, UUID.randomUUID()
        )).isInstanceOfSatisfying(AccessPolicyException.class, exception ->
            assertThat(exception.code()).isEqualTo(AccessPolicyException.Code.GRANTS_UNSUPPORTED)
        );
    }

    @Test
    void rejectsExplicitGrantToEditor() {
        UUID realmId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(authorizationService.requireLockedEditablePolicy(realmId, policyId, editorId))
            .thenReturn(policy(policyId, realmId, AccessClassification.SPOILER));
        when(membershipRepository.findActiveMembership(realmId, targetId)).thenReturn(Optional.of(
            new MembershipView(targetId, "Editor", "editor@example.test", RealmRole.EDITOR)
        ));

        assertThatThrownBy(() -> service.grantSpoilerAccess(
            realmId, policyId, editorId, targetId
        )).isInstanceOfSatisfying(AccessPolicyException.class, exception ->
            assertThat(exception.code()).isEqualTo(AccessPolicyException.Code.INVALID_GRANTEE)
        );
    }

    private static AccessPolicyView policy(
        UUID policyId,
        UUID realmId,
        AccessClassification classification
    ) {
        return new AccessPolicyView(policyId, realmId, classification, "Policy", null);
    }
}
