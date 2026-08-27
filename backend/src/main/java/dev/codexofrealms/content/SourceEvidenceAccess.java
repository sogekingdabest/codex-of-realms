package dev.codexofrealms.content;

import dev.codexofrealms.content.application.SourceNotFoundException;
import dev.codexofrealms.content.infrastructure.SourceJdbcRepository;
import dev.codexofrealms.realm.RealmAccess;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Public source-provenance facade for other application modules. */
@Component
public class SourceEvidenceAccess {

    private final RealmAccess realmAccess;
    private final SourceJdbcRepository repository;

    public SourceEvidenceAccess(RealmAccess realmAccess, SourceJdbcRepository repository) {
        this.realmAccess = realmAccess;
        this.repository = repository;
    }

    public List<SourceEvidence> resolveActive(
        UUID realmId,
        UUID accessPolicyId,
        UUID userId,
        List<UUID> requestedChunkIds
    ) {
        realmAccess.requireEditor(realmId, userId);
        realmAccess.requireEditablePolicy(realmId, accessPolicyId, userId);
        if (requestedChunkIds == null || requestedChunkIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> chunkIds = new LinkedHashSet<>(requestedChunkIds);
        if (chunkIds.contains(null) || chunkIds.size() > 20) {
            throw new IllegalArgumentException("Source evidence must contain at most 20 distinct chunk identifiers.");
        }

        List<SourceEvidence> evidence = new ArrayList<>(chunkIds.size());
        for (UUID chunkId : chunkIds) {
            evidence.add(repository.findActiveEvidence(realmId, accessPolicyId, chunkId)
                .orElseThrow(SourceNotFoundException::new));
        }
        return List.copyOf(evidence);
    }
}
