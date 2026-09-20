package dev.codexofrealms.content.application.source;

import java.util.UUID;

public record SourceContentView(
    UUID documentId,
    UUID versionId,
    String title,
    String originalFilename,
    String content,
    int excludedSentences
) {
    public SourceContentView(UUID documentId, UUID versionId, String title, String originalFilename, String content) {
        this(documentId, versionId, title, originalFilename, content,
            dev.codexofrealms.content.application.evidence.VisiblePassageService.analyze(content).excludedSentences());
    }
}
