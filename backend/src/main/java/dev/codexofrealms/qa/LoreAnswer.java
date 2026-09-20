package dev.codexofrealms.qa;

import java.util.List;

public record LoreAnswer(
    AnswerOutcome outcome,
    String answer,
    List<Citation> citations,
    AnswerProvenance provenance,
    AnswerFailureReason failureReason,
    String answerMode,
    List<AnswerExcerpt> excerpts
) {
    public LoreAnswer(AnswerOutcome outcome, String answer, List<Citation> citations,
                      AnswerProvenance provenance, AnswerFailureReason failureReason) {
        this(outcome, answer, citations, provenance, failureReason, "EXTRACTIVE", List.of());
    }
    public LoreAnswer(
        AnswerOutcome outcome,
        String answer,
        List<Citation> citations,
        AnswerProvenance provenance
    ) {
        this(
            outcome,
            answer,
            citations,
            provenance,
            outcome == AnswerOutcome.INSUFFICIENT_EVIDENCE
                ? AnswerFailureReason.NO_EVIDENCE
                : null
        );
    }

    public LoreAnswer {
        citations = List.copyOf(citations);
        excerpts = List.copyOf(excerpts);
        if (!"EXTRACTIVE".equals(answerMode)) throw new IllegalArgumentException("Unsupported answer mode.");
        if (outcome == AnswerOutcome.ANSWERED && (answer == null || answer.isBlank() || citations.isEmpty())) {
            throw new IllegalArgumentException("An answered result requires text and citations.");
        }
        if (outcome == AnswerOutcome.INSUFFICIENT_EVIDENCE && (answer != null || !citations.isEmpty())) {
            throw new IllegalArgumentException("An insufficient-evidence result cannot contain an answer.");
        }
        if (outcome == AnswerOutcome.ANSWERED && failureReason != null) {
            throw new IllegalArgumentException("An answered result cannot contain a failure reason.");
        }
        if (outcome == AnswerOutcome.INSUFFICIENT_EVIDENCE && failureReason == null) {
            throw new IllegalArgumentException("An insufficient-evidence result requires a failure reason.");
        }
    }

    public static LoreAnswer insufficient(AnswerProvenance provenance) {
        return insufficient(provenance, AnswerFailureReason.NO_EVIDENCE);
    }

    public static LoreAnswer insufficient(
        AnswerProvenance provenance,
        AnswerFailureReason failureReason
    ) {
        return new LoreAnswer(
            AnswerOutcome.INSUFFICIENT_EVIDENCE,
            null,
            List.of(),
            provenance,
            failureReason
        );
    }
}
