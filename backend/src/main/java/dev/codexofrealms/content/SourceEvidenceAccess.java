package dev.codexofrealms.content;

import dev.codexofrealms.content.application.evidence.SourceEvidenceService;
import dev.codexofrealms.content.application.evidence.VisiblePassageService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Public source-provenance facade for other application modules. */
@Component
public class SourceEvidenceAccess {

    private final SourceEvidenceService service;
    private final VisiblePassageService passages;

    public SourceEvidenceAccess(SourceEvidenceService service, VisiblePassageService passages) {
        this.service = service;
        this.passages = passages;
    }

    public List<SourcePassage> visibleParagraphs(UUID realmId, UUID userId, UUID documentId, UUID versionId,
                                               int start, int end) {
        try {
            return passages.paragraphs(realmId, userId, documentId, versionId, start, end);
        } catch (dev.codexofrealms.content.application.source.SourceException unavailable) {
            return List.of();
        }
    }

    public boolean matchesVisible(UUID realmId, UUID userId, UUID documentId, UUID versionId, SourcePassage passage) {
        return passages.matches(realmId, userId, documentId, versionId, passage);
    }

    public boolean matchesAllVisible(UUID realmId, UUID userId, UUID documentId, UUID versionId, List<SourcePassage> selected) {
        return passages.matchesAll(realmId, userId, documentId, versionId, selected);
    }

    public List<SourceEvidence> resolveActive(
        UUID realmId,
        UUID accessPolicyId,
        UUID userId,
        List<UUID> requestedChunkIds
    ) {
        return service.resolveActive(realmId, accessPolicyId, userId, requestedChunkIds);
    }
}
