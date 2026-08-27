package dev.codexofrealms.content.infrastructure;

import java.util.UUID;

public record SourceVersionRecord(
    UUID documentId,
    UUID versionId,
    int versionNumber,
    String title,
    String originalFilename,
    String mediaType,
    String language,
    String checksum,
    String storageKey,
    UUID accessPolicyId,
    String pipelineFingerprint
) {
}
