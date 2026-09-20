package dev.codexofrealms.content.application.ingestion;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

@Testcontainers
class SourceJobMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"));

    @Test
    void migratesExistingDataAndLeavesPublishedContentMembershipsAndKeycloakIntact() {
        var datasource =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(datasource).target("6").load().migrate();
        var jdbc = JdbcClient.create(datasource);
        UUID user = UUID.randomUUID(),
                realm = UUID.randomUUID(),
                policy = UUID.randomUUID(),
                doc = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO codex_user(id,issuer,subject,display_name,email)"
                            + " VALUES(:id,'issuer','owner','Owner','owner@example.local')")
                .param("id", user)
                .update();
        jdbc.sql("INSERT INTO realm(id,name,created_by) VALUES(:id,'Existing realm',:user)")
                .param("id", realm)
                .param("user", user)
                .update();
        jdbc.sql(
                        "INSERT INTO realm_membership(id,realm_id,user_id,role)"
                            + " VALUES(gen_random_uuid(),:realm,:user,'OWNER')")
                .param("realm", realm)
                .param("user", user)
                .update();
        jdbc.sql(
                        "INSERT INTO access_policy(id,realm_id,classification,name)"
                            + " VALUES(:id,:realm,'PUBLIC','Public')")
                .param("id", policy)
                .param("realm", realm)
                .update();
        jdbc.sql(
                        "INSERT INTO source_document(id,realm_id,title,created_by)"
                            + " VALUES(:id,:realm,'Existing source',:user)")
                .param("id", doc)
                .param("realm", realm)
                .param("user", user)
                .update();
        for (int number = 1; number <= 2; number++) {
            jdbc.sql(
                            """
                            INSERT INTO document_version(id,realm_id,document_id,version_number,checksum_sha256,original_filename,
                                media_type,language,storage_key,processing_status,access_policy_id,pipeline_fingerprint,created_by,active)
                            VALUES(gen_random_uuid(),:realm,:doc,:number,repeat('a',64),'source.md','text/markdown','es',:key,:state,:policy,repeat('b',64),:user,:active)
                            """)
                    .param("realm", realm)
                    .param("doc", doc)
                    .param("number", number)
                    .param("key", "original-" + number)
                    .param("state", number == 1 ? "READY" : "PROCESSING")
                    .param("policy", policy)
                    .param("user", user)
                    .param("active", number == 1)
                    .update();
        }
        jdbc.sql("CREATE SCHEMA keycloak").update();
        jdbc.sql("CREATE TABLE keycloak.migration_sentinel(value TEXT)").update();
        jdbc.sql("INSERT INTO keycloak.migration_sentinel VALUES('preserve realm configuration')")
                .update();
        Flyway.configure().dataSource(datasource).load().migrate();
        assertThat(
                        jdbc.sql("SELECT email_verified FROM codex_user WHERE id=:id")
                                .param("id", user)
                                .query(Boolean.class)
                                .single())
                .isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM document_version").query(Integer.class).single())
                .isEqualTo(2);
        assertThat(
                        jdbc.sql(
                                        "SELECT version_number FROM document_version WHERE active"
                                            + " AND processing_status='READY'")
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT state FROM source_job").query(String.class).single())
                .isEqualTo("FAILED");
        assertThat(
                        jdbc.sql("SELECT role FROM realm_membership WHERE active")
                                .query(String.class)
                                .single())
                .isEqualTo("OWNER");
        assertThat(
                        jdbc.sql("SELECT value FROM keycloak.migration_sentinel")
                                .query(String.class)
                                .single())
                .isEqualTo("preserve realm configuration");
    }
}
