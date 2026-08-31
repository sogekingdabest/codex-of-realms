package dev.codexofrealms.qa.application.answering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.qa.AnswerOutcome;
import dev.codexofrealms.qa.AnswerFailureReason;
import dev.codexofrealms.qa.application.port.AnswerModelUnavailableException;
import dev.codexofrealms.qa.application.port.DraftClaim;
import dev.codexofrealms.qa.application.port.GroundedAnswerDraft;
import dev.codexofrealms.qa.application.port.GroundedAnswerModel;
import dev.codexofrealms.qa.application.port.GroundedAnswerRequest;
import dev.codexofrealms.qa.application.port.ModelDescriptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QuestionAnsweringServiceTest {

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
            throw new AnswerModelUnavailableException("runtime unavailable");
        });

        var answer = service(search, model)
            .answer(UUID.randomUUID(), UUID.randomUUID(), "pregunta");

        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
        assertThat(answer.answer()).isNull();
        assertThat(answer.failureReason()).isEqualTo(AnswerFailureReason.MODEL_UNAVAILABLE);
    }

    @Test
    void forwardsConfiguredRetrievalLimit() {
        AtomicInteger receivedLimit = new AtomicInteger();
        LoreSearch search = (realmId, userId, question, limit) -> {
            receivedLimit.set(limit);
            return new RetrievalResult(
                "¿En qué año apareció el Meridiano?", "test", "embedding-v1",
                List.of(TestQaFixtures.evidence(1, 0.90,
                    "El Meridiano apareció en el año 0 Después de la Partición."))
            );
        };

        service(search, model(request -> GroundedAnswerDraft.insufficient()))
            .answer(UUID.randomUUID(), UUID.randomUUID(), "pregunta");

        assertThat(receivedLimit).hasValue(TestQaFixtures.properties().retrievalLimit());
    }

    @Test
    void doesNotHideUnexpectedModelFailures() {
        LoreSearch search = search("¿En qué año apareció el Meridiano?",
            "El Meridiano apareció en el año 0 Después de la Partición.");
        GroundedAnswerModel model = model(request -> {
            throw new IllegalStateException("programming error");
        });

        assertThatThrownBy(() -> service(search, model)
            .answer(UUID.randomUUID(), UUID.randomUUID(), "pregunta"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("programming error");
    }

    @Test
    void recordsModelRefusalMetricsExactlyOnce() {
        LoreSearch search = search("¿En qué año apareció el Meridiano?",
            "El Meridiano apareció en el año 0 Después de la Partición.");
        GroundedAnswerModel model = model(request -> {
            throw new AnswerModelUnavailableException("runtime unavailable");
        });
        var registry = new SimpleMeterRegistry();

        service(search, model, registry)
            .answer(UUID.randomUUID(), UUID.randomUUID(), "pregunta");

        assertThat(registry.get("codex.qa.outcomes")
            .tag("outcome", "insufficient_evidence")
            .tag("stage", "model")
            .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("codex.qa.duration")
            .tag("outcome", "insufficient_evidence")
            .tag("reason", "model_unavailable")
            .timer().count()).isEqualTo(1L);
    }

    private static QuestionAnsweringService service(LoreSearch search, GroundedAnswerModel model) {
        return service(search, model, new SimpleMeterRegistry());
    }

    private static QuestionAnsweringService service(
        LoreSearch search,
        GroundedAnswerModel model,
        SimpleMeterRegistry registry
    ) {
        AnsweringProperties properties = TestQaFixtures.properties();
        return new QuestionAnsweringService(
            search,
            model,
            new EvidenceGate(properties),
            new AnswerValidator(properties),
            new QaMetrics(registry),
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
