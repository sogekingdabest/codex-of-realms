package dev.codexofrealms.lore.infrastructure;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.application.port.LoreRelationRepository;
import dev.codexofrealms.lore.application.relation.LoreRelationView;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.CataloguePromotion;
import dev.codexofrealms.lore.domain.LoreRelation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("java:S1192")
public class LoreRelationJdbcRepository implements LoreRelationRepository {

    private static final String RELATION_COLUMNS = """
        SELECT rel.id, rel.realm_id, rel.source_entity_id, source.display_name source_name,
               rel.target_entity_id, target.display_name target_name, rel.relation_type,
               rel.description, rel.canon_status, rel.access_policy_id,
               rel.created_by, rel.created_at, rel.updated_by, rel.updated_at,
               rel.promoted_by, rel.promoted_at
        """;
    private static final String RELATION_SELECT = RELATION_COLUMNS + """
        FROM lore_relation rel
        JOIN lore_entity source
          ON source.realm_id=rel.realm_id AND source.id=rel.source_entity_id
        JOIN lore_entity target
          ON target.realm_id=rel.realm_id AND target.id=rel.target_entity_id
        JOIN access_policy rp
          ON rp.realm_id=rel.realm_id AND rp.id=rel.access_policy_id AND rp.active
        JOIN access_policy sp
          ON sp.realm_id=source.realm_id AND sp.id=source.access_policy_id AND sp.active
        JOIN access_policy tp
          ON tp.realm_id=target.realm_id AND tp.id=target.access_policy_id AND tp.active
        JOIN realm r ON r.id=rel.realm_id
        JOIN realm_membership m ON m.realm_id=rel.realm_id AND m.user_id=:userId
        """;
    private static final String EDITOR_SELECT = RELATION_COLUMNS + """
        FROM lore_relation rel
        JOIN lore_entity source
          ON source.realm_id=rel.realm_id AND source.id=rel.source_entity_id
        JOIN lore_entity target
          ON target.realm_id=rel.realm_id AND target.id=rel.target_entity_id
        """;
    private static final String RELATION_POLICY = accessiblePolicy("rp");
    private static final String SOURCE_POLICY = accessiblePolicy("sp");
    private static final String TARGET_POLICY = accessiblePolicy("tp");
    private static final String EVIDENCE_SELECT = """
        SELECT document_id, document_version_id, chunk_id, source_title,
               checksum_sha256, heading, start_offset, end_offset
        FROM lore_relation_source_evidence WHERE relation_id=:ownerId ORDER BY recorded_at, chunk_id
        """;
    private static final String EVIDENCE_INSERT = """
        INSERT INTO lore_relation_source_evidence (
            realm_id, relation_id, chunk_id, document_id, document_version_id,
            source_title, checksum_sha256, heading, start_offset, end_offset
        ) VALUES (
            :realmId, :ownerId, :chunkId, :documentId, :versionId,
            :sourceTitle, :checksum, :heading, :startOffset, :endOffset
        )
        """;

    private final JdbcClient jdbc;

    public LoreRelationJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean bothEntitiesActive(UUID realmId, UUID sourceId, UUID targetId) {
        Integer count = jdbc.sql("""
                SELECT count(*)
                FROM lore_entity
                WHERE realm_id=:realmId AND active AND id IN (:sourceId, :targetId)
                """)
            .param("realmId", realmId)
            .param("sourceId", sourceId)
            .param("targetId", targetId)
            .query(Integer.class)
            .single();
        return count == 2;
    }

    @Override
    public CreateResult createRelation(
        UUID id,
        UUID realmId,
        LoreRelation relation,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    ) {
        try {
            jdbc.sql("""
                    INSERT INTO lore_relation (
                        id, realm_id, source_entity_id, target_entity_id, relation_type,
                        description, access_policy_id, created_by, updated_by
                    ) VALUES (
                        :id, :realmId, :sourceId, :targetId, :relationType,
                        :description, :accessPolicyId, :userId, :userId
                    )
                    """)
                .param("id", id)
                .param("realmId", realmId)
                .param("sourceId", relation.sourceEntityId())
                .param("targetId", relation.targetEntityId())
                .param("relationType", relation.relationType())
                .param("description", relation.description())
                .param("accessPolicyId", accessPolicyId)
                .param("userId", userId)
                .update();
            replaceEvidence(realmId, id, evidence);
            return CreateResult.CREATED;
        } catch (DuplicateKeyException exception) {
            return CreateResult.DUPLICATE;
        }
    }

    @Override
    public UpdateResult updateRelation(
        UUID id,
        UUID realmId,
        String relationType,
        String description,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    ) {
        try {
            int updated = jdbc.sql("""
                    UPDATE lore_relation
                    SET relation_type=:relationType,
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
                .param("relationType", relationType)
                .param("description", description)
                .param("accessPolicyId", accessPolicyId)
                .param("userId", userId)
                .update();
            if (updated == 0) return UpdateResult.UNAVAILABLE;
            replaceEvidence(realmId, id, evidence);
            return UpdateResult.UPDATED;
        } catch (DuplicateKeyException exception) {
            return UpdateResult.DUPLICATE;
        }
    }

    @Override
    public List<LoreRelationView> listAccessibleRelations(
        UUID realmId,
        UUID userId,
        UUID entityId,
        CanonStatus canonStatus
    ) {
        StringBuilder sql = new StringBuilder(RELATION_SELECT).append("""
             WHERE rel.realm_id=:realmId AND rel.active
             AND source.active AND target.active AND r.active AND m.active
               AND """).append(RELATION_POLICY)
            .append(" AND ").append(SOURCE_POLICY)
            .append(" AND ").append(TARGET_POLICY);
        if (entityId != null) {
            sql.append(" AND (rel.source_entity_id=:entityId OR rel.target_entity_id=:entityId)");
        }
        if (canonStatus != null) sql.append(" AND rel.canon_status=:canonStatus");
        sql.append(
            " ORDER BY lower(source.display_name), rel.relation_type, lower(target.display_name), rel.id"
        );

        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString())
            .param("realmId", realmId)
            .param("userId", userId);
        if (entityId != null) statement = statement.param("entityId", entityId);
        if (canonStatus != null) statement = statement.param("canonStatus", canonStatus.name());
        return statement.query(LoreRelationJdbcRepository::mapRow)
            .list().stream().map(this::toView).toList();
    }

    @Override
    public Optional<LoreRelationView> findAccessibleRelation(
        UUID realmId,
        UUID id,
        UUID userId
    ) {
        return jdbc.sql(RELATION_SELECT + """
                 WHERE rel.realm_id=:realmId AND rel.id=:id AND rel.active
                 AND source.active AND target.active AND r.active AND m.active
                   AND """ + RELATION_POLICY
                + " AND " + SOURCE_POLICY
                + " AND " + TARGET_POLICY)
            .param("realmId", realmId)
            .param("id", id)
            .param("userId", userId)
            .query(LoreRelationJdbcRepository::mapRow)
            .optional()
            .map(this::toView);
    }

    @Override
    public Optional<LoreRelationView> findRelationForEditor(UUID realmId, UUID id) {
        return jdbc.sql(EDITOR_SELECT + " WHERE rel.realm_id=:realmId AND rel.id=:id AND rel.active")
            .param("realmId", realmId)
            .param("id", id)
            .query(LoreRelationJdbcRepository::mapRow)
            .optional()
            .map(this::toView);
    }

    @Override
    public boolean deactivateRelation(UUID realmId, UUID id, UUID userId) {
        return jdbc.sql("""
                UPDATE lore_relation
                SET active=false, updated_by=:userId, updated_at=CURRENT_TIMESTAMP
                WHERE realm_id=:realmId AND id=:id AND active
                """)
            .param("realmId", realmId)
            .param("id", id)
            .param("userId", userId)
            .update() == 1;
    }

    @Override
    public void promoteRelation(UUID realmId, UUID id, UUID userId) {
        int promoted = jdbc.sql("""
                UPDATE lore_relation
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
                    INSERT INTO lore_relation_promotion (id, realm_id, relation_id, promoted_by)
                    VALUES (:promotionId, :realmId, :relationId, :userId)
                    """)
                .param("promotionId", UUID.randomUUID())
                .param("realmId", realmId)
                .param("relationId", id)
                .param("userId", userId)
                .update();
        }
    }

    private void replaceEvidence(UUID realmId, UUID relationId, List<SourceEvidence> evidence) {
        jdbc.sql(
                "DELETE FROM lore_relation_source_evidence WHERE realm_id=:realmId AND relation_id=:relationId"
            )
            .param("realmId", realmId).param("relationId", relationId).update();
        for (SourceEvidence item : evidence) {
            LoreJdbcMappings.insertEvidence(jdbc.sql(EVIDENCE_INSERT), realmId, relationId, item);
        }
    }

    private LoreRelationView toView(RelationRow row) {
        return new LoreRelationView(
            row.id(), row.realmId(), row.sourceEntityId(), row.sourceEntityName(),
            row.targetEntityId(), row.targetEntityName(), row.relationType(), row.description(),
            row.canonStatus(), row.accessPolicyId(), evidence(row.id()),
            row.createdBy(), row.createdAt(), row.updatedBy(), row.updatedAt(),
            row.promotedBy(), row.promotedAt(), promotions(row.id())
        );
    }

    private List<SourceEvidence> evidence(UUID relationId) {
        return jdbc.sql(EVIDENCE_SELECT)
            .param("ownerId", relationId).query(LoreJdbcMappings::mapEvidence).list();
    }

    private List<CataloguePromotion> promotions(UUID relationId) {
        return jdbc.sql("""
                SELECT id, promoted_by, promoted_at FROM lore_relation_promotion
                WHERE relation_id=:ownerId ORDER BY promoted_at, id
                """)
            .param("ownerId", relationId).query(LoreJdbcMappings::mapPromotion).list();
    }

    private static RelationRow mapRow(ResultSet resultSet, int row) throws SQLException {
        return new RelationRow(
            resultSet.getObject("id", UUID.class), resultSet.getObject("realm_id", UUID.class),
            resultSet.getObject("source_entity_id", UUID.class), resultSet.getString("source_name"),
            resultSet.getObject("target_entity_id", UUID.class), resultSet.getString("target_name"),
            resultSet.getString("relation_type"), resultSet.getString("description"),
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

    private static String accessiblePolicy(String alias) {
        return """
            (
                m.role IN ('OWNER', 'EDITOR')
                OR %1$s.classification='PUBLIC'
                OR (%1$s.classification='SPOILER' AND EXISTS (
                    SELECT 1 FROM access_grant g
                    WHERE g.realm_id=%1$s.realm_id
                      AND g.policy_id=%1$s.id
                      AND g.membership_id=m.id
                ))
            )
            """.formatted(alias);
    }

    private record RelationRow(
        UUID id, UUID realmId, UUID sourceEntityId, String sourceEntityName,
        UUID targetEntityId, String targetEntityName, String relationType, String description,
        CanonStatus canonStatus, UUID accessPolicyId, UUID createdBy, Instant createdAt,
        UUID updatedBy, Instant updatedAt, UUID promotedBy, Instant promotedAt
    ) {
    }
}
