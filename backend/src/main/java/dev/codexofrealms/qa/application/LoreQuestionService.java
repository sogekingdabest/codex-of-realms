package dev.codexofrealms.qa.application;

import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.qa.AnswerProvenance;
import dev.codexofrealms.qa.GroundedAnswerDraft;
import dev.codexofrealms.qa.GroundedAnswerModel;
import dev.codexofrealms.qa.GroundedAnswerRequest;
import dev.codexofrealms.qa.LoreAnswer;
import dev.codexofrealms.qa.ModelDescriptor;
import io.micrometer.core.instrument.Timer;
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
                return LoreAnswer.insufficient(provenance);
            }

            GroundedAnswerDraft draft;
            try {
                draft = model.generate(new GroundedAnswerRequest(retrieval.question(), decision.evidence()));
            } catch (RuntimeException modelFailure) {
                draft = GroundedAnswerDraft.insufficient();
            }
            LoreAnswer answer = validator.validate(realmId, draft, decision.evidence(), provenance);
            metrics.completed(sample, answer.outcome());
            return answer;
        } catch (RuntimeException exception) {
            metrics.failed(sample);
            throw exception;
        }
    }
}
