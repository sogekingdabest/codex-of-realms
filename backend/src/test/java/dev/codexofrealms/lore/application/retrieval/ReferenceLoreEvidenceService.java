package dev.codexofrealms.lore.application.retrieval;

import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.content.SourcePassage;
import dev.codexofrealms.lore.LoreEvidence;
import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

public class ReferenceLoreEvidenceService implements LoreEvidence {
    private final SourceEvidenceAccess sources;

    public ReferenceLoreEvidenceService(SourceEvidenceAccess sources) { this.sources = sources; }

    public List<RetrievedEvidence> passages(UUID realmId, UUID userId, String question, List<RetrievedEvidence> chunks, int limit) {
        var seen = new HashSet<String>();
        var result = new ArrayList<RetrievedEvidence>();
        for (var chunk : chunks) {
            for (var p : sources.visibleParagraphs(realmId, userId, chunk.sourceDocumentId(),
                chunk.documentVersionId(), chunk.startOffset(), chunk.endOffset())) {
                String key = chunk.documentVersionId() + ":" + p.startOffset() + ":" + p.endOffset();
                if (!seen.add(key)) continue;
                result.add(new RetrievedEvidence(result.size() + 1, chunk.distance(), chunk.similarity(),
                    chunk.chunkId(), p.content(), chunk.heading(), p.startOffset(), p.endOffset(),
                    chunk.sourceDocumentId(), chunk.documentVersionId(), chunk.versionNumber(), chunk.sourceTitle(),
                    chunk.originalFilename(), chunk.checksumSha256(), chunk.accessPolicyId(), chunk.accessClassification()));
                if (result.size() == limit) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    public boolean stillVisible(UUID realmId, UUID userId, List<RetrievedEvidence> passages) {
        return passages.stream().allMatch(p -> sources.matchesVisible(realmId, userId, p.sourceDocumentId(),
            p.documentVersionId(), new SourcePassage(p.content(), p.startOffset(), p.endOffset())));
    }
}
