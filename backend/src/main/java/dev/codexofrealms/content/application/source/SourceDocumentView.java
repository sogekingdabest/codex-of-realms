package dev.codexofrealms.content.application.source;

import dev.codexofrealms.content.domain.ProcessingStatus;
import java.time.Instant;
import java.util.UUID;

public record SourceDocumentView(
    UUID id,
    UUID realmId,
    String title,
    UUID versionId,
    int versionNumber,
    String checksumSha256,
    String originalFilename,
    String mediaType,
    String language,
    ProcessingStatus status,
    UUID accessPolicyId,
    String embeddingProvider,
    String embeddingModel,
    Integer embeddingDimension,
    int chunkCount,
    Instant createdAt
) {
}
