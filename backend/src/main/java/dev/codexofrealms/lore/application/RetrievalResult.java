package dev.codexofrealms.lore.application;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.List;

public record RetrievalResult(
    String embeddingProvider,
    String embeddingModel,
    List<RetrievedEvidence> evidence
) {
    public RetrievalResult {
        evidence = List.copyOf(evidence);
    }
}
