package dev.codexofrealms.qa.application.answering;

import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.qa.AnswerFailureReason;
import dev.codexofrealms.qa.AnswerProvenance;
import dev.codexofrealms.qa.LoreAnswer;
import dev.codexofrealms.qa.application.port.AnswerModelUnavailableException;
import dev.codexofrealms.qa.application.port.GroundedAnswerDraft;
import dev.codexofrealms.qa.application.port.GroundedAnswerModel;
import dev.codexofrealms.qa.application.port.GroundedAnswerRequest;
import dev.codexofrealms.qa.application.port.ModelDescriptor;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuestionAnsweringService {

    private final LoreSearch loreSearch;
    private final GroundedAnswerModel model;
    private final EvidenceGate evidenceGate;
    private final AnswerValidator validator;
    private final QaMetrics metrics;
    private final AnsweringProperties properties;

    QuestionAnsweringService(
        LoreSearch loreSearch,
        GroundedAnswerModel model,
        EvidenceGate evidenceGate,
        AnswerValidator validator,
        QaMetrics metrics,
        AnsweringProperties properties
    ) {
        this.loreSearch = loreSearch;
        this.model = model;
        this.evidenceGate = evidenceGate;
        this.validator = validator;
        this.metrics = metrics;
        this.properties = properties;
    }

    public LoreAnswer answer(UUID realmId, UUID userId, String question) {
        Timer.Sample sample = metrics.start();
        try {
            RetrievalResult retrieval = loreSearch.retrieve(
                realmId, userId, question, properties.retrievalLimit()
            );
            ModelDescriptor modelDescriptor = model.descriptor();
            AnswerProvenance provenance = new AnswerProvenance(
                retrieval.embeddingProvider(), retrieval.embeddingModel(),
                modelDescriptor.provider(), modelDescriptor.model()
            );
            EvidenceGateDecision decision = evidenceGate.evaluate(retrieval.question(), retrieval.evidence());
            if (!decision.sufficient()) {
                metrics.refused(sample, "gate", decision.reason().metricValue());
                return LoreAnswer.insufficient(provenance, gateFailure(decision.reason()));
            }

            Optional<GroundedAnswerDraft> draft = generate(
                new GroundedAnswerRequest(retrieval.question(), decision.evidence())
            );
            if (draft.isEmpty()) {
                metrics.refused(sample, "model", "model_unavailable");
                return LoreAnswer.insufficient(provenance, AnswerFailureReason.MODEL_UNAVAILABLE);
            }
            LoreAnswer answer = validator.validate(
                realmId, draft.orElseThrow(), decision.evidence(), provenance
            );
            metrics.completed(sample, answer);
            return answer;
        } catch (RuntimeException exception) {
            metrics.failed(sample);
            throw exception;
        }
    }

    private Optional<GroundedAnswerDraft> generate(GroundedAnswerRequest request) {
        try {
            return Optional.ofNullable(model.generate(request));
        } catch (AnswerModelUnavailableException unavailable) {
            return Optional.empty();
        }
    }

    private static AnswerFailureReason gateFailure(EvidenceGateDecision.Reason reason) {
        return switch (reason) {
            case DIRECT_INJECTION, INDIRECT_INJECTION -> AnswerFailureReason.UNSAFE_INPUT;
            case LOW_SIMILARITY, LOW_QUESTION_COVERAGE -> AnswerFailureReason.LOW_RELEVANCE;
            case NO_EVIDENCE -> AnswerFailureReason.NO_EVIDENCE;
            case SUFFICIENT -> throw new IllegalArgumentException("A sufficient decision has no failure reason.");
        };
    }
}
