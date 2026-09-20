package dev.codexofrealms.lore.application.retrieval;

import dev.codexofrealms.content.SourceEvidenceAccess;
import dev.codexofrealms.content.SourcePassage;
import dev.codexofrealms.lore.LoreEvidence;
import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class LoreEvidenceService implements LoreEvidence {
    private final SourceEvidenceAccess sources;

    LoreEvidenceService(SourceEvidenceAccess sources) { this.sources = sources; }

    public List<RetrievedEvidence> passages(UUID realmId, UUID userId, String question, List<RetrievedEvidence> chunks, int limit) {
        var seen = new HashSet<String>();
        var result = new ArrayList<RetrievedEvidence>();
        var byVersion = new HashMap<UUID, List<SourcePassage>>();
        var rankedChunks = chunks.stream().sorted(Comparator.comparingDouble(RetrievedEvidence::similarity).reversed()
            .thenComparingInt(RetrievedEvidence::rank)).toList();
        for (var chunk : rankedChunks) {
            for (var p : byVersion.computeIfAbsent(chunk.documentVersionId(), id -> sources.visibleParagraphs(realmId, userId,
                chunk.sourceDocumentId(), id, 0, Integer.MAX_VALUE))) {
                if (p.startOffset() >= chunk.endOffset() || p.endOffset() <= chunk.startOffset()) continue;
                String key = chunk.documentVersionId() + ":" + p.startOffset() + ":" + p.endOffset();
                if (!seen.add(key)) continue;
                result.add(new RetrievedEvidence(chunk.rank(), chunk.distance(), chunk.similarity(),
                    chunk.chunkId(), p.content(), chunk.heading(), p.startOffset(), p.endOffset(),
                    chunk.sourceDocumentId(), chunk.documentVersionId(), chunk.versionNumber(), chunk.sourceTitle(),
                    chunk.originalFilename(), chunk.checksumSha256(), chunk.accessPolicyId(), chunk.accessClassification(), chunk.retrievalSignals()));

            }
        }
        return dev.codexofrealms.lore.PassageSelection.select(question, result, limit);
    }

    public boolean stillVisible(UUID realmId, UUID userId, List<RetrievedEvidence> passages) {
        var versions = passages.stream().collect(java.util.stream.Collectors.groupingBy(RetrievedEvidence::documentVersionId));
        return versions.values().stream().allMatch(group -> sources.matchesAllVisible(realmId, userId,
            group.getFirst().sourceDocumentId(), group.getFirst().documentVersionId(), group.stream()
                .map(p -> new SourcePassage(p.content(), p.startOffset(), p.endOffset())).toList()));
    }
}
