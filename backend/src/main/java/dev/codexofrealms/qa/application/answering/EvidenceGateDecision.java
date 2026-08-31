package dev.codexofrealms.qa.application.answering;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.util.List;

record EvidenceGateDecision(boolean sufficient, Reason reason, List<RetrievedEvidence> evidence) {

    EvidenceGateDecision {
        evidence = List.copyOf(evidence);
    }

    static EvidenceGateDecision accept(List<RetrievedEvidence> evidence) {
        return new EvidenceGateDecision(true, Reason.SUFFICIENT, evidence);
    }

    static EvidenceGateDecision reject(Reason reason) {
        if (reason == Reason.SUFFICIENT) {
            throw new IllegalArgumentException("A rejection requires a failure reason.");
        }
        return new EvidenceGateDecision(false, reason, List.of());
    }

    enum Reason {
        SUFFICIENT("sufficient"),
        NO_EVIDENCE("no_evidence"),
        DIRECT_INJECTION("direct_injection"),
        INDIRECT_INJECTION("indirect_injection"),
        LOW_SIMILARITY("low_similarity"),
        LOW_QUESTION_COVERAGE("low_question_coverage");

        private final String metricValue;

        Reason(String metricValue) {
            this.metricValue = metricValue;
        }

        String metricValue() {
            return metricValue;
        }
    }
}
