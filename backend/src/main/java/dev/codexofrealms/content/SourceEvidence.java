package dev.codexofrealms.content;

import java.util.UUID;

/** Immutable, display-safe snapshot of one source chunk used as structured-lore evidence. */
public record SourceEvidence(
    UUID documentId,
    UUID documentVersionId,
    UUID chunkId,
    String sourceTitle,
    String checksumSha256,
    String heading,
    int startOffset,
    int endOffset
) {
}
