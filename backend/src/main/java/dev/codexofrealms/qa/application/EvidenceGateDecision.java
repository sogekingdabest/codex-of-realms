package dev.codexofrealms.qa.application;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.List;

record EvidenceGateDecision(boolean sufficient, String reason, List<RetrievedEvidence> evidence) {

    EvidenceGateDecision {
        evidence = List.copyOf(evidence);
    }

    static EvidenceGateDecision reject(String reason) {
        return new EvidenceGateDecision(false, reason, List.of());
    }
}
