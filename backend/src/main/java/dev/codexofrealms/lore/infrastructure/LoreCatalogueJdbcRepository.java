package dev.codexofrealms.lore.infrastructure;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.application.CataloguePromotionView;
import dev.codexofrealms.lore.application.LoreEntityView;
import dev.codexofrealms.lore.application.LoreRelationView;
import dev.codexofrealms.lore.application.port.LoreCatalogueRepository;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.lore.domain.LoreEntity;
import dev.codexofrealms.lore.domain.LoreRelation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("java:S1192") // JDBC placeholder and result-column names intentionally mirror the SQL.
public class LoreCatalogueJdbcRepository implements LoreCatalogueRepository {

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
    private static final String ENTITY_EDITOR_SELECT = ENTITY_COLUMNS + " FROM lore_entity e";
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
    private static final String RELATION_EDITOR_SELECT = RELATION_COLUMNS + """
        FROM lore_relation rel
        JOIN lore_entity source
          ON source.realm_id=rel.realm_id AND source.id=rel.source_entity_id
        JOIN lore_entity target
          ON target.realm_id=rel.realm_id AND target.id=rel.target_entity_id
        """;
    private static final String P_ACCESSIBLE_POLICY = """
        (
            m.role IN ('OWNER', 'EDITOR')
            OR p.classification='PUBLIC'
            OR (p.classification='SPOILER' AND EXISTS (
                SELECT 1 FROM access_grant g
                WHERE g.realm_id=p.realm_id AND g.policy_id=p.id AND g.membership_id=m.id
            ))
        )
        """;
    private static final String RP_ACCESSIBLE_POLICY = """
        (
            m.role IN ('OWNER', 'EDITOR')
            OR rp.classification='PUBLIC'
            OR (rp.classification='SPOILER' AND EXISTS (
                SELECT 1 FROM access_grant g
                WHERE g.realm_id=rp.realm_id AND g.policy_id=rp.id AND g.membership_id=m.id
            ))
        )
        """;
    private static final String SP_ACCESSIBLE_POLICY = """
        (
            m.role IN ('OWNER', 'EDITOR')
            OR sp.classification='PUBLIC'
            OR (sp.classification='SPOILER' AND EXISTS (
                SELECT 1 FROM access_grant g
                WHERE g.realm_id=sp.realm_id AND g.policy_id=sp.id AND g.membership_id=m.id
            ))
        )
        """;
    private static final String TP_ACCESSIBLE_POLICY = """
        (
            m.role IN ('OWNER', 'EDITOR')
            OR tp.classification='PUBLIC'
            OR (tp.classification='SPOILER' AND EXISTS (
                SELECT 1 FROM access_grant g
                WHERE g.realm_id=tp.realm_id AND g.policy_id=tp.id AND g.membership_id=m.id
            ))
        )
        """;
    private static final String ENTITY_EVIDENCE_SQL = """
        SELECT document_id, document_version_id, chunk_id, source_title,
               checksum_sha256, heading, start_offset, end_offset
        FROM lore_entity_source_evidence WHERE entity_id=:ownerId ORDER BY recorded_at, chunk_id
        """;
    private static final String RELATION_EVIDENCE_SQL = """
        SELECT document_id, document_version_id, chunk_id, source_title,
               checksum_sha256, heading, start_offset, end_offset
        FROM lore_relation_source_evidence WHERE relation_id=:ownerId ORDER BY recorded_at, chunk_id
        """;
    private static final String ENTITY_EVIDENCE_INSERT_SQL = """
        INSERT INTO lore_entity_source_evidence (
            realm_id, entity_id, chunk_id, document_id, document_version_id,
            source_title, checksum_sha256, heading, start_offset, end_offset
        ) VALUES (
            :realmId, :ownerId, :chunkId, :documentId, :versionId,
            :sourceTitle, :checksum, :heading, :startOffset, :endOffset
        )
        """;
    private static final String RELATION_EVIDENCE_INSERT_SQL = """
        INSERT INTO lore_relation_source_evidence (
            realm_id, relation_id, chunk_id, document_id, document_version_id,
            source_title, checksum_sha256, heading, start_offset, end_offset
        ) VALUES (
            :realmId, :ownerId, :chunkId, :documentId, :versionId,
            :sourceTitle, :checksum, :heading, :startOffset, :endOffset
        )
        """;

    private final JdbcClient jdbc;

    public LoreCatalogueJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

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
        replaceEntityAliases(realmId, id, entity.aliases());
        replaceEntityEvidence(realmId, id, evidence);
    }

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
            replaceEntityAliases(realmId, id, entity.aliases());
            replaceEntityEvidence(realmId, id, evidence);
        }
        return updated == 1;
    }

    public List<LoreEntityView> listAccessibleEntities(
        UUID realmId,
        UUID userId,
        EntityType type,
        CanonStatus canonStatus
    ) {
        StringBuilder sql = new StringBuilder(ENTITY_SELECT).append("""
             WHERE e.realm_id=:realmId AND e.active AND r.active AND m.active
               AND """).append(P_ACCESSIBLE_POLICY);
        if (type != null) sql.append(" AND e.entity_type=:entityType");
        if (canonStatus != null) sql.append(" AND e.canon_status=:canonStatus");
        sql.append(" ORDER BY lower(e.display_name), e.id");

        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString())
            .param("realmId", realmId)
            .param("userId", userId);
        if (type != null) statement = statement.param("entityType", type.name());
        if (canonStatus != null) statement = statement.param("canonStatus", canonStatus.name());
        return statement.query(LoreCatalogueJdbcRepository::mapEntityRow)
            .list().stream().map(this::toEntityView).toList();
    }

    public Optional<LoreEntityView> findAccessibleEntity(UUID realmId, UUID id, UUID userId) {
        return jdbc.sql(ENTITY_SELECT + """
                 WHERE e.realm_id=:realmId AND e.id=:id AND e.active AND r.active AND m.active
                   AND """ + P_ACCESSIBLE_POLICY)
            .param("realmId", realmId)
            .param("id", id)
            .param("userId", userId)
            .query(LoreCatalogueJdbcRepository::mapEntityRow)
            .optional()
            .map(this::toEntityView);
    }

    public Optional<LoreEntityView> findEntityForEditor(UUID realmId, UUID id) {
        return jdbc.sql(ENTITY_EDITOR_SELECT + " WHERE e.realm_id=:realmId AND e.id=:id AND e.active")
            .param("realmId", realmId)
            .param("id", id)
            .query(LoreCatalogueJdbcRepository::mapEntityRow)
            .optional()
            .map(this::toEntityView);
    }

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

    public void createRelation(
        UUID id,
        UUID realmId,
        LoreRelation relation,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    ) {
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
        replaceRelationEvidence(realmId, id, evidence);
    }

    public boolean updateRelation(
        UUID id,
        UUID realmId,
        String relationType,
        String description,
        UUID accessPolicyId,
        UUID userId,
        List<SourceEvidence> evidence
    ) {
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
        if (updated == 1) replaceRelationEvidence(realmId, id, evidence);
        return updated == 1;
    }

    public List<LoreRelationView> listAccessibleRelations(
        UUID realmId,
        UUID userId,
        UUID entityId,
        CanonStatus canonStatus
    ) {
        StringBuilder sql = new StringBuilder(RELATION_SELECT).append("""
             WHERE rel.realm_id=:realmId AND rel.active
             AND source.active AND target.active AND r.active AND m.active
               AND """).append(RP_ACCESSIBLE_POLICY)
            .append(" AND ").append(SP_ACCESSIBLE_POLICY)
            .append(" AND ").append(TP_ACCESSIBLE_POLICY);
        if (entityId != null) {
            sql.append(" AND (rel.source_entity_id=:entityId OR rel.target_entity_id=:entityId)");
        }
        if (canonStatus != null) sql.append(" AND rel.canon_status=:canonStatus");
        sql.append(" ORDER BY lower(source.display_name), rel.relation_type, lower(target.display_name), rel.id");

        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString())
            .param("realmId", realmId)
            .param("userId", userId);
        if (entityId != null) statement = statement.param("entityId", entityId);
        if (canonStatus != null) statement = statement.param("canonStatus", canonStatus.name());
        return statement.query(LoreCatalogueJdbcRepository::mapRelationRow)
            .list().stream().map(this::toRelationView).toList();
    }

    public Optional<LoreRelationView> findAccessibleRelation(UUID realmId, UUID id, UUID userId) {
        return jdbc.sql(RELATION_SELECT + """
                 WHERE rel.realm_id=:realmId AND rel.id=:id AND rel.active
                 AND source.active AND target.active AND r.active AND m.active
                   AND """ + RP_ACCESSIBLE_POLICY
                + " AND " + SP_ACCESSIBLE_POLICY
                + " AND " + TP_ACCESSIBLE_POLICY)
            .param("realmId", realmId)
            .param("id", id)
            .param("userId", userId)
            .query(LoreCatalogueJdbcRepository::mapRelationRow)
            .optional()
            .map(this::toRelationView);
    }

    public Optional<LoreRelationView> findRelationForEditor(UUID realmId, UUID id) {
        return jdbc.sql(RELATION_EDITOR_SELECT + " WHERE rel.realm_id=:realmId AND rel.id=:id AND rel.active")
            .param("realmId", realmId)
            .param("id", id)
            .query(LoreCatalogueJdbcRepository::mapRelationRow)
            .optional()
            .map(this::toRelationView);
    }

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

    private void replaceEntityAliases(UUID realmId, UUID entityId, List<String> aliases) {
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

    private void replaceEntityEvidence(UUID realmId, UUID entityId, List<SourceEvidence> evidence) {
        jdbc.sql("DELETE FROM lore_entity_source_evidence WHERE realm_id=:realmId AND entity_id=:entityId")
            .param("realmId", realmId).param("entityId", entityId).update();
        for (SourceEvidence item : evidence) {
            insertEvidence(jdbc.sql(ENTITY_EVIDENCE_INSERT_SQL), realmId, entityId, item);
        }
    }

    private void replaceRelationEvidence(UUID realmId, UUID relationId, List<SourceEvidence> evidence) {
        jdbc.sql("DELETE FROM lore_relation_source_evidence WHERE realm_id=:realmId AND relation_id=:relationId")
            .param("realmId", realmId).param("relationId", relationId).update();
        for (SourceEvidence item : evidence) {
            insertEvidence(jdbc.sql(RELATION_EVIDENCE_INSERT_SQL), realmId, relationId, item);
        }
    }

    private void insertEvidence(
        JdbcClient.StatementSpec statement,
        UUID realmId,
        UUID ownerId,
        SourceEvidence evidence
    ) {
        statement
            .param("realmId", realmId).param("ownerId", ownerId)
            .param("chunkId", evidence.chunkId()).param("documentId", evidence.documentId())
            .param("versionId", evidence.documentVersionId()).param("sourceTitle", evidence.sourceTitle())
            .param("checksum", evidence.checksumSha256()).param("heading", evidence.heading())
            .param("startOffset", evidence.startOffset()).param("endOffset", evidence.endOffset())
            .update();
    }

    private LoreEntityView toEntityView(EntityRow row) {
        return new LoreEntityView(
            row.id(), row.realmId(), row.type(), row.displayName(), entityAliases(row.id()),
            row.description(), row.canonStatus(), row.accessPolicyId(), entityEvidence(row.id()),
            row.createdBy(), row.createdAt(), row.updatedBy(), row.updatedAt(),
            row.promotedBy(), row.promotedAt(), entityPromotions(row.id())
        );
    }

    private LoreRelationView toRelationView(RelationRow row) {
        return new LoreRelationView(
            row.id(), row.realmId(), row.sourceEntityId(), row.sourceEntityName(),
            row.targetEntityId(), row.targetEntityName(), row.relationType(), row.description(),
            row.canonStatus(), row.accessPolicyId(), relationEvidence(row.id()),
            row.createdBy(), row.createdAt(), row.updatedBy(), row.updatedAt(),
            row.promotedBy(), row.promotedAt(), relationPromotions(row.id())
        );
    }

    private List<String> entityAliases(UUID entityId) {
        return jdbc.sql("SELECT alias FROM lore_entity_alias WHERE entity_id=:entityId ORDER BY ordinal")
            .param("entityId", entityId).query(String.class).list();
    }

    private List<SourceEvidence> entityEvidence(UUID entityId) {
        return jdbc.sql(ENTITY_EVIDENCE_SQL)
            .param("ownerId", entityId).query(LoreCatalogueJdbcRepository::mapEvidence).list();
    }

    private List<SourceEvidence> relationEvidence(UUID relationId) {
        return jdbc.sql(RELATION_EVIDENCE_SQL)
            .param("ownerId", relationId).query(LoreCatalogueJdbcRepository::mapEvidence).list();
    }

    private List<CataloguePromotionView> entityPromotions(UUID entityId) {
        return jdbc.sql("""
                SELECT id, promoted_by, promoted_at FROM lore_entity_promotion
                WHERE entity_id=:ownerId ORDER BY promoted_at, id
                """)
            .param("ownerId", entityId).query(LoreCatalogueJdbcRepository::mapPromotion).list();
    }

    private List<CataloguePromotionView> relationPromotions(UUID relationId) {
        return jdbc.sql("""
                SELECT id, promoted_by, promoted_at FROM lore_relation_promotion
                WHERE relation_id=:ownerId ORDER BY promoted_at, id
                """)
            .param("ownerId", relationId).query(LoreCatalogueJdbcRepository::mapPromotion).list();
    }

    private static EntityRow mapEntityRow(ResultSet rs, int row) throws SQLException {
        return new EntityRow(
            rs.getObject("id", UUID.class), rs.getObject("realm_id", UUID.class),
            EntityType.valueOf(rs.getString("entity_type")), rs.getString("display_name"),
            rs.getString("description"), CanonStatus.valueOf(rs.getString("canon_status")),
            rs.getObject("access_policy_id", UUID.class), rs.getObject("created_by", UUID.class),
            instant(rs, "created_at"), rs.getObject("updated_by", UUID.class),
            instant(rs, "updated_at"), rs.getObject("promoted_by", UUID.class),
            nullableInstant(rs, "promoted_at")
        );
    }

    private static RelationRow mapRelationRow(ResultSet rs, int row) throws SQLException {
        return new RelationRow(
            rs.getObject("id", UUID.class), rs.getObject("realm_id", UUID.class),
            rs.getObject("source_entity_id", UUID.class), rs.getString("source_name"),
            rs.getObject("target_entity_id", UUID.class), rs.getString("target_name"),
            rs.getString("relation_type"), rs.getString("description"),
            CanonStatus.valueOf(rs.getString("canon_status")),
            rs.getObject("access_policy_id", UUID.class), rs.getObject("created_by", UUID.class),
            instant(rs, "created_at"), rs.getObject("updated_by", UUID.class),
            instant(rs, "updated_at"), rs.getObject("promoted_by", UUID.class),
            nullableInstant(rs, "promoted_at")
        );
    }

    private static SourceEvidence mapEvidence(ResultSet rs, int row) throws SQLException {
        return new SourceEvidence(
            rs.getObject("document_id", UUID.class),
            rs.getObject("document_version_id", UUID.class),
            rs.getObject("chunk_id", UUID.class), rs.getString("source_title"),
            rs.getString("checksum_sha256"), rs.getString("heading"),
            rs.getInt("start_offset"), rs.getInt("end_offset")
        );
    }

    private static CataloguePromotionView mapPromotion(ResultSet rs, int row) throws SQLException {
        return new CataloguePromotionView(
            rs.getObject("id", UUID.class), rs.getObject("promoted_by", UUID.class),
            instant(rs, "promoted_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record EntityRow(
        UUID id, UUID realmId, EntityType type, String displayName, String description,
        CanonStatus canonStatus, UUID accessPolicyId, UUID createdBy, Instant createdAt,
        UUID updatedBy, Instant updatedAt, UUID promotedBy, Instant promotedAt
    ) {
    }

    private record RelationRow(
        UUID id, UUID realmId, UUID sourceEntityId, String sourceEntityName,
        UUID targetEntityId, String targetEntityName, String relationType, String description,
        CanonStatus canonStatus, UUID accessPolicyId, UUID createdBy, Instant createdAt,
        UUID updatedBy, Instant updatedAt, UUID promotedBy, Instant promotedAt
    ) {
    }
}
