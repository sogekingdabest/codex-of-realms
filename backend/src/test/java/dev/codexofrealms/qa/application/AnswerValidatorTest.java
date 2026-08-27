package dev.codexofrealms.qa.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.qa.AnswerOutcome;
import dev.codexofrealms.qa.AnswerProvenance;
import dev.codexofrealms.qa.DraftClaim;
import dev.codexofrealms.qa.GroundedAnswerDraft;
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
}
