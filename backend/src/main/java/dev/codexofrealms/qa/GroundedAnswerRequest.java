package dev.codexofrealms.qa;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.List;

public record GroundedAnswerRequest(String question, List<RetrievedEvidence> evidence) {

    public GroundedAnswerRequest {
        evidence = List.copyOf(evidence);
    }
}
