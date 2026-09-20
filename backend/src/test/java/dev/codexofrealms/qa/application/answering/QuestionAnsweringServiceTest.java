package dev.codexofrealms.qa.application.answering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.qa.AnswerOutcome;
import dev.codexofrealms.qa.AnswerFailureReason;
import dev.codexofrealms.qa.application.port.AnswerModelUnavailableException;
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
    void incompleteSelectionIsRejectedWithoutAnotherModelCallOrInventedCitations() {
        var permission=TestQaFixtures.evidence(1,.9,"Nara necesita el sello de Iria para abrir el portal de Ámbar.");
        var amount=TestQaFixtures.evidence(2,.9,"Li debe pagar 12 monedas.");
        String question="¿Qué necesita Nara para abrir el portal de Ámbar y cuántas monedas debe pagar Li?";
        LoreSearch search=(r,u,q,l) -> new RetrievalResult(question,"test","v1",List.of(permission,amount));
        var calls=new AtomicInteger();
        var model=model(request -> { calls.incrementAndGet(); return new GroundedAnswerDraft(AnswerOutcome.ANSWERED,List.of(permission.passageId())); });
        var service=service(search,model);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"completeSelectionEnabled",true);
        var result=service.answer(UUID.randomUUID(),UUID.randomUUID(),question);
        assertThat(result.outcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
        assertThat(result.failureReason()).isEqualTo(AnswerFailureReason.VALIDATION_FAILED);
        assertThat(result.citations()).isEmpty();
        assertThat(calls).hasValue(1);
    }

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
            List.of(request.evidence().getFirst().passageId())
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

    @Test
    void checksSelectedParagraphRelevanceAndCopiesExactOriginal() {
        var properties = TestQaFixtures.properties();
        String paragraph = "No fue el lunes: ocurrió el 2 de mayo de 2024.";
        var selector = model(request -> new GroundedAnswerDraft(AnswerOutcome.ANSWERED,
            List.of(request.evidence().getFirst().passageId())));
        var service = new QuestionAnsweringService(search("ocurrió mayo 2024", "# Calendario\n\n" + paragraph),
            TestQaFixtures.paragraphEvidence(), selector, new EvidenceGate(properties), new AnswerValidator(properties),
            new QaMetrics(new SimpleMeterRegistry()), properties);
        var answer = service.answer(UUID.randomUUID(), UUID.randomUUID(), "Calendario");
        assertThat(answer.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
        assertThat(answer.excerpts().getFirst().text()).isEqualTo(paragraph);
    }

    @Test
    void headingAloneCannotSatisfySelectedPassageCoverage() {
        var properties = TestQaFixtures.properties();
        var selector = model(request -> { throw new AssertionError("No relevant passage"); });
        var service = new QuestionAnsweringService(search("Calendario", "# Calendario\n\nNo fue el lunes."),
            TestQaFixtures.paragraphEvidence(), selector, new EvidenceGate(properties), new AnswerValidator(properties),
            new QaMetrics(new SimpleMeterRegistry()), properties);
        assertThat(service.answer(UUID.randomUUID(), UUID.randomUUID(), "Calendario").failureReason())
            .isEqualTo(AnswerFailureReason.LOW_RELEVANCE);
    }

    @Test
    void refusesWhenRetrievedContextHasNoUsableWholeParagraph() {
        var properties = TestQaFixtures.properties();
        var selector = model(request -> { throw new AssertionError("Model must not receive an oversized paragraph"); });
        var service = new QuestionAnsweringService(search("Nara", "Nara " + "x".repeat(2001)),
            TestQaFixtures.paragraphEvidence(), selector, new EvidenceGate(properties), new AnswerValidator(properties),
            new QaMetrics(new SimpleMeterRegistry()), properties);
        var answer = service.answer(UUID.randomUUID(), UUID.randomUUID(), "Nara");
        assertThat(answer.failureReason()).isEqualTo(AnswerFailureReason.NO_EVIDENCE);
    }

    @Test
    void revocationDuringSelectionDiscardsEveryExcerpt() {
        var properties = TestQaFixtures.properties();
        var evidence = org.mockito.Mockito.mock(dev.codexofrealms.lore.LoreEvidence.class);
        org.mockito.Mockito.when(evidence.passages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer(call -> call.getArgument(3));
        var selector = model(request -> new GroundedAnswerDraft(AnswerOutcome.ANSWERED,
            List.of(request.evidence().getFirst().passageId())));
        var service = new QuestionAnsweringService(search("¿Dónde vive Nara?", "Nara vive en Lumbrevela."),
            evidence, selector, new EvidenceGate(properties), new AnswerValidator(properties),
            new QaMetrics(new SimpleMeterRegistry()), properties);
        var answer = service.answer(UUID.randomUUID(), UUID.randomUUID(), "pregunta");
        assertThat(answer.failureReason()).isEqualTo(AnswerFailureReason.NO_EVIDENCE);
        assertThat(answer.excerpts()).isEmpty();
    }

    private static QuestionAnsweringService service(
        LoreSearch search,
        GroundedAnswerModel model,
        SimpleMeterRegistry registry
    ) {
        AnsweringProperties properties = TestQaFixtures.properties();
        return new QuestionAnsweringService(
            search,
            TestQaFixtures.passthroughEvidence(),
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
