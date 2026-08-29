package dev.codexofrealms.content.application;

import java.util.UUID;

/** Display-safe active source fragment offered as manual catalogue evidence. */
public record SourceChunkView(
    UUID id,
    UUID documentId,
    UUID documentVersionId,
    String sourceTitle,
    int ordinal,
    String heading,
    String content,
    int startOffset,
    int endOffset
) {
}
