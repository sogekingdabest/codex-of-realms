package dev.codexofrealms.lore;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Deterministic selection of already-authorized candidates. */
public final class PassageSelection {
    private PassageSelection() {}
    public static List<RetrievedEvidence> select(String question, List<RetrievedEvidence> candidates, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Positive passage limit required");
        var ordered = candidates.stream().sorted(Comparator
            .comparingDouble((RetrievedEvidence p) -> PassageRelevance.coverage(question, p.content())).reversed()
            .thenComparing(Comparator.comparingDouble(RetrievedEvidence::similarity).reversed())
            .thenComparingInt(RetrievedEvidence::rank).thenComparingInt(RetrievedEvidence::startOffset)
            .thenComparingInt(RetrievedEvidence::endOffset).thenComparing(RetrievedEvidence::passageId)).toList();
        var selected = new java.util.LinkedHashSet<RetrievedEvidence>();
        var documents = new HashSet<UUID>();
        for (var p : ordered) {
            if (selected.size() >= limit) break;
            if (PassageRelevance.coverage(question, p.content()) > 0 && documents.add(p.sourceDocumentId())) selected.add(p);
        }
        // Prefer information not yet covered when filling the remaining slots.
        // Absolute overlap alone can crowd out a short but essential qualification.
        while (selected.size() < limit) {
            String covered = selected.stream().map(RetrievedEvidence::content)
                .collect(java.util.stream.Collectors.joining("\n"));
            RetrievedEvidence best = null;
            double bestCoverage = -1;
            for (var p : ordered) {
                if (selected.contains(p)) continue;
                double coverage = PassageRelevance.coverage(question, covered + "\n" + p.content());
                if (coverage > bestCoverage) { best = p; bestCoverage = coverage; }
            }
            if (best == null) break;
            selected.add(best);
        }
        var result = new java.util.ArrayList<RetrievedEvidence>();
        for (var p : selected) result.add(new RetrievedEvidence(result.size() + 1, p.distance(), p.similarity(),
            p.chunkId(), p.content(), p.heading(), p.startOffset(), p.endOffset(), p.sourceDocumentId(),
            p.documentVersionId(), p.versionNumber(), p.sourceTitle(), p.originalFilename(), p.checksumSha256(),
            p.accessPolicyId(), p.accessClassification(), p.retrievalSignals()));
        return List.copyOf(result);
    }
}
