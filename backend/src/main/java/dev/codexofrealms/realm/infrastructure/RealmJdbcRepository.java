package dev.codexofrealms.realm.infrastructure;

import dev.codexofrealms.realm.application.AccessPolicyView;
import dev.codexofrealms.realm.application.MembershipView;
import dev.codexofrealms.realm.application.RealmSummary;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RealmJdbcRepository {

    private final JdbcClient jdbcClient;

    public RealmJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void createRealm(UUID realmId, String name, UUID creatorUserId) {
        jdbcClient.sql("""
                INSERT INTO realm (id, name, created_by)
                VALUES (:realmId, :name, :creatorUserId)
                """)
            .param("realmId", realmId)
            .param("name", name)
            .param("creatorUserId", creatorUserId)
            .update();
    }

    public void createOwnerMembership(UUID membershipId, UUID realmId, UUID userId) {
        jdbcClient.sql("""
                INSERT INTO realm_membership (id, realm_id, user_id, role)
                VALUES (:membershipId, :realmId, :userId, 'OWNER')
                """)
            .param("membershipId", membershipId)
            .param("realmId", realmId)
            .param("userId", userId)
            .update();
    }

    public List<RealmSummary> findActiveRealmsForUser(UUID userId) {
        return jdbcClient.sql("""
                SELECT r.id, r.name, m.role
                FROM realm r
                JOIN realm_membership m ON m.realm_id = r.id
                WHERE m.user_id = :userId
                  AND m.active
                  AND r.active
                ORDER BY lower(r.name), r.id
                """)
            .param("userId", userId)
            .query(RealmJdbcRepository::mapRealm)
            .list();
    }

    public Optional<RealmSummary> findActiveRealmForMember(UUID realmId, UUID userId) {
        return jdbcClient.sql("""
                SELECT r.id, r.name, m.role
                FROM realm r
                JOIN realm_membership m ON m.realm_id = r.id
                WHERE r.id = :realmId
                  AND m.user_id = :userId
                  AND m.active
                  AND r.active
                """)
            .param("realmId", realmId)
            .param("userId", userId)
            .query(RealmJdbcRepository::mapRealm)
            .optional();
    }

    public boolean isActiveOwner(UUID realmId, UUID userId) {
        return hasRole(realmId, userId, "m.role = 'OWNER'");
    }

    public boolean isActiveEditor(UUID realmId, UUID userId) {
        return hasRole(realmId, userId, "m.role IN ('OWNER', 'EDITOR')");
    }

    public void lockRealm(UUID realmId) {
        jdbcClient.sql("""
                SELECT id
                FROM realm
                WHERE id = :realmId
                FOR UPDATE
                """)
            .param("realmId", realmId)
            .query(UUID.class)
            .optional()
            .orElseThrow();
    }

    private boolean hasRole(UUID realmId, UUID userId, String rolePredicate) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM realm r
                JOIN realm_membership m ON m.realm_id = r.id
                WHERE r.id = :realmId
                  AND m.user_id = :userId
                  AND m.active
                  AND r.active
                  AND %s
            )
            """.formatted(rolePredicate);

        return Boolean.TRUE.equals(jdbcClient.sql(sql)
            .param("realmId", realmId)
            .param("userId", userId)
            .query(Boolean.class)
            .single());
    }

    public Optional<MembershipView> findActiveMembership(UUID realmId, UUID userId) {
        return jdbcClient.sql("""
                SELECT m.user_id, u.display_name, m.role
                FROM realm_membership m
                JOIN codex_user u ON u.id = m.user_id
                WHERE m.realm_id = :realmId
                  AND m.user_id = :userId
                  AND m.active
                """)
            .param("realmId", realmId)
            .param("userId", userId)
            .query(RealmJdbcRepository::mapMembership)
            .optional();
    }

    public MembershipView upsertMembership(UUID realmId, UUID userId, RealmRole role) {
        jdbcClient.sql("""
                INSERT INTO realm_membership (id, realm_id, user_id, role)
                VALUES (:membershipId, :realmId, :userId, :role)
                ON CONFLICT (realm_id, user_id) DO UPDATE
                SET role = EXCLUDED.role,
                    active = true,
                    updated_at = CURRENT_TIMESTAMP
                """)
            .param("membershipId", UUID.randomUUID())
            .param("realmId", realmId)
            .param("userId", userId)
            .param("role", role.name())
            .update();

        return findActiveMembership(realmId, userId).orElseThrow();
    }

    public int countActiveOwners(UUID realmId) {
        return jdbcClient.sql("""
                SELECT count(*)
                FROM realm_membership
                WHERE realm_id = :realmId
                  AND role = 'OWNER'
                  AND active
                """)
            .param("realmId", realmId)
            .query(Integer.class)
            .single();
    }

    public boolean deactivateMembership(UUID realmId, UUID userId) {
        return jdbcClient.sql("""
                UPDATE realm_membership
                SET active = false,
                    updated_at = CURRENT_TIMESTAMP
                WHERE realm_id = :realmId
                  AND user_id = :userId
                  AND active
                """)
            .param("realmId", realmId)
            .param("userId", userId)
            .update() == 1;
    }

    public void revokeAllGrants(UUID realmId, UUID userId) {
        jdbcClient.sql("""
                DELETE FROM access_grant g
                USING realm_membership m
                WHERE g.realm_id = :realmId
                  AND g.membership_id = m.id
                  AND m.realm_id = :realmId
                  AND m.user_id = :userId
                """)
            .param("realmId", realmId)
            .param("userId", userId)
            .update();
    }

    public AccessPolicyView createAccessPolicy(
        UUID policyId,
        UUID realmId,
        AccessClassification classification
    ) {
        return jdbcClient.sql("""
                INSERT INTO access_policy (id, realm_id, classification)
                VALUES (:policyId, :realmId, :classification)
                RETURNING id, realm_id, classification
                """)
            .param("policyId", policyId)
            .param("realmId", realmId)
            .param("classification", classification.name())
            .query(RealmJdbcRepository::mapPolicy)
            .single();
    }

    public Optional<AccessPolicyView> findPolicyForEditor(
        UUID realmId,
        UUID policyId,
        UUID userId
    ) {
        return jdbcClient.sql("""
                SELECT p.id, p.realm_id, p.classification
                FROM access_policy p
                JOIN realm r ON r.id = p.realm_id
                JOIN realm_membership m
                  ON m.realm_id = p.realm_id
                 AND m.user_id = :userId
                 AND m.active
                WHERE p.id = :policyId
                  AND p.realm_id = :realmId
                  AND p.active
                  AND r.active
                  AND m.role IN ('OWNER', 'EDITOR')
                """)
            .param("realmId", realmId)
            .param("policyId", policyId)
            .param("userId", userId)
            .query(RealmJdbcRepository::mapPolicy)
            .optional();
    }

    public Optional<AccessPolicyView> findAccessiblePolicy(
        UUID realmId,
        UUID policyId,
        UUID userId
    ) {
        return jdbcClient.sql("""
                SELECT p.id, p.realm_id, p.classification
                FROM access_policy p
                JOIN realm r ON r.id = p.realm_id
                JOIN realm_membership m
                  ON m.realm_id = p.realm_id
                 AND m.user_id = :userId
                 AND m.active
                WHERE p.id = :policyId
                  AND p.realm_id = :realmId
                  AND p.active
                  AND r.active
                  AND (
                    m.role IN ('OWNER', 'EDITOR')
                    OR p.classification = 'PUBLIC'
                    OR (
                      p.classification = 'SPOILER'
                      AND EXISTS (
                        SELECT 1
                        FROM access_grant g
                        WHERE g.realm_id = p.realm_id
                          AND g.policy_id = p.id
                          AND g.membership_id = m.id
                      )
                    )
                  )
                """)
            .param("realmId", realmId)
            .param("policyId", policyId)
            .param("userId", userId)
            .query(RealmJdbcRepository::mapPolicy)
            .optional();
    }

    public List<AccessPolicyView> findAccessiblePolicies(UUID realmId, UUID userId) {
        return jdbcClient.sql("""
                SELECT p.id, p.realm_id, p.classification
                FROM access_policy p
                JOIN realm r ON r.id = p.realm_id
                JOIN realm_membership m
                  ON m.realm_id = p.realm_id
                 AND m.user_id = :userId
                 AND m.active
                WHERE p.realm_id = :realmId
                  AND p.active
                  AND r.active
                  AND (
                    m.role IN ('OWNER', 'EDITOR')
                    OR p.classification = 'PUBLIC'
                    OR (
                      p.classification = 'SPOILER'
                      AND EXISTS (
                        SELECT 1
                        FROM access_grant g
                        WHERE g.realm_id = p.realm_id
                          AND g.policy_id = p.id
                          AND g.membership_id = m.id
                      )
                    )
                  )
                ORDER BY p.classification, p.id
                """)
            .param("realmId", realmId)
            .param("userId", userId)
            .query(RealmJdbcRepository::mapPolicy)
            .list();
    }

    public void grantPolicy(UUID realmId, UUID policyId, UUID membershipId) {
        jdbcClient.sql("""
                INSERT INTO access_grant (realm_id, policy_id, membership_id)
                VALUES (:realmId, :policyId, :membershipId)
                ON CONFLICT DO NOTHING
                """)
            .param("realmId", realmId)
            .param("policyId", policyId)
            .param("membershipId", membershipId)
            .update();
    }

    public void revokePolicy(UUID realmId, UUID policyId, UUID membershipId) {
        jdbcClient.sql("""
                DELETE FROM access_grant
                WHERE realm_id = :realmId
                  AND policy_id = :policyId
                  AND membership_id = :membershipId
                """)
            .param("realmId", realmId)
            .param("policyId", policyId)
            .param("membershipId", membershipId)
            .update();
    }

    public Optional<UUID> findActiveMembershipId(UUID realmId, UUID userId) {
        return jdbcClient.sql("""
                SELECT id
                FROM realm_membership
                WHERE realm_id = :realmId
                  AND user_id = :userId
                  AND active
                """)
            .param("realmId", realmId)
            .param("userId", userId)
            .query(UUID.class)
            .optional();
    }

    private static RealmSummary mapRealm(java.sql.ResultSet resultSet, int rowNumber)
        throws java.sql.SQLException {
        return new RealmSummary(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("name"),
            RealmRole.valueOf(resultSet.getString("role"))
        );
    }

    private static MembershipView mapMembership(java.sql.ResultSet resultSet, int rowNumber)
        throws java.sql.SQLException {
        return new MembershipView(
            resultSet.getObject("user_id", UUID.class),
            resultSet.getString("display_name"),
            RealmRole.valueOf(resultSet.getString("role"))
        );
    }

    private static AccessPolicyView mapPolicy(java.sql.ResultSet resultSet, int rowNumber)
        throws java.sql.SQLException {
        return new AccessPolicyView(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("realm_id", UUID.class),
            AccessClassification.valueOf(resultSet.getString("classification"))
        );
    }
}
