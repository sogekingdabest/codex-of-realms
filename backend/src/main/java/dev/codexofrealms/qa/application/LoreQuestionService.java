package dev.codexofrealms.qa.application;

import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.qa.AnswerProvenance;
import dev.codexofrealms.qa.AnswerFailureReason;
import dev.codexofrealms.qa.GroundedAnswerDraft;
import dev.codexofrealms.qa.GroundedAnswerModel;
import dev.codexofrealms.qa.GroundedAnswerRequest;
import dev.codexofrealms.qa.LoreAnswer;
import dev.codexofrealms.qa.ModelDescriptor;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LoreQuestionService {

    private final LoreSearch loreSearch;
    private final GroundedAnswerModel model;
    private final EvidenceGate evidenceGate;
    private final AnswerValidator validator;
    private final QaMetrics metrics;
    private final QaProperties properties;

    LoreQuestionService(
        LoreSearch loreSearch,
        GroundedAnswerModel model,
        EvidenceGate evidenceGate,
        AnswerValidator validator,
        QaMetrics metrics,
        QaProperties properties
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
                metrics.gateRejected(sample, decision.reason());
                return LoreAnswer.insufficient(provenance, gateFailure(decision.reason()));
            }

            Optional<GroundedAnswerDraft> draft = generate(
                new GroundedAnswerRequest(retrieval.question(), decision.evidence())
            );
            if (draft.isEmpty()) {
                metrics.gateRejected(sample, "model_unavailable");
                return LoreAnswer.insufficient(provenance, AnswerFailureReason.MODEL_UNAVAILABLE);
            }
            LoreAnswer answer = validator.validate(
                realmId, draft.orElseThrow(), decision.evidence(), provenance
            );
            metrics.completed(sample, answer.outcome());
            return answer;
        } catch (RuntimeException exception) {
            metrics.failed(sample);
            throw exception;
        }
    }

    private Optional<GroundedAnswerDraft> generate(GroundedAnswerRequest request) {
        try {
            return Optional.ofNullable(model.generate(request));
        } catch (RuntimeException modelFailure) {
            return Optional.empty();
        }
    }

    private static AnswerFailureReason gateFailure(String reason) {
        return switch (reason) {
            case "direct_injection", "indirect_injection" -> AnswerFailureReason.UNSAFE_INPUT;
            case "low_similarity", "low_question_coverage" -> AnswerFailureReason.LOW_RELEVANCE;
            default -> AnswerFailureReason.NO_EVIDENCE;
        };
    }
}
