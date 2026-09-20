package dev.codexofrealms.realm.infrastructure;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.application.identity.ExternalIdentity;
import dev.codexofrealms.realm.application.port.UserRepository;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserJdbcRepository implements UserRepository {

    private static final String EMAIL = "email";

    private final JdbcClient jdbcClient;

    public UserJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public AuthenticatedUser synchronize(ExternalIdentity identity) {
        return jdbcClient.sql("""
                INSERT INTO codex_user (id, issuer, subject, display_name, email, email_verified)
                VALUES (:id, :issuer, :subject, :displayName, :email, :emailVerified)
                ON CONFLICT (issuer, subject) DO UPDATE
                SET display_name = EXCLUDED.display_name,
                    email = EXCLUDED.email,
                    email_verified = EXCLUDED.email_verified,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id, issuer, subject, display_name, email, email_verified
                """)
            .param("id", UUID.randomUUID())
            .param("issuer", identity.issuer())
            .param("subject", identity.subject())
            .param("displayName", identity.displayName())
            .param(EMAIL, identity.email(), Types.VARCHAR)
            .param("emailVerified", identity.emailVerified())
            .query(UserJdbcRepository::mapUser)
            .single();
    }

    public Optional<AuthenticatedUser> findById(UUID userId) {
        return jdbcClient.sql("""
                SELECT id, issuer, subject, display_name, email, email_verified
                FROM codex_user
                WHERE id = :userId
                """)
            .param("userId", userId)
            .query(UserJdbcRepository::mapUser)
            .optional();
    }

    public Optional<AuthenticatedUser> findByEmail(String email) {
        return jdbcClient.sql("""
                SELECT id, issuer, subject, display_name, email, email_verified
                FROM codex_user
                WHERE email IS NOT NULL
                  AND lower(email) = lower(:email)
                ORDER BY created_at
                LIMIT 1
                """)
            .param(EMAIL, email)
            .query(UserJdbcRepository::mapUser)
            .optional();
    }

    private static AuthenticatedUser mapUser(java.sql.ResultSet resultSet, int rowNumber)
        throws java.sql.SQLException {
        return new AuthenticatedUser(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("issuer"),
            resultSet.getString("subject"),
            resultSet.getString("display_name"),
            resultSet.getString(EMAIL),
            resultSet.getBoolean("email_verified")
        );
    }
}
