package dev.codexofrealms.content.infrastructure;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.content.application.SourceChunk;
import dev.codexofrealms.content.application.SourceChunkView;
import dev.codexofrealms.content.application.SourceDocumentView;
import dev.codexofrealms.content.domain.ProcessingStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("java:S1192") // JDBC placeholder and result-column names intentionally mirror the SQL.
public class SourceJdbcRepository {

    private static final String VIEW_SQL = """
        SELECT d.id, d.realm_id, d.title, v.id version_id, v.version_number,
               v.checksum_sha256, v.original_filename, v.media_type, v.language,
               v.processing_status, v.access_policy_id, v.embedding_provider,
               v.embedding_model, v.embedding_dimension, d.created_at,
               (SELECT count(*) FROM lore_chunk c WHERE c.document_version_id=v.id) chunk_count
        FROM source_document d JOIN document_version v ON v.document_id=d.id AND v.realm_id=d.realm_id
        """;
    private static final String ACCESSIBLE_VIEW_SQL = VIEW_SQL + """
        JOIN access_policy p
          ON p.realm_id=v.realm_id AND p.id=v.access_policy_id
        JOIN realm r
          ON r.id=d.realm_id
        JOIN realm_membership m
          ON m.realm_id=d.realm_id AND m.active
        """;

    private final JdbcClient jdbc;

    public SourceJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void createDocument(UUID id, UUID realmId, String title, UUID userId) {
        jdbc.sql("""
                INSERT INTO source_document (id, realm_id, title, created_by)
                VALUES (:id, :realmId, :title, :userId)
                """)
            .param("id", id).param("realmId", realmId).param("title", title)
            .param("userId", userId).update();
    }

    public SourceVersionRecord createVersion(
        UUID realmId,
        UUID userId,
        SourceVersionRecord version
    ) {
        jdbc.sql("""
                INSERT INTO document_version (
                    id, realm_id, document_id, version_number, checksum_sha256,
                    original_filename, media_type, language, storage_key,
                    processing_status, access_policy_id, pipeline_fingerprint, created_by
                ) VALUES (
                    :versionId, :realmId, :documentId, :versionNumber, :checksum,
                    :filename, :mediaType, :language, :storageKey,
                    'RECEIVED', :policyId, :fingerprint, :userId
                )
                """)
            .param("versionId", version.versionId()).param("realmId", realmId)
            .param("documentId", version.documentId()).param("versionNumber", version.versionNumber())
            .param("checksum", version.checksum()).param("filename", version.originalFilename())
            .param("mediaType", version.mediaType()).param("language", version.language())
            .param("storageKey", version.storageKey()).param("policyId", version.accessPolicyId())
            .param("fingerprint", version.pipelineFingerprint()).param("userId", userId).update();
        return version;
    }

    public int nextVersionNumber(UUID realmId, UUID documentId) {
        jdbc.sql("SELECT id FROM source_document WHERE realm_id=:realmId AND id=:documentId AND active FOR UPDATE")
            .param("realmId", realmId).param("documentId", documentId)
            .query(UUID.class).optional().orElseThrow();
        return jdbc.sql("SELECT COALESCE(max(version_number), 0) + 1 FROM document_version WHERE realm_id=:realmId AND document_id=:documentId")
            .param("realmId", realmId).param("documentId", documentId)
            .query(Integer.class).single();
    }

    public Optional<SourceVersionRecord> findActiveVersion(UUID realmId, UUID documentId) {
        return jdbc.sql("""
                SELECT d.id document_id, v.id version_id, v.version_number, d.title,
                       v.original_filename, v.media_type, v.language, v.checksum_sha256,
                       v.storage_key, v.access_policy_id, v.pipeline_fingerprint
                FROM source_document d JOIN document_version v ON v.document_id=d.id AND v.realm_id=d.realm_id
                WHERE d.realm_id=:realmId AND d.id=:documentId AND d.active AND v.active
                """)
            .param("realmId", realmId).param("documentId", documentId)
            .query(SourceJdbcRepository::mapVersion).optional();
    }

    public Optional<SourceEvidence> findActiveEvidence(
        UUID realmId,
        UUID accessPolicyId,
        UUID chunkId
    ) {
        return jdbc.sql("""
                SELECT d.id document_id, v.id document_version_id, c.id chunk_id,
                       d.title source_title, v.checksum_sha256, c.heading,
                       c.start_offset, c.end_offset
                FROM lore_chunk c
                JOIN document_version v
                  ON v.realm_id=c.realm_id AND v.id=c.document_version_id
                JOIN source_document d
                  ON d.realm_id=v.realm_id AND d.id=v.document_id
                WHERE c.realm_id=:realmId
                  AND c.id=:chunkId
                  AND v.access_policy_id=:accessPolicyId
                  AND v.active
                  AND v.processing_status='READY'
                  AND d.active
                """)
            .param("realmId", realmId)
            .param("accessPolicyId", accessPolicyId)
            .param("chunkId", chunkId)
            .query((rs, row) -> new SourceEvidence(
                rs.getObject("document_id", UUID.class),
                rs.getObject("document_version_id", UUID.class),
                rs.getObject("chunk_id", UUID.class),
                rs.getString("source_title"),
                rs.getString("checksum_sha256"),
                rs.getString("heading"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset")
            ))
            .optional();
    }

    public List<SourceChunkView> listActiveChunks(UUID realmId, UUID documentId) {
        return jdbc.sql("""
                SELECT c.id, d.id document_id, v.id document_version_id,
                       d.title source_title, c.ordinal, c.heading, c.content,
                       c.start_offset, c.end_offset
                FROM source_document d
                JOIN document_version v
                  ON v.realm_id=d.realm_id AND v.document_id=d.id
                JOIN lore_chunk c
                  ON c.realm_id=v.realm_id AND c.document_version_id=v.id
                WHERE d.realm_id=:realmId
                  AND d.id=:documentId
                  AND d.active
                  AND v.active
                  AND v.processing_status='READY'
                ORDER BY c.ordinal
                """)
            .param("realmId", realmId)
            .param("documentId", documentId)
            .query((rs, row) -> new SourceChunkView(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getObject("document_version_id", UUID.class),
                rs.getString("source_title"),
                rs.getInt("ordinal"),
                rs.getString("heading"),
                rs.getString("content"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset")
            ))
            .list();
    }

    public void setStatus(UUID versionId, ProcessingStatus status, String failureCode) {
        jdbc.sql("""
                UPDATE document_version SET processing_status=:status, failure_code=:failureCode
                WHERE id=:versionId AND NOT active AND processing_status <> 'RETIRED'
                """)
            .param("versionId", versionId).param("status", status.name())
            .param("failureCode", failureCode).update();
    }

    public void activate(
        SourceVersionRecord version, List<SourceChunk> chunks, List<float[]> embeddings,
        String provider, String model
    ) {
        if (chunks.size() != embeddings.size() || chunks.isEmpty()) {
            throw new IllegalArgumentException("Every source chunk requires one embedding.");
        }
        jdbc.sql("SELECT id FROM source_document WHERE id=:documentId AND active FOR UPDATE")
            .param("documentId", version.documentId()).query(UUID.class)
            .optional().orElseThrow();
        boolean superseded = Boolean.TRUE.equals(jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM document_version
                    WHERE document_id=:documentId AND version_number>:versionNumber
                )
                """)
            .param("documentId", version.documentId())
            .param("versionNumber", version.versionNumber())
            .query(Boolean.class).single());
        if (superseded) {
            jdbc.sql("UPDATE document_version SET processing_status='RETIRED' WHERE id=:versionId AND NOT active")
                .param("versionId", version.versionId()).update();
            return;
        }
        int dimension = embeddings.getFirst().length;
        jdbc.sql("""
                UPDATE document_version SET active=false, processing_status='RETIRED'
                WHERE document_id=:documentId AND active
                """).param("documentId", version.documentId()).update();
        for (int index = 0; index < chunks.size(); index++) {
            SourceChunk chunk = chunks.get(index);
            float[] embedding = embeddings.get(index);
            if (embedding.length != dimension) throw new IllegalArgumentException("Embedding dimensions differ.");
            jdbc.sql("""
                    INSERT INTO lore_chunk (
                        id, realm_id, document_version_id, ordinal, heading, content,
                        start_offset, end_offset, embedding
                    ) SELECT gen_random_uuid(), realm_id, id, :ordinal, :heading, :content,
                             :startOffset, :endOffset, CAST(:embedding AS vector)
                      FROM document_version WHERE id=:versionId
                    """)
                .param("versionId", version.versionId()).param("ordinal", chunk.ordinal())
                .param("heading", chunk.heading()).param("content", chunk.content())
                .param("startOffset", chunk.startOffset()).param("endOffset", chunk.endOffset())
                .param("embedding", vectorLiteral(embedding)).update();
        }
        int activated = jdbc.sql("""
                UPDATE document_version
                SET processing_status='READY', active=true, failure_code=NULL,
                    embedding_provider=:provider, embedding_model=:model,
                    embedding_dimension=:dimension, processed_at=CURRENT_TIMESTAMP
                WHERE id=:versionId AND processing_status='PROCESSING'
                """)
            .param("versionId", version.versionId()).param("provider", provider)
            .param("model", model).param("dimension", dimension).update();
        if (activated != 1) throw new IllegalStateException("The version was not ready for activation.");
    }

    public List<SourceDocumentView> listActive(UUID realmId) {
        return jdbc.sql(VIEW_SQL + " WHERE d.realm_id=:realmId AND d.active AND v.active ORDER BY lower(d.title), d.id")
            .param("realmId", realmId).query(SourceJdbcRepository::mapView).list();
    }

    public List<SourceDocumentView> listAccessible(UUID realmId, UUID userId) {
        return jdbc.sql(ACCESSIBLE_VIEW_SQL + """
                WHERE d.realm_id=:realmId
                  AND m.user_id=:userId
                  AND d.active
                  AND v.active
                  AND v.processing_status='READY'
                  AND p.active
                  AND r.active
                  AND (
                    m.role IN ('OWNER', 'EDITOR')
                    OR p.classification='PUBLIC'
                    OR (
                      p.classification='SPOILER'
                      AND EXISTS (
                        SELECT 1 FROM access_grant g
                        WHERE g.realm_id=p.realm_id
                          AND g.policy_id=p.id
                          AND g.membership_id=m.id
                      )
                    )
                  )
                ORDER BY lower(d.title), d.id
                """)
            .param("realmId", realmId)
            .param("userId", userId)
            .query(SourceJdbcRepository::mapView)
            .list();
    }

    public Optional<SourceDocumentView> findActiveView(UUID realmId, UUID documentId) {
        return jdbc.sql(VIEW_SQL + " WHERE d.realm_id=:realmId AND d.id=:documentId AND d.active AND v.active")
            .param("realmId", realmId).param("documentId", documentId)
            .query(SourceJdbcRepository::mapView).optional();
    }

    public Optional<SourceDocumentView> findAccessibleView(
        UUID realmId,
        UUID documentId,
        UUID userId
    ) {
        return jdbc.sql(ACCESSIBLE_VIEW_SQL + """
                WHERE d.realm_id=:realmId
                  AND d.id=:documentId
                  AND m.user_id=:userId
                  AND d.active
                  AND v.active
                  AND v.processing_status='READY'
                  AND p.active
                  AND r.active
                  AND (
                    m.role IN ('OWNER', 'EDITOR')
                    OR p.classification='PUBLIC'
                    OR (
                      p.classification='SPOILER'
                      AND EXISTS (
                        SELECT 1 FROM access_grant g
                        WHERE g.realm_id=p.realm_id
                          AND g.policy_id=p.id
                          AND g.membership_id=m.id
                      )
                    )
                  )
                """)
            .param("realmId", realmId)
            .param("documentId", documentId)
            .param("userId", userId)
            .query(SourceJdbcRepository::mapView)
            .optional();
    }

    public Optional<SourceVersionRecord> findAccessibleVersion(
        UUID realmId,
        UUID documentId,
        UUID versionId,
        UUID userId
    ) {
        return jdbc.sql("""
                SELECT d.id document_id, v.id version_id, v.version_number, d.title,
                       v.original_filename, v.media_type, v.language, v.checksum_sha256,
                       v.storage_key, v.access_policy_id, v.pipeline_fingerprint
                FROM source_document d
                JOIN document_version v
                  ON v.document_id=d.id AND v.realm_id=d.realm_id
                JOIN realm r
                  ON r.id=d.realm_id AND r.active
                JOIN access_policy p
                  ON p.realm_id=v.realm_id AND p.id=v.access_policy_id AND p.active
                JOIN realm_membership m
                  ON m.realm_id=d.realm_id AND m.user_id=:userId AND m.active
                WHERE d.realm_id=:realmId
                  AND d.id=:documentId
                  AND v.id=:versionId
                  AND d.active
                  AND v.active
                  AND v.processing_status='READY'
                  AND (
                    m.role IN ('OWNER', 'EDITOR')
                    OR p.classification='PUBLIC'
                    OR (
                      p.classification='SPOILER'
                      AND EXISTS (
                        SELECT 1 FROM access_grant g
                        WHERE g.realm_id=p.realm_id
                          AND g.policy_id=p.id
                          AND g.membership_id=m.id
                      )
                    )
                  )
                """)
            .param("realmId", realmId)
            .param("documentId", documentId)
            .param("versionId", versionId)
            .param("userId", userId)
            .query(SourceJdbcRepository::mapVersion)
            .optional();
    }

    public List<String> retireDocument(UUID realmId, UUID documentId) {
        List<String> keys = jdbc.sql("SELECT storage_key FROM document_version WHERE realm_id=:realmId AND document_id=:documentId")
            .param("realmId", realmId).param("documentId", documentId).query(String.class).list();
        jdbc.sql("UPDATE source_document SET active=false, updated_at=CURRENT_TIMESTAMP WHERE realm_id=:realmId AND id=:documentId AND active")
            .param("realmId", realmId).param("documentId", documentId).update();
        jdbc.sql("DELETE FROM lore_chunk WHERE realm_id=:realmId AND document_version_id IN (SELECT id FROM document_version WHERE realm_id=:realmId AND document_id=:documentId)")
            .param("realmId", realmId).param("documentId", documentId).update();
        jdbc.sql("UPDATE document_version SET active=false, processing_status='RETIRED' WHERE realm_id=:realmId AND document_id=:documentId")
            .param("realmId", realmId).param("documentId", documentId).update();
        return keys;
    }

    private static SourceVersionRecord mapVersion(ResultSet rs, int row) throws SQLException {
        return new SourceVersionRecord(rs.getObject("document_id", UUID.class), rs.getObject("version_id", UUID.class),
            rs.getInt("version_number"), rs.getString("title"), rs.getString("original_filename"),
            rs.getString("media_type"), rs.getString("language"), rs.getString("checksum_sha256"),
            rs.getString("storage_key"), rs.getObject("access_policy_id", UUID.class), rs.getString("pipeline_fingerprint"));
    }

    private static SourceDocumentView mapView(ResultSet rs, int row) throws SQLException {
        return new SourceDocumentView(rs.getObject("id", UUID.class), rs.getObject("realm_id", UUID.class),
            rs.getString("title"), rs.getObject("version_id", UUID.class), rs.getInt("version_number"),
            rs.getString("checksum_sha256"), rs.getString("original_filename"), rs.getString("media_type"),
            rs.getString("language"), ProcessingStatus.valueOf(rs.getString("processing_status")),
            rs.getObject("access_policy_id", UUID.class), rs.getString("embedding_provider"),
            rs.getString("embedding_model"), (Integer) rs.getObject("embedding_dimension"),
            rs.getInt("chunk_count"), rs.getTimestamp("created_at").toInstant());
    }

    private static String vectorLiteral(float[] values) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (!Float.isFinite(values[i])) throw new IllegalArgumentException("Embedding contains a non-finite value.");
            if (i > 0) value.append(',');
            value.append(values[i]);
        }
        return value.append(']').toString();
    }
}
