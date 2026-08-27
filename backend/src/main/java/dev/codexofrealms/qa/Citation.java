package dev.codexofrealms.qa;

import java.util.UUID;

public record Citation(
    int rank,
    UUID realmId,
    UUID chunkId,
    UUID sourceDocumentId,
    UUID documentVersionId,
    int versionNumber,
    String sourceTitle,
    String heading,
    int startOffset,
    int endOffset
) {
}
