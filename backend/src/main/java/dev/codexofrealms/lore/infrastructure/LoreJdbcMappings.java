package dev.codexofrealms.lore.infrastructure;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.lore.domain.CataloguePromotion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

final class LoreJdbcMappings {

    private LoreJdbcMappings() {
    }

    static SourceEvidence mapEvidence(ResultSet resultSet, int row) throws SQLException {
        return new SourceEvidence(
            resultSet.getObject("document_id", UUID.class),
            resultSet.getObject("document_version_id", UUID.class),
            resultSet.getObject("chunk_id", UUID.class), resultSet.getString("source_title"),
            resultSet.getString("checksum_sha256"), resultSet.getString("heading"),
            resultSet.getInt("start_offset"), resultSet.getInt("end_offset")
        );
    }

    static CataloguePromotion mapPromotion(ResultSet resultSet, int row) throws SQLException {
        return new CataloguePromotion(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("promoted_by", UUID.class),
            instant(resultSet, "promoted_at")
        );
    }

    static void insertEvidence(
        JdbcClient.StatementSpec statement,
        UUID realmId,
        UUID ownerId,
        SourceEvidence evidence
    ) {
        statement
            .param("realmId", realmId).param("ownerId", ownerId)
            .param("chunkId", evidence.chunkId()).param("documentId", evidence.documentId())
            .param("versionId", evidence.documentVersionId())
            .param("sourceTitle", evidence.sourceTitle())
            .param("checksum", evidence.checksumSha256()).param("heading", evidence.heading())
            .param("startOffset", evidence.startOffset()).param("endOffset", evidence.endOffset())
            .update();
    }

    static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
