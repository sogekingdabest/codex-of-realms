package dev.codexofrealms.lore;

import java.util.UUID;

public record RetrievedEvidence(
    int rank,
    double distance,
    double similarity,
    UUID chunkId,
    String content,
    String heading,
    int startOffset,
    int endOffset,
    UUID sourceDocumentId,
    UUID documentVersionId,
    int versionNumber,
    String sourceTitle,
    String originalFilename,
    String checksumSha256,
    UUID accessPolicyId,
    String accessClassification
) {
}
