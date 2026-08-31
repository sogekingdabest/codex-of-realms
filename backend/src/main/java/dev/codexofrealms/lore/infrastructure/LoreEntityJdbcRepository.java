package dev.codexofrealms.lore.infrastructure;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.application.entity.LoreEntityView;
import dev.codexofrealms.lore.application.port.LoreEntityRepository;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.CataloguePromotion;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.lore.domain.LoreEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("java:S1192")
public class LoreEntityJdbcRepository implements LoreEntityRepository {

    private static final String ENTITY_COLUMNS = """
        SELECT e.id, e.realm_id, e.entity_type, e.display_name, e.description,
               e.canon_status, e.access_policy_id, e.created_by, e.created_at,
               e.updated_by, e.updated_at, e.promoted_by, e.promoted_at
        """;
    private static final String ENTITY_SELECT = ENTITY_COLUMNS + """
        FROM lore_entity e
        JOIN access_policy p ON p.realm_id=e.realm_id AND p.id=e.access_policy_id AND p.active
        JOIN realm r ON r.id=e.realm_id
        JOIN realm_membership m ON m.realm_id=e.realm_id AND m.user_id=:userId
        """;
    private static final String EDITOR_SELECT = ENTITY_COLUMNS + " FROM lore_entity e";
    private static final String ACCESSIBLE_POLICY = """
        (
            m.role IN ('OWNER', 'EDITOR')
            OR p.classification='PUBLIC'
            OR (p.classification='SPOILER' AND EXISTS (
                SELECT 1 FROM access_grant g
                WHERE g.realm_id=p.realm_id AND g.policy_id=p.id AND g.membership_id=m.id
            ))
        )
        """;
    private static final String EVIDENCE_SELECT = """
        SELECT document_id, document_version_id, chunk_id, source_title,
               checksum_sha256, heading, start_offset, end_offset
        FROM lore_entity_source_evidence WHERE entity_id=:ownerId ORDER BY recorded_at, chunk_id
        """;
    private static final String EVIDENCE_INSERT = """
        INSERT INTO lore_entity_source_evidence (
            realm_id, entity_id, chunk_id, document_id, document_version_id,
            source_title, checksum_sha256, heading, start_offset, end_offset
        ) VALUES (
            :realmId, :ownerId, :chunkId, :documentId, :versionId,
            :sourceTitle, :checksum, :heading, :startOffset, :endOffset
        )
        """;

    private final JdbcClient jdbc;

    public LoreEntityJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createEntity(
        UUID id,
        UUID realmId,
        LoreEntity entity,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    ) {
        jdbc.sql("""
                INSERT INTO lore_entity (
                    id, realm_id, entity_type, display_name, description,
                    access_policy_id, created_by, updated_by
                ) VALUES (
                    :id, :realmId, :entityType, :displayName, :description,
                    :accessPolicyId, :userId, :userId
                )
                """)
            .param("id", id)
            .param("realmId", realmId)
            .param("entityType", entity.type().name())
            .param("displayName", entity.displayName())
            .param("description", entity.description())
            .param("accessPolicyId", accessPolicyId)
            .param("userId", userId)
            .update();
        replaceAliases(realmId, id, entity.aliases());
        replaceEvidence(realmId, id, evidence);
    }

    @Override
    public boolean updateEntity(
        UUID id,
        UUID realmId,
        LoreEntity entity,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    ) {
        int updated = jdbc.sql("""
                UPDATE lore_entity
                SET entity_type=:entityType,
                    display_name=:displayName,
                    description=:description,
                    access_policy_id=:accessPolicyId,
                    canon_status='PROPOSED',
                    promoted_by=NULL,
                    promoted_at=NULL,
                    updated_by=:userId,
                    updated_at=CURRENT_TIMESTAMP
                WHERE realm_id=:realmId AND id=:id AND active
                """)
            .param("id", id)
            .param("realmId", realmId)
            .param("entityType", entity.type().name())
            .param("displayName", entity.displayName())
            .param("description", entity.description())
            .param("accessPolicyId", accessPolicyId)
            .param("userId", userId)
            .update();
        if (updated == 1) {
            replaceAliases(realmId, id, entity.aliases());
            replaceEvidence(realmId, id, evidence);
        }
        return updated == 1;
    }

    @Override
    public List<LoreEntityView> listAccessibleEntities(
        UUID realmId,
        UUID userId,
        EntityType type,
        CanonStatus canonStatus
    ) {
        StringBuilder sql = new StringBuilder(ENTITY_SELECT).append("""
             WHERE e.realm_id=:realmId AND e.active AND r.active AND m.active
               AND """).append(ACCESSIBLE_POLICY);
        if (type != null) sql.append(" AND e.entity_type=:entityType");
        if (canonStatus != null) sql.append(" AND e.canon_status=:canonStatus");
        sql.append(" ORDER BY lower(e.display_name), e.id");

        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString())
            .param("realmId", realmId)
            .param("userId", userId);
        if (type != null) statement = statement.param("entityType", type.name());
        if (canonStatus != null) statement = statement.param("canonStatus", canonStatus.name());
        return statement.query(LoreEntityJdbcRepository::mapRow)
            .list().stream().map(this::toView).toList();
    }

    @Override
    public Optional<LoreEntityView> findAccessibleEntity(UUID realmId, UUID id, UUID userId) {
        return jdbc.sql(ENTITY_SELECT + """
                 WHERE e.realm_id=:realmId AND e.id=:id AND e.active AND r.active AND m.active
                   AND """ + ACCESSIBLE_POLICY)
            .param("realmId", realmId)
            .param("id", id)
            .param("userId", userId)
            .query(LoreEntityJdbcRepository::mapRow)
            .optional()
            .map(this::toView);
    }

    @Override
    public Optional<LoreEntityView> findEntityForEditor(UUID realmId, UUID id) {
        return jdbc.sql(EDITOR_SELECT + " WHERE e.realm_id=:realmId AND e.id=:id AND e.active")
            .param("realmId", realmId)
            .param("id", id)
            .query(LoreEntityJdbcRepository::mapRow)
            .optional()
            .map(this::toView);
    }

    @Override
    public boolean hasActiveRelations(UUID realmId, UUID entityId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM lore_relation
                    WHERE realm_id=:realmId AND active
                      AND (source_entity_id=:entityId OR target_entity_id=:entityId)
                )
                """)
            .param("realmId", realmId)
            .param("entityId", entityId)
            .query(Boolean.class)
            .single());
    }

    @Override
    public boolean deactivateEntity(UUID realmId, UUID id, UUID userId) {
        return jdbc.sql("""
                UPDATE lore_entity
                SET active=false, updated_by=:userId, updated_at=CURRENT_TIMESTAMP
                WHERE realm_id=:realmId AND id=:id AND active
                """)
            .param("realmId", realmId)
            .param("id", id)
            .param("userId", userId)
            .update() == 1;
    }

    @Override
    public void promoteEntity(UUID realmId, UUID id, UUID userId) {
        int promoted = jdbc.sql("""
                UPDATE lore_entity
                SET canon_status='CANON', promoted_by=:userId,
                    promoted_at=CURRENT_TIMESTAMP, updated_by=:userId,
                    updated_at=CURRENT_TIMESTAMP
                WHERE realm_id=:realmId AND id=:id AND active AND canon_status='PROPOSED'
                """)
            .param("realmId", realmId)
            .param("id", id)
            .param("userId", userId)
            .update();
        if (promoted == 1) {
            jdbc.sql("""
                    INSERT INTO lore_entity_promotion (id, realm_id, entity_id, promoted_by)
                    VALUES (:promotionId, :realmId, :entityId, :userId)
                    """)
                .param("promotionId", UUID.randomUUID())
                .param("realmId", realmId)
                .param("entityId", id)
                .param("userId", userId)
                .update();
        }
    }

    private void replaceAliases(UUID realmId, UUID entityId, List<String> aliases) {
        jdbc.sql("DELETE FROM lore_entity_alias WHERE realm_id=:realmId AND entity_id=:entityId")
            .param("realmId", realmId).param("entityId", entityId).update();
        for (int ordinal = 0; ordinal < aliases.size(); ordinal++) {
            String alias = aliases.get(ordinal);
            jdbc.sql("""
                    INSERT INTO lore_entity_alias (realm_id, entity_id, ordinal, alias, alias_key)
                    VALUES (:realmId, :entityId, :ordinal, :alias, lower(:alias))
                    """)
                .param("realmId", realmId).param("entityId", entityId)
                .param("ordinal", ordinal).param("alias", alias).update();
        }
    }

    private void replaceEvidence(UUID realmId, UUID entityId, List<SourceEvidence> evidence) {
        jdbc.sql(
                "DELETE FROM lore_entity_source_evidence WHERE realm_id=:realmId AND entity_id=:entityId"
            )
            .param("realmId", realmId).param("entityId", entityId).update();
        for (SourceEvidence item : evidence) {
            LoreJdbcMappings.insertEvidence(jdbc.sql(EVIDENCE_INSERT), realmId, entityId, item);
        }
    }

    private LoreEntityView toView(EntityRow row) {
        return new LoreEntityView(
            row.id(), row.realmId(), row.type(), row.displayName(), aliases(row.id()),
            row.description(), row.canonStatus(), row.accessPolicyId(), evidence(row.id()),
            row.createdBy(), row.createdAt(), row.updatedBy(), row.updatedAt(),
            row.promotedBy(), row.promotedAt(), promotions(row.id())
        );
    }

    private List<String> aliases(UUID entityId) {
        return jdbc.sql("SELECT alias FROM lore_entity_alias WHERE entity_id=:entityId ORDER BY ordinal")
            .param("entityId", entityId).query(String.class).list();
    }

    private List<SourceEvidence> evidence(UUID entityId) {
        return jdbc.sql(EVIDENCE_SELECT)
            .param("ownerId", entityId).query(LoreJdbcMappings::mapEvidence).list();
    }

    private List<CataloguePromotion> promotions(UUID entityId) {
        return jdbc.sql("""
                SELECT id, promoted_by, promoted_at FROM lore_entity_promotion
                WHERE entity_id=:ownerId ORDER BY promoted_at, id
                """)
            .param("ownerId", entityId).query(LoreJdbcMappings::mapPromotion).list();
    }

    private static EntityRow mapRow(ResultSet resultSet, int row) throws SQLException {
        return new EntityRow(
            resultSet.getObject("id", UUID.class), resultSet.getObject("realm_id", UUID.class),
            EntityType.valueOf(resultSet.getString("entity_type")),
            resultSet.getString("display_name"), resultSet.getString("description"),
            CanonStatus.valueOf(resultSet.getString("canon_status")),
            resultSet.getObject("access_policy_id", UUID.class),
            resultSet.getObject("created_by", UUID.class),
            LoreJdbcMappings.instant(resultSet, "created_at"),
            resultSet.getObject("updated_by", UUID.class),
            LoreJdbcMappings.instant(resultSet, "updated_at"),
            resultSet.getObject("promoted_by", UUID.class),
            LoreJdbcMappings.nullableInstant(resultSet, "promoted_at")
        );
    }

    private record EntityRow(
        UUID id, UUID realmId, EntityType type, String displayName, String description,
        CanonStatus canonStatus, UUID accessPolicyId, UUID createdBy, Instant createdAt,
        UUID updatedBy, Instant updatedAt, UUID promotedBy, Instant promotedAt
    ) {
    }
}
