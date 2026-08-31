package dev.codexofrealms.content.application.evidence;

import dev.codexofrealms.content.SourceEvidence;
import dev.codexofrealms.content.application.port.SourceRepository;
import dev.codexofrealms.realm.RealmAccess;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceEvidenceService {
    private final RealmAccess realmAccess;
    private final SourceRepository repository;

    public SourceEvidenceService(RealmAccess realmAccess, SourceRepository repository) {
        this.realmAccess = realmAccess;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SourceEvidence> resolveActive(
        UUID realmId,
        UUID accessPolicyId,
        UUID userId,
        List<UUID> requestedChunkIds
    ) {
        realmAccess.requireEditor(realmId, userId);
        realmAccess.requireEditablePolicy(realmId, accessPolicyId, userId);
        if (requestedChunkIds == null || requestedChunkIds.isEmpty()) return List.of();

        LinkedHashSet<UUID> chunkIds = new LinkedHashSet<>(requestedChunkIds);
        if (chunkIds.contains(null) || chunkIds.size() > 20) {
            throw SourceEvidenceException.invalidSelection(
                "Source evidence must contain at most 20 distinct chunk identifiers."
            );
        }

        List<SourceEvidence> evidence = new ArrayList<>(chunkIds.size());
        for (UUID chunkId : chunkIds) {
            evidence.add(repository.findActiveEvidence(realmId, accessPolicyId, chunkId)
                .orElseThrow(SourceEvidenceException::unavailable));
        }
        return List.copyOf(evidence);
    }
}
