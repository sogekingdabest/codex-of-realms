package dev.codexofrealms.content.application.source;

import java.util.UUID;

public record SourceContentView(
    UUID documentId,
    UUID versionId,
    String title,
    String originalFilename,
    String content
) {
}
