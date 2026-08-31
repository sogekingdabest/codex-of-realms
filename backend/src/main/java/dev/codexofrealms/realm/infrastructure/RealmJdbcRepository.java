package dev.codexofrealms.realm.infrastructure;

import dev.codexofrealms.realm.application.access.AccessPolicyView;
import dev.codexofrealms.realm.application.invitation.InvitationView;
import dev.codexofrealms.realm.application.lifecycle.RealmSummary;
import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.application.port.RealmRepository;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.InvitationStatus;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("java:S1192") // JDBC placeholder and result-column names intentionally mirror the SQL.
public class RealmJdbcRepository implements RealmRepository {

    private static final String ACTIVE_OWNER_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM realm r
            JOIN realm_membership m ON m.realm_id = r.id
            WHERE r.id = :realmId
              AND m.user_id = :userId
              AND m.active
              AND r.active
              AND m.role = 'OWNER'
        )
        """;
    private static final String ACTIVE_EDITOR_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM realm r
            JOIN realm_membership m ON m.realm_id = r.id
            WHERE r.id = :realmId
              AND m.user_id = :userId
              AND m.active
              AND r.active
              AND m.role IN ('OWNER', 'EDITOR')
        )
        """;
    private static final String INVITATION_SQL = """
        SELECT i.id, i.realm_id, i.email, i.role, i.accepted_by, i.created_at,
               CASE
                 WHEN i.accepted_at IS NOT NULL THEN 'ACCEPTED'
                 WHEN i.revoked_at IS NOT NULL THEN 'REVOKED'
                 ELSE 'PENDING'
               END status
        FROM realm_invitation i
        """;

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
        return Boolean.TRUE.equals(jdbcClient.sql(ACTIVE_OWNER_SQL)
            .param("realmId", realmId)
            .param("userId", userId)
            .query(Boolean.class)
            .single());
    }

    public boolean isActiveEditor(UUID realmId, UUID userId) {
        return Boolean.TRUE.equals(jdbcClient.sql(ACTIVE_EDITOR_SQL)
            .param("realmId", realmId)
            .param("userId", userId)
            .query(Boolean.class)
            .single());
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

    public Optional<MembershipView> findActiveMembership(UUID realmId, UUID userId) {
        return jdbcClient.sql("""
                SELECT m.user_id, u.display_name, u.email, m.role
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

    public List<MembershipView> listActiveMemberships(UUID realmId) {
        return jdbcClient.sql("""
                SELECT m.user_id, u.display_name, u.email, m.role
                FROM realm_membership m
                JOIN codex_user u ON u.id = m.user_id
                WHERE m.realm_id = :realmId
                  AND m.active
                ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'EDITOR' THEN 1 ELSE 2 END,
                         lower(u.display_name), m.user_id
                """)
            .param("realmId", realmId)
            .query(RealmJdbcRepository::mapMembership)
            .list();
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
        AccessClassification classification,
        String name,
        String description
    ) {
        return jdbcClient.sql("""
                INSERT INTO access_policy (id, realm_id, classification, name, description)
                VALUES (:policyId, :realmId, :classification, :name, :description)
                RETURNING id, realm_id, classification, name, description
                """)
            .param("policyId", policyId)
            .param("realmId", realmId)
            .param("classification", classification.name())
            .param("name", name)
            .param("description", description, java.sql.Types.VARCHAR)
            .query(RealmJdbcRepository::mapPolicy)
            .single();
    }

    public boolean hasActivePolicyName(UUID realmId, String name) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM access_policy
                    WHERE realm_id = :realmId
                      AND active
                      AND lower(name) = lower(:name)
                )
                """)
            .param("realmId", realmId)
            .param("name", name)
            .query(Boolean.class)
            .single());
    }

    public Optional<AccessPolicyView> findPolicyForEditor(
        UUID realmId,
        UUID policyId,
        UUID userId
    ) {
        return jdbcClient.sql("""
                SELECT p.id, p.realm_id, p.classification, p.name, p.description
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
                SELECT p.id, p.realm_id, p.classification, p.name, p.description
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
                SELECT p.id, p.realm_id, p.classification, p.name, p.description
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
                ORDER BY p.classification, lower(p.name), p.id
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

    public List<MembershipView> listPolicyGrants(UUID realmId, UUID policyId) {
        return jdbcClient.sql("""
                SELECT m.user_id, u.display_name, u.email, m.role
                FROM access_grant g
                JOIN realm_membership m
                  ON m.realm_id = g.realm_id
                 AND m.id = g.membership_id
                 AND m.active
                JOIN codex_user u ON u.id = m.user_id
                WHERE g.realm_id = :realmId
                  AND g.policy_id = :policyId
                ORDER BY lower(u.display_name), m.user_id
                """)
            .param("realmId", realmId)
            .param("policyId", policyId)
            .query(RealmJdbcRepository::mapMembership)
            .list();
    }

    public boolean hasPendingInvitation(UUID realmId, String email) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM realm_invitation
                    WHERE realm_id = :realmId
                      AND lower(email) = lower(:email)
                      AND accepted_at IS NULL
                      AND revoked_at IS NULL
                )
                """)
            .param("realmId", realmId)
            .param("email", email)
            .query(Boolean.class)
            .single());
    }

    public InvitationView createInvitation(
        UUID invitationId,
        UUID realmId,
        String email,
        RealmRole role,
        UUID invitedBy
    ) {
        jdbcClient.sql("""
                INSERT INTO realm_invitation (id, realm_id, email, role, invited_by)
                VALUES (:id, :realmId, :email, :role, :invitedBy)
                """)
            .param("id", invitationId)
            .param("realmId", realmId)
            .param("email", email)
            .param("role", role.name())
            .param("invitedBy", invitedBy)
            .update();
        return findInvitation(realmId, invitationId).orElseThrow();
    }

    public Optional<InvitationView> findInvitation(UUID realmId, UUID invitationId) {
        return jdbcClient.sql(INVITATION_SQL + " WHERE i.realm_id=:realmId AND i.id=:invitationId")
            .param("realmId", realmId)
            .param("invitationId", invitationId)
            .query(RealmJdbcRepository::mapInvitation)
            .optional();
    }

    public List<InvitationView> listInvitations(UUID realmId) {
        return jdbcClient.sql(INVITATION_SQL + " WHERE i.realm_id=:realmId ORDER BY i.created_at DESC, i.id")
            .param("realmId", realmId)
            .query(RealmJdbcRepository::mapInvitation)
            .list();
    }

    public void acceptInvitation(UUID invitationId, UUID userId) {
        jdbcClient.sql("""
                UPDATE realm_invitation
                SET accepted_by=:userId, accepted_at=CURRENT_TIMESTAMP
                WHERE id=:invitationId
                  AND accepted_at IS NULL
                  AND revoked_at IS NULL
                """)
            .param("invitationId", invitationId)
            .param("userId", userId)
            .update();
    }

    public boolean revokeInvitation(UUID realmId, UUID invitationId) {
        return jdbcClient.sql("""
                UPDATE realm_invitation
                SET revoked_at=CURRENT_TIMESTAMP
                WHERE realm_id=:realmId
                  AND id=:invitationId
                  AND accepted_at IS NULL
                  AND revoked_at IS NULL
                """)
            .param("realmId", realmId)
            .param("invitationId", invitationId)
            .update() == 1;
    }

    public void acceptPendingInvitations(UUID userId, String email) {
        if (email == null || email.isBlank()) return;
        jdbcClient.sql("""
                INSERT INTO realm_membership (id, realm_id, user_id, role)
                SELECT gen_random_uuid(), i.realm_id, :userId, i.role
                FROM realm_invitation i
                JOIN realm r ON r.id=i.realm_id AND r.active
                WHERE lower(i.email)=lower(:email)
                  AND i.accepted_at IS NULL
                  AND i.revoked_at IS NULL
                ON CONFLICT (realm_id, user_id) DO UPDATE
                SET active=true,
                    role=CASE
                        WHEN realm_membership.role='OWNER' THEN 'OWNER'
                        WHEN realm_membership.role='EDITOR' OR EXCLUDED.role='EDITOR' THEN 'EDITOR'
                        ELSE 'PLAYER'
                    END,
                    updated_at=CURRENT_TIMESTAMP
                """)
            .param("userId", userId)
            .param("email", email)
            .update();
        jdbcClient.sql("""
                UPDATE realm_invitation
                SET accepted_by=:userId, accepted_at=CURRENT_TIMESTAMP
                WHERE lower(email)=lower(:email)
                  AND accepted_at IS NULL
                  AND revoked_at IS NULL
                """)
            .param("userId", userId)
            .param("email", email)
            .update();
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
            resultSet.getString("email"),
            RealmRole.valueOf(resultSet.getString("role"))
        );
    }

    private static AccessPolicyView mapPolicy(java.sql.ResultSet resultSet, int rowNumber)
        throws java.sql.SQLException {
        return new AccessPolicyView(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("realm_id", UUID.class),
            AccessClassification.valueOf(resultSet.getString("classification")),
            resultSet.getString("name"),
            resultSet.getString("description")
        );
    }

    private static InvitationView mapInvitation(java.sql.ResultSet resultSet, int rowNumber)
        throws java.sql.SQLException {
        return new InvitationView(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("realm_id", UUID.class),
            resultSet.getString("email"),
            RealmRole.valueOf(resultSet.getString("role")),
            InvitationStatus.valueOf(resultSet.getString("status")),
            resultSet.getObject("accepted_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
