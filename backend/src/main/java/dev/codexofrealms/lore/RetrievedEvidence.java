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
    String accessClassification,
    @com.fasterxml.jackson.annotation.JsonIgnore RetrievalSignals retrievalSignals
) {
    public RetrievedEvidence(int rank, double distance, double similarity, UUID chunkId, String content,
            String heading, int startOffset, int endOffset, UUID sourceDocumentId, UUID documentVersionId,
            int versionNumber, String sourceTitle, String originalFilename, String checksumSha256,
            UUID accessPolicyId, String accessClassification) {
        this(rank, distance, similarity, chunkId, content, heading, startOffset, endOffset,
            sourceDocumentId, documentVersionId, versionNumber, sourceTitle, originalFilename,
            checksumSha256, accessPolicyId, accessClassification, RetrievalSignals.vectorOnly(rank));
    }
    public String passageId() { return documentVersionId + ":" + startOffset + ":" + endOffset; }
}
