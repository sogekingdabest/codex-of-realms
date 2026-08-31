package dev.codexofrealms.content;

import dev.codexofrealms.content.application.evidence.SourceEvidenceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Public source-provenance facade for other application modules. */
@Component
public class SourceEvidenceAccess {

    private final SourceEvidenceService service;

    public SourceEvidenceAccess(SourceEvidenceService service) {
        this.service = service;
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
