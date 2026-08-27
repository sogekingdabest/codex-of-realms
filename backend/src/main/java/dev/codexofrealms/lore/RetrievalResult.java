package dev.codexofrealms.lore;

import java.util.List;

public record RetrievalResult(
    String question,
    String embeddingProvider,
    String embeddingModel,
    List<RetrievedEvidence> evidence
) {
    public RetrievalResult {
        evidence = List.copyOf(evidence);
    }
}
