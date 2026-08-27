package dev.codexofrealms.qa;

import java.util.List;

public record LoreAnswer(
    AnswerOutcome outcome,
    String answer,
    List<Citation> citations,
    AnswerProvenance provenance
) {
    public LoreAnswer {
        citations = List.copyOf(citations);
        if (outcome == AnswerOutcome.ANSWERED && (answer == null || answer.isBlank() || citations.isEmpty())) {
            throw new IllegalArgumentException("An answered result requires text and citations.");
        }
        if (outcome == AnswerOutcome.INSUFFICIENT_EVIDENCE && (answer != null || !citations.isEmpty())) {
            throw new IllegalArgumentException("An insufficient-evidence result cannot contain an answer.");
        }
    }

    public static LoreAnswer insufficient(AnswerProvenance provenance) {
        return new LoreAnswer(AnswerOutcome.INSUFFICIENT_EVIDENCE, null, List.of(), provenance);
    }
}
