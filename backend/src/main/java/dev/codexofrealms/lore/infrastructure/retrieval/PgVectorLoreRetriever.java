package dev.codexofrealms.lore.infrastructure.retrieval;

import dev.codexofrealms.lore.RetrievedEvidence;
import dev.codexofrealms.lore.application.port.LoreRetriever;
import dev.codexofrealms.lore.application.port.RetrievalQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PgVectorLoreRetriever implements LoreRetriever {

    @org.springframework.beans.factory.annotation.Value("${codex.retrieval.hybrid-enabled:false}")
    private boolean hybridEnabled;

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
                query_terms AS (
                    SELECT tsvector_to_array(to_tsvector('spanish', :question)) AS terms
                ), scored AS (
                    SELECT authorized_chunks.*,
                           embedding <=> CAST(:embedding AS vector) AS distance,
                           CASE WHEN :hybrid THEN to_tsvector('spanish', content) ELSE ''::tsvector END AS document_terms
                    FROM authorized_chunks
                ), lexical_query AS (
                    SELECT CASE WHEN cardinality(terms) = 0 THEN ''::tsquery
                        ELSE to_tsquery('spanish', (SELECT string_agg(quote_literal(t), ' | ') FROM unnest(terms) t)) END AS query
                    FROM query_terms
                ), vector_candidates AS (
                    SELECT chunk_id, row_number() OVER (ORDER BY distance, chunk_id)::int AS vector_rank
                    FROM scored ORDER BY distance, chunk_id LIMIT :limit
                ), lexical_candidates AS (
                    SELECT chunk_id, row_number() OVER (
                        ORDER BY ts_rank_cd(document_terms, query) DESC, chunk_id)::int AS lexical_rank
                    FROM scored CROSS JOIN lexical_query
                    WHERE :hybrid AND document_terms @@ query
                    ORDER BY ts_rank_cd(document_terms, query) DESC, chunk_id LIMIT :limit
                ), candidates AS (
                    SELECT coalesce(v.chunk_id, l.chunk_id) AS chunk_id,
                        coalesce(vector_rank, 0) AS vector_rank, coalesce(lexical_rank, 0) AS lexical_rank,
                        CASE WHEN vector_rank IS NULL THEN 0 ELSE 1.0 / (60 + vector_rank) END
                        + CASE WHEN lexical_rank IS NULL THEN 0 ELSE 1.0 / (60 + lexical_rank) END AS fusion_score
                    FROM vector_candidates v FULL JOIN lexical_candidates l ON l.chunk_id = v.chunk_id
                ), merged AS (
                    SELECT s.*, c.vector_rank, c.lexical_rank, c.fusion_score,
                        ARRAY(SELECT t FROM query_terms, unnest(terms) t
                            WHERE t = ANY(tsvector_to_array(document_terms)) ORDER BY t) AS matched_terms,
                        ARRAY(SELECT t FROM query_terms, unnest(terms) t
                            WHERE NOT t = ANY(tsvector_to_array(document_terms)) ORDER BY t) AS missing_terms
                    FROM candidates c JOIN scored s ON s.chunk_id = c.chunk_id
                )
                SELECT row_number() OVER (ORDER BY fusion_score DESC, chunk_id) AS rank, merged.*
                FROM merged ORDER BY fusion_score DESC, chunk_id LIMIT :limit
                """)
            .param("hybrid", hybridEnabled)
            .param("question", hybridEnabled ? query.question() : "")
            .param("realmId", query.realmId())
            .param("userId", query.userId())
            .param("provider", query.embeddingDescriptor().provider())
            .param("model", query.embeddingDescriptor().model())
            .param("dimension", embedding.length)
            .param("embedding", vectorLiteral(embedding))
            .param("limit", query.limit())
            .query((rs, row) -> mapEvidence(rs, row, hybridEnabled))
            .list();
    }

    private static RetrievedEvidence mapEvidence(ResultSet resultSet, int row, boolean hybrid)
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
            resultSet.getString("classification"),
            new dev.codexofrealms.lore.RetrievalSignals(resultSet.getInt("vector_rank"),
                resultSet.getInt("lexical_rank"), resultSet.getDouble("fusion_score"),
                java.util.List.of((String[]) resultSet.getArray("matched_terms").getArray()),
                java.util.List.of((String[]) resultSet.getArray("missing_terms").getArray()), hybrid)
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
