package dev.codexofrealms.realm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessPolicyTest {

    @Test
    void privilegedRolesCanReadEveryClassification() {
        for (AccessClassification classification : AccessClassification.values()) {
            AccessPolicy policy = new AccessPolicy(classification);

            assertThat(policy.allows(RealmRole.OWNER, false)).isTrue();
            assertThat(policy.allows(RealmRole.EDITOR, false)).isTrue();
        }
    }

    @Test
    void playersCanReadPublicContentWithoutAGrant() {
        AccessPolicy policy = new AccessPolicy(AccessClassification.PUBLIC);

        assertThat(policy.allows(RealmRole.PLAYER, false)).isTrue();
        assertThat(policy.allows(RealmRole.PLAYER, true)).isTrue();
    }

    @Test
    void playersCanNeverReadGmOnlyContent() {
        AccessPolicy policy = new AccessPolicy(AccessClassification.GM_ONLY);

        assertThat(policy.allows(RealmRole.PLAYER, false)).isFalse();
        assertThat(policy.allows(RealmRole.PLAYER, true)).isFalse();
    }

    @Test
    void playersNeedAMatchingSpoilerGrant() {
        AccessPolicy policy = new AccessPolicy(AccessClassification.SPOILER);

        assertThat(policy.allows(RealmRole.PLAYER, false)).isFalse();
        assertThat(policy.allows(RealmRole.PLAYER, true)).isTrue();
        assertThat(policy.supportsExplicitGrants()).isTrue();
    }
}
