package dev.codexofrealms.qa.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.qa.AnswerOutcome;
import dev.codexofrealms.qa.DraftClaim;
import dev.codexofrealms.qa.GroundedAnswerDraft;
import dev.codexofrealms.qa.GroundedAnswerModel;
import dev.codexofrealms.qa.GroundedAnswerRequest;
import dev.codexofrealms.qa.ModelDescriptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LoreQuestionServiceTest {

    @Test
    void doesNotCallModelWhenEvidenceGateRejectsQuestion() {
        AtomicBoolean generated = new AtomicBoolean();
        GroundedAnswerModel model = model(request -> {
            generated.set(true);
            return GroundedAnswerDraft.insufficient();
        });
        LoreSearch search = search("¿Cómo se llama la madre de Maela Ors?",
            "Maela Ors es la cartógrafa mayor de la Custodia del Sextante.");
        var service = service(search, model);

        var answer = service.answer(UUID.randomUUID(), UUID.randomUUID(), "pregunta");

        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
        assertThat(generated).isFalse();
    }

    @Test
    void returnsAnsweredOnlyAfterDraftAndCitationValidation() {
        LoreSearch search = search("¿En qué año apareció el Meridiano?",
            "El Meridiano apareció en el año 0 Después de la Partición.");
        GroundedAnswerModel model = model(request -> new GroundedAnswerDraft(
            AnswerOutcome.ANSWERED,
            List.of(new DraftClaim("El Meridiano apareció en el año 0.", List.of(1)))
        ));

        var answer = service(search, model)
            .answer(UUID.randomUUID(), UUID.randomUUID(), "pregunta");

        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.answer()).endsWith("[1]");
    }

    @Test
    void failsClosedWhenModelThrows() {
        LoreSearch search = search("¿En qué año apareció el Meridiano?",
            "El Meridiano apareció en el año 0 Después de la Partición.");
        GroundedAnswerModel model = model(request -> {
            throw new IllegalStateException("runtime unavailable");
        });

        var answer = service(search, model)
            .answer(UUID.randomUUID(), UUID.randomUUID(), "pregunta");

        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
        assertThat(answer.answer()).isNull();
    }

    private static LoreQuestionService service(LoreSearch search, GroundedAnswerModel model) {
        QaProperties properties = TestQaFixtures.properties();
        return new LoreQuestionService(
            search,
            model,
            new EvidenceGate(properties),
            new AnswerValidator(properties),
            new QaMetrics(new SimpleMeterRegistry()),
            properties
        );
    }

    private static LoreSearch search(String normalizedQuestion, String content) {
        return (realmId, userId, question, limit) -> new RetrievalResult(
            normalizedQuestion, "test", "embedding-v1",
            List.of(TestQaFixtures.evidence(1, 0.90, content))
        );
    }

    private static GroundedAnswerModel model(
        java.util.function.Function<GroundedAnswerRequest, GroundedAnswerDraft> generator
    ) {
        return new GroundedAnswerModel() {
            @Override
            public GroundedAnswerDraft generate(GroundedAnswerRequest request) {
                return generator.apply(request);
            }

            @Override
            public ModelDescriptor descriptor() {
                return new ModelDescriptor("test", "chat-v1");
            }
        };
    }
}
