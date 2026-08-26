package dev.codexofrealms.realm.infrastructure;

import dev.codexofrealms.realm.application.AuthenticatedUser;
import dev.codexofrealms.realm.application.ExternalIdentity;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserJdbcRepository {

    private final JdbcClient jdbcClient;

    public UserJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public AuthenticatedUser synchronize(ExternalIdentity identity) {
        return jdbcClient.sql("""
                INSERT INTO codex_user (id, issuer, subject, display_name, email)
                VALUES (:id, :issuer, :subject, :displayName, :email)
                ON CONFLICT (issuer, subject) DO UPDATE
                SET display_name = EXCLUDED.display_name,
                    email = EXCLUDED.email,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id, issuer, subject, display_name, email
                """)
            .param("id", UUID.randomUUID())
            .param("issuer", identity.issuer())
            .param("subject", identity.subject())
            .param("displayName", identity.displayName())
            .param("email", identity.email(), Types.VARCHAR)
            .query(UserJdbcRepository::mapUser)
            .single();
    }

    public Optional<AuthenticatedUser> findById(UUID userId) {
        return jdbcClient.sql("""
                SELECT id, issuer, subject, display_name, email
                FROM codex_user
                WHERE id = :userId
                """)
            .param("userId", userId)
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
            resultSet.getString("email")
        );
    }
}
