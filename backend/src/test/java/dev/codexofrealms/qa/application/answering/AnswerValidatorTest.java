package dev.codexofrealms.qa.application.answering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.qa.AnswerOutcome;
import dev.codexofrealms.qa.AnswerProvenance;
import dev.codexofrealms.qa.application.port.DraftClaim;
import dev.codexofrealms.qa.application.port.GroundedAnswerDraft;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnswerValidatorTest {

    private final AnswerValidator validator = new AnswerValidator(TestQaFixtures.properties());
    private final AnswerProvenance provenance = new AnswerProvenance(
        "test", "embedding-v1", "test", "chat-v1"
    );

    @Test
    void rendersVisibleValidatedCitationsNextToEachClaim() {
        var evidence = TestQaFixtures.evidence(1, 0.90,
            "El Meridiano de Ceniza apareció en el año 0 Después de la Partición.");
        var draft = new GroundedAnswerDraft(AnswerOutcome.ANSWERED, List.of(
            new DraftClaim("El Meridiano apareció en el año 0.", List.of(1))
        ));

        var answer = validator.validate(UUID.randomUUID(), draft, List.of(evidence), provenance);

        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
        assertThat(answer.answer()).contains("[1]");
        assertThat(answer.citations()).singleElement()
            .satisfies(citation -> assertThat(citation.chunkId()).isEqualTo(evidence.chunkId()));
    }

    @Test
    void convertsUnknownCitationToSafeRefusal() {
        var draft = new GroundedAnswerDraft(AnswerOutcome.ANSWERED, List.of(
            new DraftClaim("El Meridiano apareció en el año 0.", List.of(99))
        ));

        var answer = validator.validate(
            UUID.randomUUID(), draft,
            List.of(TestQaFixtures.evidence(1, 0.90, "El Meridiano apareció en el año 0.")),
            provenance
        );

        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
        assertThat(answer.answer()).isNull();
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void convertsUngroundedClaimToSafeRefusal() {
        var draft = new GroundedAnswerDraft(AnswerOutcome.ANSWERED, List.of(
            new DraftClaim("Nara quedó atrapada dentro del Meridiano.", List.of(1))
        ));

        var answer = validator.validate(
            UUID.randomUUID(), draft,
            List.of(TestQaFixtures.evidence(1, 0.90, "El Meridiano apareció en el año 0.")),
            provenance
        );

        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void convertsMissingDraftToValidationFailure() {
        var answer = validator.validate(
            UUID.randomUUID(), null,
            List.of(TestQaFixtures.evidence(1, 0.90, "El Meridiano apareció en el año 0.")),
            provenance
        );

        assertThat(answer.failureReason()).isEqualTo(dev.codexofrealms.qa.AnswerFailureReason.VALIDATION_FAILED);
    }

    @Test
    void convertsDuplicateCitationsToValidationFailure() {
        var draft = new GroundedAnswerDraft(AnswerOutcome.ANSWERED, List.of(
            new DraftClaim("El Meridiano apareció en el año 0.", List.of(1, 1))
        ));

        var answer = validator.validate(
            UUID.randomUUID(), draft,
            List.of(TestQaFixtures.evidence(1, 0.90, "El Meridiano apareció en el año 0.")),
            provenance
        );

        assertThat(answer.failureReason()).isEqualTo(dev.codexofrealms.qa.AnswerFailureReason.VALIDATION_FAILED);
    }

    @Test
    void keepsCitationsUniqueAndOrderedByRank() {
        var first = TestQaFixtures.evidence(1, 0.90,
            "El Meridiano apareció en el año 0 y la Aguja protege el paso.");
        var second = TestQaFixtures.evidence(2, 0.88,
            "La Aguja protege el paso y el Meridiano apareció en el año 0.");
        var draft = new GroundedAnswerDraft(AnswerOutcome.ANSWERED, List.of(
            new DraftClaim("El Meridiano apareció en el año 0.", List.of(2, 1)),
            new DraftClaim("La Aguja protege el paso.", List.of(2))
        ));

        var answer = validator.validate(UUID.randomUUID(), draft, List.of(first, second), provenance);

        assertThat(answer.citations()).extracting(citation -> citation.rank()).containsExactly(1, 2);
        assertThat(answer.answer()).contains("[1] [2]", "[2]");
    }

    @Test
    void enforcesMaximumAnswerLength() {
        var shortLimit = new AnswerValidator(new AnsweringProperties(
            10, 6, 0.45, 0.70, 0.35, 6, 100
        ));
        String longClaim = "Meridiano ".repeat(11).strip();
        var draft = new GroundedAnswerDraft(AnswerOutcome.ANSWERED, List.of(
            new DraftClaim(longClaim, List.of(1))
        ));

        var answer = shortLimit.validate(
            UUID.randomUUID(), draft,
            List.of(TestQaFixtures.evidence(1, 0.90, longClaim)),
            provenance
        );

        assertThat(answer.failureReason()).isEqualTo(dev.codexofrealms.qa.AnswerFailureReason.VALIDATION_FAILED);
    }
}
