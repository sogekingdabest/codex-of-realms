package dev.codexofrealms.content.application.port;

import java.util.UUID;

public record SourceVersion(
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
