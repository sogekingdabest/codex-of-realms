package dev.codexofrealms.lore.infrastructure;

import dev.codexofrealms.lore.LoreRetriever;
import dev.codexofrealms.lore.RetrievalQuery;
import dev.codexofrealms.lore.RetrievedEvidence;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PgVectorLoreRetriever implements LoreRetriever {

    private final JdbcClient jdbc;

    PgVectorLoreRetriever(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RetrievedEvidence> retrieve(RetrievalQuery query) {
        float[] embedding = query.embedding();
        validateEmbedding(embedding);
        return jdbc.sql("""
                WITH authorized_chunks AS MATERIALIZED (
                    SELECT c.id AS chunk_id,
                           c.content,
                           c.heading,
                           c.start_offset,
                           c.end_offset,
                           c.embedding,
                           d.id AS document_id,
                           d.title AS source_title,
                           v.id AS version_id,
                           v.version_number,
                           v.original_filename,
                           v.checksum_sha256,
                           p.id AS policy_id,
                           p.classification
                    FROM realm r
                    JOIN realm_membership m
                      ON m.realm_id = r.id
                     AND m.user_id = :userId
                     AND m.active
                    JOIN source_document d
                      ON d.realm_id = r.id
                     AND d.active
                    JOIN document_version v
                      ON v.realm_id = d.realm_id
                     AND v.document_id = d.id
                     AND v.active
                     AND v.processing_status = 'READY'
                     AND v.embedding_provider = :provider
                     AND v.embedding_model = :model
                     AND v.embedding_dimension = :dimension
                    JOIN access_policy p
                      ON p.realm_id = v.realm_id
                     AND p.id = v.access_policy_id
                     AND p.active
                    JOIN lore_chunk c
                      ON c.realm_id = v.realm_id
                     AND c.document_version_id = v.id
                    WHERE r.id = :realmId
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
                ),
                ranked AS (
                    SELECT authorized_chunks.*,
                           embedding <=> CAST(:embedding AS vector) AS distance
                    FROM authorized_chunks
                )
                SELECT row_number() OVER (ORDER BY distance, chunk_id) AS rank,
                       distance,
                       chunk_id,
                       content,
                       heading,
                       start_offset,
                       end_offset,
                       document_id,
                       source_title,
                       version_id,
                       version_number,
                       original_filename,
                       checksum_sha256,
                       policy_id,
                       classification
                FROM ranked
                ORDER BY distance, chunk_id
                LIMIT :limit
                """)
            .param("realmId", query.realmId())
            .param("userId", query.userId())
            .param("provider", query.embeddingDescriptor().provider())
            .param("model", query.embeddingDescriptor().model())
            .param("dimension", embedding.length)
            .param("embedding", vectorLiteral(embedding))
            .param("limit", query.limit())
            .query(PgVectorLoreRetriever::mapEvidence)
            .list();
    }

    private static RetrievedEvidence mapEvidence(ResultSet resultSet, int row)
        throws SQLException {
        double distance = resultSet.getDouble("distance");
        return new RetrievedEvidence(
            resultSet.getInt("rank"),
            distance,
            1.0 - distance,
            resultSet.getObject("chunk_id", UUID.class),
            resultSet.getString("content"),
            resultSet.getString("heading"),
            resultSet.getInt("start_offset"),
            resultSet.getInt("end_offset"),
            resultSet.getObject("document_id", UUID.class),
            resultSet.getObject("version_id", UUID.class),
            resultSet.getInt("version_number"),
            resultSet.getString("source_title"),
            resultSet.getString("original_filename"),
            resultSet.getString("checksum_sha256"),
            resultSet.getObject("policy_id", UUID.class),
            resultSet.getString("classification")
        );
    }

    private static void validateEmbedding(float[] embedding) {
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Query embedding contains a non-finite value.");
            }
        }
    }

    private static String vectorLiteral(float[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) result.append(',');
            result.append(values[index]);
        }
        return result.append(']').toString();
    }
}
