package dev.codexofrealms.qa.application.answering;

import static org.assertj.core.api.Assertions.assertThat;
import dev.codexofrealms.qa.*;
import dev.codexofrealms.qa.application.port.GroundedAnswerDraft;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnswerValidatorTest {
    private final AnswerValidator validator = new AnswerValidator(TestQaFixtures.properties());
    private final AnswerProvenance provenance = new AnswerProvenance("test", "embedding", "test", "selector");

    @Test
    void copiesNegationsDatesNumbersAndUnicodeWithoutGeneratingText() {
        String text = "Nara no pertenece a la Cofradía del Bronce.\nPagó 42 monedas el 3 de marzo de 2026. 🧭";
        var evidence = TestQaFixtures.evidence(1, .9, text);
        var answer = validator.validate(UUID.randomUUID(), new GroundedAnswerDraft(AnswerOutcome.ANSWERED,
            List.of(evidence.passageId())), List.of(evidence), provenance);
        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
        assertThat(answer.excerpts()).containsExactly(new AnswerExcerpt(text, 1));
        assertThat(answer.answer()).isEqualTo(text + " [1]");
        assertThat(answer.answerMode()).isEqualTo("EXTRACTIVE");
    }

    @Test
    void rejectsInventedIdsEvenWhenTheyContainWordsFromTheEvidence() {
        var evidence = TestQaFixtures.evidence(1, .9, "Nara no pertenece a la Cofradía del Bronce.");
        var answer = validator.validate(UUID.randomUUID(), new GroundedAnswerDraft(AnswerOutcome.ANSWERED,
            List.of("Nara pertenece a la Cofradía del Bronce.")), List.of(evidence), provenance);
        assertThat(answer.failureReason()).isEqualTo(AnswerFailureReason.VALIDATION_FAILED);
        assertThat(answer.excerpts()).isEmpty();
    }

    @Test
    void rejectsDuplicateSelection() {
        var e = TestQaFixtures.evidence(1, .9, "Fuente literal.");
        var answer = validator.validate(UUID.randomUUID(), new GroundedAnswerDraft(AnswerOutcome.ANSWERED,
            List.of(e.passageId(), e.passageId())), List.of(e), provenance);
        assertThat(answer.failureReason()).isEqualTo(AnswerFailureReason.VALIDATION_FAILED);
    }

    @Test
    void distinguishesRefusalFromInvalidOutput() {
        for (var invalid : List.of(new GroundedAnswerDraft(null, List.of()),
            new GroundedAnswerDraft(AnswerOutcome.ANSWERED, List.of()),
            new GroundedAnswerDraft(AnswerOutcome.INSUFFICIENT_EVIDENCE, List.of("unexpected")))) {
            assertThat(validator.validate(UUID.randomUUID(), invalid, List.of(), provenance).failureReason())
                .isEqualTo(AnswerFailureReason.VALIDATION_FAILED);
        }
        assertThat(validator.validate(UUID.randomUUID(), GroundedAnswerDraft.insufficient(), List.of(), provenance)
            .failureReason()).isEqualTo(AnswerFailureReason.NO_EVIDENCE);
    }

    @Test
    void rejectsMissingDraftAndMoreThanThreeSelections() {
        assertThat(validator.validate(UUID.randomUUID(), null, List.of(), provenance).failureReason())
            .isEqualTo(AnswerFailureReason.VALIDATION_FAILED);
        assertThat(validator.validate(UUID.randomUUID(), new GroundedAnswerDraft(AnswerOutcome.ANSWERED,
            List.of("a", "b", "c", "d")), List.of(), provenance).failureReason())
            .isEqualTo(AnswerFailureReason.VALIDATION_FAILED);
    }

    @Test
    void neverTruncatesAnOversizedSelectedPassage() {
        var limited = new AnswerValidator(new AnsweringProperties(10, 6, .45, .70, 3, 100));
        var e = TestQaFixtures.evidence(1, .9, "Nara no pertenece a la Cofradía. ".repeat(10));
        var answer = limited.validate(UUID.randomUUID(), new GroundedAnswerDraft(AnswerOutcome.ANSWERED,
            List.of(e.passageId())), List.of(e), provenance);
        assertThat(answer.failureReason()).isEqualTo(AnswerFailureReason.VALIDATION_FAILED);
        assertThat(answer.answer()).isNull();
    }
}
